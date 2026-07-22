package com.zija.household.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerRecoveryCommandTest {

    @Test
    void missingHouseholdReturnsNonZeroExitCode() {
        var command = new OwnerRecoveryCommand(
                mock(HouseholdMapper.class), mock(MemberMapper.class), mock(OwnerRecoveryService.class));

        command.run();

        assertThat(command.getExitCode()).isEqualTo(1);
    }

    @Test
    void missingOwnerReturnsNonZeroExitCode() {
        var householdMapper = mock(HouseholdMapper.class);
        var memberMapper = mock(MemberMapper.class);
        var household = household();
        when(householdMapper.selectById((short) 1)).thenReturn(household);
        when(memberMapper.selectOwner(household.getId())).thenReturn(Optional.empty());
        var command = new OwnerRecoveryCommand(
                householdMapper, memberMapper, mock(OwnerRecoveryService.class));

        command.run();

        assertThat(command.getExitCode()).isEqualTo(1);
    }

    @Test
    void generatedRecoveryLinkReturnsZeroExitCode() {
        var householdMapper = mock(HouseholdMapper.class);
        var memberMapper = mock(MemberMapper.class);
        var recoveryService = mock(OwnerRecoveryService.class);
        var household = household();
        var owner = new MemberEntity();
        owner.setAccountId(UUID.randomUUID());
        when(householdMapper.selectById((short) 1)).thenReturn(household);
        when(memberMapper.selectOwner(household.getId())).thenReturn(Optional.of(owner));
        when(recoveryService.generate(household.getId(), owner.getAccountId()))
                .thenReturn(new OwnerRecoveryService.GenerateResult(
                        UUID.randomUUID(), "raw-token", OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15)));
        var command = new OwnerRecoveryCommand(householdMapper, memberMapper, recoveryService);

        command.run();

        assertThat(command.getExitCode()).isZero();
        verify(recoveryService).generate(household.getId(), owner.getAccountId());
    }

    private HouseholdEntity household() {
        var household = new HouseholdEntity();
        household.setId(UUID.randomUUID());
        return household;
    }
}
