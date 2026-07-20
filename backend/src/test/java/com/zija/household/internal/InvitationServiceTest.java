package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InvitationServiceTest {

    @Test
    void createsInvitationAndReturnsRawTokenOnce() {
        var invitationMapper = mock(InvitationMapper.class);
        var service = new InvitationService(invitationMapper, mock(SystemApi.class));

        var result = service.create(UUID.randomUUID(), UUID.randomUUID(),
                HouseholdApi.MemberRole.MEMBER, 24);

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.digest()).isNotBlank();
        assertThat(result.rawToken().length()).isGreaterThan(40);
        verify(invitationMapper).insert(any(InvitationEntity.class));
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

        var service = new InvitationService(invitationMapper, systemApi);
        service.redeem("raw-token", new InvitationService.RedeemCommand(
                "newuser", "Passw0rd!", "新成员", null), identityApi, memberService);

        verify(invitationMapper).markConsumed(eq(invitation.getId()), any());
        verify(memberService).addMember(any(), any(), eq(HouseholdApi.MemberRole.MEMBER));
    }
}
