package com.zija.reminder.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 通过 JdbcTemplate 查询符合角色条件的家庭成员邮箱。
 * reminder 模块不允许依赖 identity 模块，因此直接查询 account 表。
 * 供 {@link ReminderReconciler} 与 mail 子包的 {@code MailDigestScheduler} 共用。
 */
@Component
public class MemberEmails {

    private final JdbcTemplate jdbcTemplate;

    public MemberEmails(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findByRoles(UUID householdId, List<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of();
        String placeholders = roles.stream().map(r -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT a.email FROM member m
                JOIN account a ON a.id = m.account_id
                WHERE m.household_id = ? AND m.role IN (%s) AND m.status = 'ACTIVE'
                  AND a.email IS NOT NULL AND a.email != ''
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>();
        params.add(householdId);
        params.addAll(roles);
        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }
}
