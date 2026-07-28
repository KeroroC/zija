package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.exception.InsufficientRoleException;
import com.zija.household.internal.exception.MemberConcurrentUpdateException;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MemberServiceTest {

    @Test
    void adminCannotDeactivateOwner() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "ADMIN", "ACTIVE");
        var owner = member(householdId, "OWNER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(owner.getId())).thenReturn(owner);
        var service = service(mapper);

        assertThatThrownBy(() -> service.updateStatus(actor.getAccountId(),
                owner.getId(), "DEACTIVATED"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void cannotDeactivateSelf() {
        var mapper = mock(MemberMapper.class);
        var self = member("ADMIN", "ACTIVE");
        when(mapper.selectByAccount(self.getAccountId())).thenReturn(Optional.of(self));
        when(mapper.selectById(self.getId())).thenReturn(self);
        var service = service(mapper);

        assertThatThrownBy(() -> service.updateStatus(self.getAccountId(),
                self.getId(), "DEACTIVATED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ownerCanDeactivateAdmin() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "ADMIN", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateStatus(target.getId(), "DEACTIVATED", target.getVersion())).thenReturn(1);
        var service = service(mapper);

        service.updateStatus(actor.getAccountId(), target.getId(), "DEACTIVATED");

        verify(mapper).updateStatus(target.getId(), "DEACTIVATED", target.getVersion());
        verify(mapper).selectByAccount(actor.getAccountId());
    }

    @Test
    void adminCanDeactivateMember() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "ADMIN", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateStatus(target.getId(), "DEACTIVATED", target.getVersion())).thenReturn(1);
        var service = service(mapper);

        service.updateStatus(actor.getAccountId(), target.getId(), "DEACTIVATED");

        verify(mapper).updateStatus(target.getId(), "DEACTIVATED", target.getVersion());
    }

    @Test
    void statusUpdateConflictStopsAccountAndAuditSideEffects() {
        var mapper = mock(MemberMapper.class);
        var identityApi = mock(IdentityApi.class);
        var systemApi = mock(SystemApi.class);
        var sessionInvalidator = mock(ZijaSessionInvalidator.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateStatus(target.getId(), "DEACTIVATED", target.getVersion())).thenReturn(0);
        var service = new MemberService(mapper, identityApi, systemApi, sessionInvalidator);

        assertThatThrownBy(() -> service.updateStatus(
                actor.getAccountId(), target.getId(), "DEACTIVATED"))
                .isInstanceOf(MemberConcurrentUpdateException.class)
                .hasMessage("member was modified concurrently");
        verifyNoInteractions(identityApi, systemApi, sessionInvalidator);
    }

    @Test
    void adminCannotDeactivateAdmin() {
        assertStatusChangeDenied("ADMIN", "ADMIN", "ACTIVE", false);
    }

    @Test
    void memberCannotDeactivateMember() {
        assertStatusChangeDenied("MEMBER", "MEMBER", "ACTIVE", false);
    }

    @Test
    void inactiveOwnerCannotChangeMemberStatus() {
        assertStatusChangeDenied("OWNER", "MEMBER", "DEACTIVATED", false);
    }

    @Test
    void actorCannotChangeStatusAcrossHouseholds() {
        assertStatusChangeDenied("OWNER", "MEMBER", "ACTIVE", true);
    }

    @Test
    void statusMustBeActiveOrDeactivated() {
        var service = service(mock(MemberMapper.class));

        assertThatThrownBy(() -> service.updateStatus(
                UUID.randomUUID(), UUID.randomUUID(), "SUSPENDED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerCanPromoteMemberToAdmin() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(target.getId(), "ADMIN", target.getVersion())).thenReturn(1);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        service.updateRole(actor.getAccountId(), target.getId(), "ADMIN");
        verify(mapper).updateRole(target.getId(), "ADMIN", target.getVersion());
        verify(mapper).selectByAccount(actor.getAccountId());
    }

    @Test
    void roleUpdateConflictDoesNotRecordSuccessAudit() {
        var mapper = mock(MemberMapper.class);
        var systemApi = mock(SystemApi.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(target.getId(), "ADMIN", target.getVersion())).thenReturn(0);
        var service = new MemberService(mapper, mock(IdentityApi.class), systemApi,
                mock(ZijaSessionInvalidator.class));

        assertThatThrownBy(() -> service.updateRole(actor.getAccountId(), target.getId(), "ADMIN"))
                .isInstanceOf(MemberConcurrentUpdateException.class)
                .hasMessage("member was modified concurrently");
        verifyNoInteractions(systemApi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MEMBER"})
    void nonOwnerCannotUpdateRole(String actorRole) {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, actorRole, "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.updateRole(actor.getAccountId(), target.getId(), "ADMIN"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void inactiveOwnerCannotUpdateRole() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "OWNER", "DEACTIVATED");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.updateRole(actor.getAccountId(), target.getId(), "ADMIN"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void ownerCannotUpdateRoleAcrossHouseholds() {
        var mapper = mock(MemberMapper.class);
        var actor = member("OWNER", "ACTIVE");
        var target = member("MEMBER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.updateRole(actor.getAccountId(), target.getId(), "ADMIN"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void ownerCannotChangeOwnerRole() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "OWNER", "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.updateRole(actor.getAccountId(), target.getId(), "MEMBER"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void roleMustBeAdminOrMember() {
        var service = service(mock(MemberMapper.class));

        assertThatThrownBy(() -> service.updateRole(UUID.randomUUID(), UUID.randomUUID(), "AUDITOR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotSetRoleToOwner() {
        var mapper = mock(MemberMapper.class);
        var target = member("MEMBER", "ACTIVE");
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));

        assertThatThrownBy(() -> service.updateRole(UUID.randomUUID(),
                target.getId(), "OWNER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeOwnerCanTransferOwnershipToActiveMember() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var owner = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(owner.getAccountId())).thenReturn(Optional.of(owner));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(owner.getId(), "ADMIN", owner.getVersion())).thenReturn(1);
        when(mapper.updateRole(target.getId(), "OWNER", target.getVersion())).thenReturn(1);
        var service = service(mapper);

        service.transferOwnership(owner.getAccountId(), target.getId());

        verify(mapper).updateRole(owner.getId(), "ADMIN", owner.getVersion());
        verify(mapper).updateRole(target.getId(), "OWNER", target.getVersion());
    }

    @Test
    void ownershipTransferStopsWhenCurrentOwnerUpdateConflicts() {
        var mapper = mock(MemberMapper.class);
        var identityApi = mock(IdentityApi.class);
        var systemApi = mock(SystemApi.class);
        var sessionInvalidator = mock(ZijaSessionInvalidator.class);
        var householdId = UUID.randomUUID();
        var owner = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(owner.getAccountId())).thenReturn(Optional.of(owner));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(owner.getId(), "ADMIN", owner.getVersion())).thenReturn(0);
        var service = new MemberService(mapper, identityApi, systemApi, sessionInvalidator);

        assertThatThrownBy(() -> service.transferOwnership(owner.getAccountId(), target.getId()))
                .isInstanceOf(MemberConcurrentUpdateException.class)
                .hasMessage("member was modified concurrently");
        verify(mapper, never()).updateRole(target.getId(), "OWNER", target.getVersion());
        verifyNoInteractions(identityApi, systemApi, sessionInvalidator);
    }

    @Test
    void ownershipTransferStopsWhenTargetUpdateConflicts() {
        var mapper = mock(MemberMapper.class);
        var identityApi = mock(IdentityApi.class);
        var systemApi = mock(SystemApi.class);
        var sessionInvalidator = mock(ZijaSessionInvalidator.class);
        var householdId = UUID.randomUUID();
        var owner = member(householdId, "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(owner.getAccountId())).thenReturn(Optional.of(owner));
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(owner.getId(), "ADMIN", owner.getVersion())).thenReturn(1);
        when(mapper.updateRole(target.getId(), "OWNER", target.getVersion())).thenReturn(0);
        var service = new MemberService(mapper, identityApi, systemApi, sessionInvalidator);

        assertThatThrownBy(() -> service.transferOwnership(owner.getAccountId(), target.getId()))
                .isInstanceOf(MemberConcurrentUpdateException.class)
                .hasMessage("member was modified concurrently");
        verifyNoInteractions(identityApi, systemApi, sessionInvalidator);
    }

    @Test
    void forgedActorCannotTransferAnotherHouseholdsOwnership() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var forgedActor = member(UUID.randomUUID(), "OWNER", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(forgedActor.getAccountId())).thenReturn(Optional.of(forgedActor));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.transferOwnership(forgedActor.getAccountId(), target.getId()))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void activeAdminCannotTransferOwnership() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var admin = member(householdId, "ADMIN", "ACTIVE");
        var target = member(householdId, "MEMBER", "ACTIVE");
        when(mapper.selectByAccount(admin.getAccountId())).thenReturn(Optional.of(admin));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.transferOwnership(admin.getAccountId(), target.getId()))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void inactiveOwnerCannotTransferOwnership() {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var owner = member(householdId, "OWNER", "DEACTIVATED");
        var target = member(householdId, "ADMIN", "ACTIVE");
        when(mapper.selectByAccount(owner.getAccountId())).thenReturn(Optional.of(owner));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.transferOwnership(owner.getAccountId(), target.getId()))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "DEACTIVATED"})
    void ownershipTargetMustBeActiveAndNotOwner(String disqualifier) {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var owner = member(householdId, "OWNER", "ACTIVE");
        var target = "OWNER".equals(disqualifier)
                ? member(householdId, "OWNER", "ACTIVE")
                : member(householdId, "MEMBER", "DEACTIVATED");
        when(mapper.selectByAccount(owner.getAccountId())).thenReturn(Optional.of(owner));
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = service(mapper);

        assertThatThrownBy(() -> service.transferOwnership(owner.getAccountId(), target.getId()))
                .isInstanceOf(InsufficientRoleException.class);
    }

    private MemberEntity member(String role, String status) {
        return member(UUID.randomUUID(), role, status);
    }

    private MemberEntity member(UUID householdId, String role, String status) {
        var m = new MemberEntity();
        m.setId(UUID.randomUUID());
        m.setHouseholdId(householdId);
        m.setAccountId(UUID.randomUUID());
        m.setRole(role);
        m.setStatus(status);
        m.setVersion(0);
        return m;
    }

    private MemberService service(MemberMapper mapper) {
        return new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class),
                mock(ZijaSessionInvalidator.class));
    }

    private void assertStatusChangeDenied(
            String actorRole, String targetRole, String actorStatus, boolean crossHousehold
    ) {
        var mapper = mock(MemberMapper.class);
        var householdId = UUID.randomUUID();
        var actor = member(householdId, actorRole, actorStatus);
        var target = member(crossHousehold ? UUID.randomUUID() : householdId, targetRole, "ACTIVE");
        when(mapper.selectByAccount(actor.getAccountId())).thenReturn(Optional.of(actor));
        when(mapper.selectById(target.getId())).thenReturn(target);

        assertThatThrownBy(() -> service(mapper).updateStatus(
                actor.getAccountId(), target.getId(), "DEACTIVATED"))
                .isInstanceOf(InsufficientRoleException.class);
    }
}
