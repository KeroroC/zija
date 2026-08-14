package com.zija.household.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.household.internal.exception.InvalidInvitationException;
import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
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
 * 家庭所有者密码恢复服务，为忘记密码的 OWNER 提供基于一次性 token 的密码重置机制。
 * <p>
 * 恢复 token 有效期为 15 分钟，使用后即失效。存储时仅保留 SHA-256 摘要，
 * 生成新 token 时自动作废该账户所有未消费的旧 token。
 */
@Service
class OwnerRecoveryService {

    private final OwnerRecoveryTokenMapper tokenMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    OwnerRecoveryService(OwnerRecoveryTokenMapper tokenMapper, IdentityApi identityApi,
                        SystemApi systemApi) {
        this.tokenMapper = tokenMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record GenerateResult(UUID id, String rawToken, OffsetDateTime expiresAt) {
    }

    /**
     * 生成密码恢复 token，先作废该账户所有未消费的旧 token。
     *
     * @param householdId    家庭 ID
     * @param ownerAccountId OWNER 账户 ID
     * @return 生成结果，包含 token ID、原始 token 和过期时间
     */
    @Transactional
    public GenerateResult generate(UUID householdId, UUID ownerAccountId) {
        tokenMapper.invalidatePending(ownerAccountId);

        var rawBytes = new byte[32];
        random.nextBytes(rawBytes);
        var rawToken = base64Url.encodeToString(rawBytes);
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15);

        var entity = new OwnerRecoveryTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setAccountId(ownerAccountId);
        entity.setTokenDigest(InvitationService.sha256Hex(rawToken));
        entity.setExpiresAt(expiresAt);
        tokenMapper.insert(entity);

        return new GenerateResult(entity.getId(), rawToken, expiresAt);
    }

    /**
     * 使用恢复 token 重置 OWNER 密码。
     *
     * @param rawToken   原始 token
     * @param newPassword 新密码
     * @throws InvalidInvitationException 如果 token 无效、已消费或已过期
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var digest = InvitationService.sha256Hex(rawToken);
        var token = tokenMapper.selectByDigestForUpdate(digest)
                .orElseThrow(InvalidInvitationException::new);
        if (token.getConsumedAt() != null
                || token.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInvitationException();
        }
        tokenMapper.markConsumed(token.getId());
        identityApi.resetPassword(token.getAccountId(), newPassword);
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "OWNER_RECOVERY", ZijaAuditOutcome.SUCCESS, token.getHouseholdId(),
                token.getAccountId(), token.getAccountId(), null, null, null));
    }

    /**
     * 验证恢复 token 是否有效（未消费且未过期）。
     *
     * @param rawToken 原始 token
     * @return 有效的 token 实体，无效则返回空
     */
    @Transactional(readOnly = true)
    public Optional<OwnerRecoveryTokenEntity> inspect(String rawToken) {
        return tokenMapper.selectByDigest(InvitationService.sha256Hex(rawToken))
                .filter(t -> t.getConsumedAt() == null
                        && t.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
