package com.zija.household.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.shared.ZijaDigests;
import com.zija.shared.ZijaMemberRole;
import com.zija.shared.ZijaMemberStatus;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.InsufficientRoleException;
import com.zija.household.internal.exception.InvalidInvitationException;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * 邀请码服务，管理家庭成员邀请的创建、兑换和验证。
 * <p>
 * 邀请码使用 Base64 URL 编码的随机 token 生成，存储时仅保留 SHA-256 摘要。
 * 支持设置有效期，兑换时自动注册账户并添加为家庭成员。
 * 权限规则：OWNER 可邀请 ADMIN 和 MEMBER，ADMIN 仅可邀请 MEMBER。
 */
@Service
class InvitationService {

    private final InvitationMapper invitationMapper;
    private final MemberMapper memberMapper;
    private final SystemApi systemApi;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    InvitationService(InvitationMapper invitationMapper, MemberMapper memberMapper, SystemApi systemApi) {
        this.invitationMapper = invitationMapper;
        this.memberMapper = memberMapper;
        this.systemApi = systemApi;
    }

    public record CreateResult(UUID id, String rawToken, String digest,
                                HouseholdApi.MemberRole role, OffsetDateTime expiresAt) {
    }

    public record RedeemCommand(String username, String password,
                                String displayName, String email) {
    }

    /**
     * 创建邀请码，返回原始 token（仅本次可见）和摘要。
     *
     * @param householdId    家庭 ID
     * @param createdBy      创建人账户 ID
     * @param role           邀请角色（ADMIN 或 MEMBER）
     * @param expiresInHours 有效期（小时）
     * @return 创建结果，包含 ID、原始 token、摘要、角色和过期时间
     * @throws InsufficientRoleException 如果创建人权限不足
     */
    @Transactional
    public CreateResult create(UUID householdId, UUID createdBy,
                                HouseholdApi.MemberRole role, int expiresInHours) {
        if (role != HouseholdApi.MemberRole.ADMIN && role != HouseholdApi.MemberRole.MEMBER) {
            throw new IllegalArgumentException("role must be ADMIN or MEMBER");
        }
        var creator = memberMapper.selectByAccount(createdBy)
                .orElseThrow(InsufficientRoleException::new);
        if (!ZijaMemberStatus.ACTIVE.equals(creator.getStatus())
                || !householdId.equals(creator.getHouseholdId())) {
            throw new InsufficientRoleException();
        }
        var ownerInvitation = ZijaMemberRole.OWNER.equals(creator.getRole());
        var adminMemberInvitation = ZijaMemberRole.ADMIN.equals(creator.getRole())
                && role == HouseholdApi.MemberRole.MEMBER;
        if (!ownerInvitation && !adminMemberInvitation) {
            throw new InsufficientRoleException();
        }

        var rawBytes = new byte[32];
        random.nextBytes(rawBytes);
        var rawToken = base64Url.encodeToString(rawBytes);
        var digest = sha256Hex(rawToken);

        var entity = new InvitationEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setTokenDigest(digest);
        entity.setRole(role.name());
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(expiresInHours));
        entity.setCreatedBy(createdBy);
        invitationMapper.insert(entity);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVITATION_CREATED, ZijaAuditOutcome.SUCCESS, householdId, createdBy, null,
                null, null, null));
        return new CreateResult(entity.getId(), rawToken, digest, role, entity.getExpiresAt());
    }

    /**
     * 兑换邀请码，注册新账户并添加为家庭成员。
     * <p>
     * 邀请码兑换后即失效（标记为已消费），不可重复使用。
     *
     * @param rawToken      原始 token
     * @param command       注册信息（用户名、密码、显示名、邮箱）
     * @param identityApi   身份服务（用于注册账户）
     * @param memberService 成员服务（用于添加成员）
     * @throws InvalidInvitationException 如果 token 无效、已消费或已过期
     */
    @Transactional
    public void redeem(String rawToken, RedeemCommand command,
                       IdentityApi identityApi, MemberService memberService) {
        var digest = sha256Hex(rawToken);
        var invitation = invitationMapper.selectByDigestForUpdate(digest)
                .orElseThrow(InvalidInvitationException::new);

        if (invitation.getConsumedAt() != null
                || invitation.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInvitationException();
        }

        var account = identityApi.registerAccount(new IdentityApi.RegisterAccountCommand(
                command.username(), command.password(), command.displayName(), command.email()));
        memberService.addMember(invitation.getHouseholdId(), account.id(),
                HouseholdApi.MemberRole.valueOf(invitation.getRole()));
        invitationMapper.markConsumed(invitation.getId(), account.id());

        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.MEMBER_JOINED, ZijaAuditOutcome.SUCCESS, invitation.getHouseholdId(),
                account.id(), account.id(), null, null, null));
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.INVITATION_REDEEMED, ZijaAuditOutcome.SUCCESS, invitation.getHouseholdId(),
                account.id(), account.id(), null, null, null));
    }

    /**
     * 验证邀请码是否有效（未消费且未过期）。
     *
     * @param rawToken 原始 token
     * @return 有效的邀请实体，无效则返回空
     */
    @Transactional(readOnly = true)
    public Optional<InvitationEntity> inspect(String rawToken) {
        return invitationMapper.selectByDigest(sha256Hex(rawToken))
                .filter(i -> i.getConsumedAt() == null
                        && i.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * 计算输入字符串的 SHA-256 十六进制摘要。
     *
     * @param input 输入字符串
     * @return 小写十六进制摘要
     */
    static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance(ZijaDigests.SHA_256);
            var hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
