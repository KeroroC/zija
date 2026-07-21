package com.zija.household.internal;

import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "zija.command", havingValue = "recover-owner")
class OwnerRecoveryCommand implements org.springframework.boot.CommandLineRunner {

    private final HouseholdMapper householdMapper;
    private final MemberMapper memberMapper;
    private final OwnerRecoveryService recoveryService;

    OwnerRecoveryCommand(HouseholdMapper householdMapper, MemberMapper memberMapper,
                        OwnerRecoveryService recoveryService) {
        this.householdMapper = householdMapper;
        this.memberMapper = memberMapper;
        this.recoveryService = recoveryService;
    }

    @Override
    public void run(String... args) {
        var household = householdMapper.selectById((short) 1);
        if (household == null) {
            System.err.println("household not initialized");
            return;
        }
        var owner = memberMapper.selectOwner(household.getId())
                .orElse(null);
        if (owner == null) {
            System.err.println("owner not found");
            return;
        }
        var result = recoveryService.generate(household.getId(), owner.getAccountId());
        System.out.println("Recovery link: /owner-recovery#token=" + result.rawToken());
        System.out.println("Expires at: " + result.expiresAt());
    }
}
