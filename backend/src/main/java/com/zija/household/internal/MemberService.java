package com.zija.household.internal;

import com.zija.shared.ZijaAuditOutcome;
import com.zija.shared.ZijaMemberRole;
import com.zija.shared.ZijaMemberStatus;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.InsufficientRoleException;
import com.zija.household.internal.exception.InvalidCredentialsException;
import com.zija.household.internal.exception.MemberConcurrentUpdateException;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 家庭成员管理服务，处理成员的角色变更、状态管理和所有权转移。
 * <p>
 * 提供成员添加、角色升降级（OWNER > ADMIN > MEMBER）、成员停用/激活、
 * 以及所有权转移等操作。所有操作均进行权限校验并记录审计日志。
 * 成员停用时会同时禁用其账户并失效所有会话。
 */
@Service
class MemberService {

    private final MemberMapper memberMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;
    private final ZijaSessionInvalidator sessionInvalidator;

    MemberService(MemberMapper memberMapper, IdentityApi identityApi, SystemApi systemApi,
                  ZijaSessionInvalidator sessionInvalidator) {
        this.memberMapper = memberMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
        this.sessionInvalidator = sessionInvalidator;
    }

    /**
     * 向家庭添加新成员。
     *
     * @param householdId 家庭 ID
     * @param accountId   账户 ID
     * @param role        成员角色
     */
    @Transactional
    public void addMember(UUID householdId, UUID accountId, HouseholdApi.MemberRole role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role.name());
        member.setStatus(ZijaMemberStatus.ACTIVE);
        memberMapper.insert(member);
    }

    /**
     * 修改成员角色。仅 OWNER 可操作，且只能修改 ADMIN 或 MEMBER 的角色。
     *
     * @param actorAccountId  操作人账户 ID
     * @param targetMemberId  目标成员 ID
     * @param newRole         新角色（ADMIN 或 MEMBER）
     * @throws InsufficientRoleException 如果操作人权限不足
     */
    @Transactional
    public void updateRole(UUID actorAccountId, UUID targetMemberId, String newRole) {
        if (!ZijaMemberRole.ADMIN.equals(newRole) && !ZijaMemberRole.MEMBER.equals(newRole)) {
            throw new IllegalArgumentException("role must be ADMIN or MEMBER");
        }
        var target = requireMember(targetMemberId);
        var actor = requireActiveActor(actorAccountId, target.getHouseholdId());
        if (!ZijaMemberRole.OWNER.equals(actor.getRole())
                || (!ZijaMemberRole.ADMIN.equals(target.getRole()) && !ZijaMemberRole.MEMBER.equals(target.getRole()))) {
            throw new InsufficientRoleException();
        }
        requireSingleMemberUpdate(memberMapper.updateRole(
                targetMemberId, newRole, target.getVersion()));
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.ROLE_CHANGED, ZijaAuditOutcome.SUCCESS, target.getHouseholdId(),
                actorAccountId, target.getAccountId(), null, null,
                java.util.Map.of("oldRole", target.getRole(), "newRole", newRole)));
    }

    /**
     * 修改成员状态（激活/停用）。停用时同时禁用账户并失效会话。
     * <p>
     * 权限规则：OWNER 可管理 ADMIN 和 MEMBER，ADMIN 可管理 MEMBER，不可操作自身。
     *
     * @param actorAccountId 操作人账户 ID
     * @param targetMemberId 目标成员 ID
     * @param newStatus      新状态（ACTIVE 或 DEACTIVATED）
     * @throws InsufficientRoleException 如果操作人权限不足
     */
    @Transactional
    public void updateStatus(UUID actorAccountId, UUID targetMemberId, String newStatus) {
        if (!ZijaMemberStatus.ACTIVE.equals(newStatus) && !ZijaMemberStatus.DEACTIVATED.equals(newStatus)) {
            throw new IllegalArgumentException("status must be ACTIVE or DEACTIVATED");
        }
        var target = requireMember(targetMemberId);
        var actor = requireActiveActor(actorAccountId, target.getHouseholdId());
        if (actorAccountId.equals(target.getAccountId())) {
            throw new IllegalStateException("cannot change own status");
        }
        var ownerManagingAssignableRole = ZijaMemberRole.OWNER.equals(actor.getRole())
                && (ZijaMemberRole.ADMIN.equals(target.getRole()) || ZijaMemberRole.MEMBER.equals(target.getRole()));
        var adminManagingMember = ZijaMemberRole.ADMIN.equals(actor.getRole())
                && ZijaMemberRole.MEMBER.equals(target.getRole());
        if (!ownerManagingAssignableRole && !adminManagingMember) {
            throw new InsufficientRoleException();
        }
        requireSingleMemberUpdate(memberMapper.updateStatus(
                targetMemberId, newStatus, target.getVersion()));
        if (ZijaMemberStatus.DEACTIVATED.equals(newStatus)) {
            identityApi.disableAccount(target.getAccountId());
            sessionInvalidator.invalidateAllForAccount(target.getAccountId());
        } else {
            identityApi.activateAccount(target.getAccountId());
        }
        systemApi.recordAudit(new SystemApi.AuditEvent(
                ZijaMemberStatus.DEACTIVATED.equals(newStatus)
                        ? SystemApi.AuditAction.MEMBER_DEACTIVATED : SystemApi.AuditAction.MEMBER_REACTIVATED,
                ZijaAuditOutcome.SUCCESS, target.getHouseholdId(),
                actorAccountId, target.getAccountId(), null, null, null));
    }

    /**
     * 转移家庭所有权。当前 OWNER 变为 ADMIN，目标成员变为 OWNER。
     * 转移完成后双方账户会被短暂禁用再激活，同时失效所有会话以确保安全。
     *
     * @param currentOwnerAccountId 当前 OWNER 的账户 ID
     * @param targetMemberId        目标成员 ID
     * @throws InsufficientRoleException 如果操作人非 OWNER 或目标成员状态异常
     */
    @Transactional
    public void transferOwnership(UUID currentOwnerAccountId, UUID targetMemberId) {
        var target = requireMember(targetMemberId);
        if (!ZijaMemberStatus.ACTIVE.equals(target.getStatus())
                || target.getRole().equals(ZijaMemberRole.OWNER)) {
            throw new InsufficientRoleException();
        }
        var household = target.getHouseholdId();
        var currentOwner = requireActiveActor(currentOwnerAccountId, household);
        if (!ZijaMemberRole.OWNER.equals(currentOwner.getRole())) {
            throw new InsufficientRoleException();
        }
        requireSingleMemberUpdate(memberMapper.updateRole(
                currentOwner.getId(), ZijaMemberRole.ADMIN, currentOwner.getVersion()));
        requireSingleMemberUpdate(memberMapper.updateRole(
                targetMemberId, ZijaMemberRole.OWNER, target.getVersion()));
        identityApi.disableAccount(currentOwner.getAccountId());
        identityApi.disableAccount(target.getAccountId());
        identityApi.activateAccount(currentOwner.getAccountId());
        identityApi.activateAccount(target.getAccountId());
        sessionInvalidator.invalidateAllForAccount(currentOwner.getAccountId());
        sessionInvalidator.invalidateAllForAccount(target.getAccountId());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.OWNERSHIP_TRANSFERRED, ZijaAuditOutcome.SUCCESS, household,
                currentOwnerAccountId, target.getAccountId(), null, null,
                java.util.Map.of("oldOwner", currentOwner.getAccountId().toString(),
                        "newOwner", target.getAccountId().toString())));
    }

    private MemberEntity requireMember(UUID memberId) {
        var member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new InvalidCredentialsException();
        }
        return member;
    }

    private void requireSingleMemberUpdate(int updatedRows) {
        if (updatedRows != 1) {
            throw new MemberConcurrentUpdateException();
        }
    }

    private MemberEntity requireActiveActor(UUID accountId, UUID householdId) {
        var actor = memberMapper.selectByAccount(accountId)
                .orElseThrow(InsufficientRoleException::new);
        if (!ZijaMemberStatus.ACTIVE.equals(actor.getStatus())
                || !householdId.equals(actor.getHouseholdId())) {
            throw new InsufficientRoleException();
        }
        return actor;
    }
}
