package com.zija.household.internal;

import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 家庭所有者密码恢复命令行工具。
 * <p>
 * 通过 {@code --zija.command=recover-owner} 参数激活，执行后生成一次性
 * 密码恢复链接并输出到控制台。适用于 OWNER 忘记密码时的紧急恢复场景。
 */
@Component
@ConditionalOnProperty(name = "zija.command", havingValue = "recover-owner")
class OwnerRecoveryCommand implements org.springframework.boot.CommandLineRunner, ExitCodeGenerator {

    private final HouseholdMapper householdMapper;
    private final MemberMapper memberMapper;
    private final OwnerRecoveryService recoveryService;
    private int exitCode;

    OwnerRecoveryCommand(HouseholdMapper householdMapper, MemberMapper memberMapper,
                        OwnerRecoveryService recoveryService) {
        this.householdMapper = householdMapper;
        this.memberMapper = memberMapper;
        this.recoveryService = recoveryService;
    }

    /**
     * 执行密码恢复流程：查找家庭和 OWNER，生成恢复 token 并输出链接。
     *
     * @param args 命令行参数（未使用）
     */
    @Override
    public void run(@NonNull String @NonNull ... args) {
        var household = householdMapper.selectById((short) 1);
        if (household == null) {
            System.err.println("household not initialized");
            exitCode = 1;
            return;
        }
        var owner = memberMapper.selectOwner(household.getId())
                .orElse(null);
        if (owner == null) {
            System.err.println("owner not found");
            exitCode = 1;
            return;
        }
        var result = recoveryService.generate(household.getId(), owner.getAccountId());
        System.out.println("Recovery link: /owner-recovery#token=" + result.rawToken());
        System.out.println("Expires at: " + result.expiresAt());
        exitCode = 0;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
