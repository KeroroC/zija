package com.zija.household.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerRecoveryCommandTest {

    @Test
    void printsRecoveryTokenInUrlFragment() {
        var householdId = UUID.randomUUID();
        var ownerAccountId = UUID.randomUUID();
        var household = new HouseholdEntity();
        household.setId(householdId);
        var owner = new MemberEntity();
        owner.setAccountId(ownerAccountId);

        var householdMapper = mock(HouseholdMapper.class);
        var memberMapper = mock(MemberMapper.class);
        var recoveryService = mock(OwnerRecoveryService.class);
        when(householdMapper.selectById((short) 1)).thenReturn(household);
        when(memberMapper.selectOwner(householdId)).thenReturn(Optional.of(owner));
        when(recoveryService.generate(householdId, ownerAccountId)).thenReturn(
                new OwnerRecoveryService.GenerateResult(
                        UUID.randomUUID(), "raw-token", OffsetDateTime.parse("2026-07-21T12:00:00Z")));

        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            new OwnerRecoveryCommand(householdMapper, memberMapper, recoveryService).run();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Recovery link: /owner-recovery#token=raw-token")
                .doesNotContain("?token=");
    }
}
