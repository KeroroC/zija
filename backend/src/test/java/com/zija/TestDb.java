package com.zija;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

/**
 * 集成测试统一数据清理入口。
 *
 * <p>所有集成测试共用 {@link SharedPostgres} 单例容器，测试类之间必须自行隔离数据。
 * 此前 32 个测试类各自手写 {@code TRUNCATE}，表清单从 2 张到 16 张不等、顺序各异，
 * 新增表时需要逐处补齐，极易遗漏。现统一为本类的 {@link #cleanAll(JdbcTemplate)}：
 * 表清单只维护一处，完整性由 {@code TestDbTableCoverageTest} 双向守护。
 *
 * <p>单条 {@code TRUNCATE} 语句 = 单事务单次提交，实测约 18ms；
 * 固定的表顺序让所有测试类的锁获取顺序一致，避免将来再次出现锁顺序反转导致的死锁。
 */
public final class TestDb {

    /**
     * 需要清理的业务表，按「子表 → 父表」固定顺序排列。
     *
     * <p>顺序对正确性并非必需（{@code CASCADE} 已处理依赖），但固定顺序是对并发场景的防御。
     * 新增表后若忘记登记，{@code TestDbTableCoverageTest} 会失败。
     */
    private static final List<String> TABLES = List.of(
            // reporting 读模型与投影
            "reporting_stock_flat",
            "reporting_movement_flat",
            "reporting_search_index",
            "reporting_event_dead_letter",
            "reporting_processed_event",
            // inventory
            "inventory_stocktake_item",
            "inventory_stocktake",
            "inventory_idempotency_record",
            "inventory_movement",
            "inventory_stock_position",
            "inventory_lot",
            // reminder
            "reminder_household_mail_setting",
            "reminder_event_dead_letter",
            "reminder_processed_event",
            "reminder_notification",
            "reminder_task",
            "reminder_household_rule",
            // catalog
            "catalog_item_tag",
            "catalog_item",
            "catalog_tag",
            "catalog_unit",
            "catalog_brand",
            "catalog_category",
            // location / file / 事件发布
            "location",
            "stored_file",
            "event_publication",
            // 会话
            "spring_session_attributes",
            "spring_session",
            // identity / household / 审计
            "audit_log",
            "owner_recovery_token",
            "invitation",
            "member",
            "household",
            "account"
    );

    /**
     * 永不清理的表。
     *
     * <ul>
     *   <li>{@code flyway_schema_history}：Flyway 自身元数据，清空会导致后续迁移校验失败。
     *   <li>{@code system_installation}：V1 迁移插入的单例种子行，
     *       {@code SystemInstallationMapperIntegrationTest} 等直接依赖它。
     * </ul>
     */
    private static final Set<String> EXCLUDED = Set.of(
            "flyway_schema_history",
            "system_installation"
    );

    private static final String TRUNCATE_SQL =
            "TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE";

    private TestDb() {
    }

    /**
     * 清空全部业务表并重置自增序列。通常在 {@code @BeforeEach} 中调用。
     *
     * <p>失败直接抛出，不做重试——测试环境应快速失败，重试只会掩盖问题。
     */
    public static void cleanAll(JdbcTemplate jdbc) {
        jdbc.execute(TRUNCATE_SQL);
    }

    /** 供 {@code TestDbTableCoverageTest} 校验清单完整性。 */
    static List<String> managedTables() {
        return TABLES;
    }

    /** 供 {@code TestDbTableCoverageTest} 校验清单完整性。 */
    static Set<String> excludedTables() {
        return EXCLUDED;
    }
}
