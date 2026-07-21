package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Transactional
    public void addMember(UUID householdId, UUID accountId, HouseholdApi.MemberRole role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role.name());
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
    }

    @Transactional
    public void updateRole(UUID actorAccountId, UUID targetMemberId, String newRole) {
        if (!"ADMIN".equals(newRole) && !"MEMBER".equals(newRole)) {
            throw new IllegalArgumentException("role must be ADMIN or MEMBER");
        }
        var target = requireMember(targetMemberId);
        var actor = requireActiveActor(actorAccountId, target.getHouseholdId());
        if (!"OWNER".equals(actor.getRole())
                || (!"ADMIN".equals(target.getRole()) && !"MEMBER".equals(target.getRole()))) {
            throw new InsufficientRoleException();
        }
        memberMapper.updateRole(targetMemberId, newRole, target.getVersion());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "ROLE_CHANGED", "SUCCESS", target.getHouseholdId(),
                actorAccountId, target.getAccountId(), null, null,
                java.util.Map.of("oldRole", target.getRole(), "newRole", newRole)));
    }

    @Transactional
    public void updateStatus(UUID actorAccountId, UUID targetMemberId, String newStatus) {
        if (!"ACTIVE".equals(newStatus) && !"DEACTIVATED".equals(newStatus)) {
            throw new IllegalArgumentException("status must be ACTIVE or DEACTIVATED");
        }
        var target = requireMember(targetMemberId);
        var actor = requireActiveActor(actorAccountId, target.getHouseholdId());
        if (actorAccountId.equals(target.getAccountId())) {
            throw new IllegalStateException("cannot change own status");
        }
        var ownerManagingAssignableRole = "OWNER".equals(actor.getRole())
                && ("ADMIN".equals(target.getRole()) || "MEMBER".equals(target.getRole()));
        var adminManagingMember = "ADMIN".equals(actor.getRole())
                && "MEMBER".equals(target.getRole());
        if (!ownerManagingAssignableRole && !adminManagingMember) {
            throw new InsufficientRoleException();
        }
        memberMapper.updateStatus(targetMemberId, newStatus, target.getVersion());
        if ("DEACTIVATED".equals(newStatus)) {
            identityApi.disableAccount(target.getAccountId());
            sessionInvalidator.invalidateAllForAccount(target.getAccountId());
        } else {
            identityApi.activateAccount(target.getAccountId());
        }
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "DEACTIVATED".equals(newStatus) ? "MEMBER_DEACTIVATED" : "MEMBER_REACTIVATED",
                "SUCCESS", target.getHouseholdId(),
                actorAccountId, target.getAccountId(), null, null, null));
    }

    @Transactional
    public void transferOwnership(UUID currentOwnerAccountId, UUID targetMemberId) {
        var target = requireMember(targetMemberId);
        if (!"ACTIVE".equals(target.getStatus())
                || target.getRole().equals("OWNER")) {
            throw new InsufficientRoleException();
        }
        var household = target.getHouseholdId();
        var currentOwner = requireActiveActor(currentOwnerAccountId, household);
        if (!"OWNER".equals(currentOwner.getRole())) {
            throw new InsufficientRoleException();
        }
        memberMapper.updateRole(currentOwner.getId(), "ADMIN", currentOwner.getVersion());
        memberMapper.updateRole(targetMemberId, "OWNER", target.getVersion());
        identityApi.disableAccount(currentOwner.getAccountId());
        identityApi.disableAccount(target.getAccountId());
        identityApi.activateAccount(currentOwner.getAccountId());
        identityApi.activateAccount(target.getAccountId());
        sessionInvalidator.invalidateAllForAccount(currentOwner.getAccountId());
        sessionInvalidator.invalidateAllForAccount(target.getAccountId());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "OWNERSHIP_TRANSFERRED", "SUCCESS", household,
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

    private MemberEntity requireActiveActor(UUID accountId, UUID householdId) {
        var actor = memberMapper.selectByAccount(accountId)
                .orElseThrow(InsufficientRoleException::new);
        if (!"ACTIVE".equals(actor.getStatus())
                || !householdId.equals(actor.getHouseholdId())) {
            throw new InsufficientRoleException();
        }
        return actor;
    }
}
