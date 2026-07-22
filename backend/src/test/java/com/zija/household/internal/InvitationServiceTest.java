package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InvitationServiceTest {

    @Test
    void memberCannotCreateInvitation() {
        var householdId = UUID.randomUUID();
        var creator = member(householdId, "MEMBER", "ACTIVE");
        var service = service(creator);

        assertThatThrownBy(() -> service.create(householdId, creator.getAccountId(),
                HouseholdApi.MemberRole.MEMBER, 24))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void adminCannotCreateAdminInvitation() {
        var householdId = UUID.randomUUID();
        var creator = member(householdId, "ADMIN", "ACTIVE");
        var service = service(creator);

        assertThatThrownBy(() -> service.create(householdId, creator.getAccountId(),
                HouseholdApi.MemberRole.ADMIN, 24))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void creatorCannotCreateInvitationForAnotherHousehold() {
        var creator = member(UUID.randomUUID(), "OWNER", "ACTIVE");
        var service = service(creator);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), creator.getAccountId(),
                HouseholdApi.MemberRole.MEMBER, 24))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void inactiveOwnerCannotCreateInvitation() {
        var householdId = UUID.randomUUID();
        var creator = member(householdId, "OWNER", "DEACTIVATED");
        var service = service(creator);

        assertThatThrownBy(() -> service.create(householdId, creator.getAccountId(),
                HouseholdApi.MemberRole.ADMIN, 24))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void invitationRoleMustBeAdminOrMember() {
        var service = new InvitationService(mock(InvitationMapper.class),
                mock(MemberMapper.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), UUID.randomUUID(),
                HouseholdApi.MemberRole.OWNER, 24))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsInvitationAndReturnsRawTokenOnce() {
        var invitationMapper = mock(InvitationMapper.class);
        var householdId = UUID.randomUUID();
        var creator = member(householdId, "OWNER", "ACTIVE");
        var service = service(invitationMapper, creator);

        var result = service.create(householdId, creator.getAccountId(),
                HouseholdApi.MemberRole.MEMBER, 24);

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.digest()).isNotBlank();
        assertThat(result.rawToken().length()).isGreaterThan(40);
        verify(invitationMapper).insert(any(InvitationEntity.class));
    }

    @Test
    void ownerCanCreateAdminInvitation() {
        var householdId = UUID.randomUUID();
        var creator = member(householdId, "OWNER", "ACTIVE");

        var result = service(creator).create(householdId, creator.getAccountId(),
                HouseholdApi.MemberRole.ADMIN, 24);

        assertThat(result.role()).isEqualTo(HouseholdApi.MemberRole.ADMIN);
    }

    @Test
    void adminCanCreateMemberInvitation() {
        var householdId = UUID.randomUUID();
        var creator = member(householdId, "ADMIN", "ACTIVE");

        var result = service(creator).create(householdId, creator.getAccountId(),
                HouseholdApi.MemberRole.MEMBER, 24);

        assertThat(result.role()).isEqualTo(HouseholdApi.MemberRole.MEMBER);
    }

    @Test
    void redeemLocksByDigestAndCreatesMember() {
        var invitationMapper = mock(InvitationMapper.class);
        var identityApi = mock(IdentityApi.class);
        var memberService = mock(MemberService.class);
        var systemApi = mock(SystemApi.class);

        var invitation = new InvitationEntity();
        invitation.setId(UUID.randomUUID());
        invitation.setHouseholdId(UUID.randomUUID());
        invitation.setRole("MEMBER");
        invitation.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        invitation.setCreatedBy(UUID.randomUUID());
        when(invitationMapper.selectByDigestForUpdate(any())).thenReturn(Optional.of(invitation));
        when(identityApi.registerAccount(any())).thenReturn(new IdentityApi.AccountInfo(
                UUID.randomUUID(), "newuser", "新成员", null, "ACTIVE"));

        var service = new InvitationService(invitationMapper, mock(MemberMapper.class), systemApi);
        service.redeem("raw-token", new InvitationService.RedeemCommand(
                "newuser", "Passw0rd!", "新成员", null), identityApi, memberService);

        verify(invitationMapper).markConsumed(eq(invitation.getId()), any());
        verify(memberService).addMember(any(), any(), eq(HouseholdApi.MemberRole.MEMBER));
        var auditCaptor = org.mockito.ArgumentCaptor.forClass(SystemApi.AuditEvent.class);
        verify(systemApi, times(2)).recordAudit(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(SystemApi.AuditEvent::action)
                .containsExactly("MEMBER_JOINED", "INVITATION_REDEEMED");
    }

    private InvitationService service(MemberEntity creator) {
        return service(mock(InvitationMapper.class), creator);
    }

    private InvitationService service(InvitationMapper invitationMapper, MemberEntity creator) {
        var memberMapper = mock(MemberMapper.class);
        when(memberMapper.selectByAccount(creator.getAccountId())).thenReturn(Optional.of(creator));
        return new InvitationService(invitationMapper, memberMapper, mock(SystemApi.class));
    }

    private MemberEntity member(UUID householdId, String role, String status) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(UUID.randomUUID());
        member.setRole(role);
        member.setStatus(status);
        member.setVersion(0);
        return member;
    }
}
