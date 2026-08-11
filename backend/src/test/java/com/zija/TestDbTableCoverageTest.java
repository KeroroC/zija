package com.zija;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守护测试：{@link TestDb} 的表清单必须与数据库实际表结构保持一致。
 *
 * <p>双向比对，两个方向都会腐烂：
 * <ul>
 *   <li>迁移新增了表但没登记 → 该表永远不会被清理，测试间数据污染，且症状随执行顺序漂移；
 *   <li>迁移删除了表但清单没删 → {@code TRUNCATE} 直接报错，全部集成测试崩溃。
 * </ul>
 *
 * <p>清单与排除项的并集必须恰好等于 {@code public} schema 下的全部基础表。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestDbTableCoverageTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void everyDatabaseTableIsEitherManagedOrExplicitlyExcluded() {
        Set<String> actual = new TreeSet<>(jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class));

        Set<String> declared = new TreeSet<>(TestDb.managedTables());
        declared.addAll(TestDb.excludedTables());

        Set<String> unregistered = new TreeSet<>(actual);
        unregistered.removeAll(declared);
        assertThat(unregistered)
                .as("数据库中存在未登记的表：请加入 TestDb.TABLES（需要清理）或 TestDb.EXCLUDED（种子/元数据表）")
                .isEmpty();

        Set<String> stale = new TreeSet<>(declared);
        stale.removeAll(actual);
        assertThat(stale)
                .as("TestDb 清单中的表在数据库里已不存在：请从 TestDb.TABLES / TestDb.EXCLUDED 中移除，否则 TRUNCATE 会报错")
                .isEmpty();
    }

    @Test
    void managedTableListHasNoDuplicates() {
        List<String> tables = TestDb.managedTables();
        assertThat(tables)
                .as("TestDb.TABLES 存在重复表名")
                .doesNotHaveDuplicates();
    }

    @Test
    void cleanAllExecutesSuccessfully() {
        TestDb.cleanAll(jdbc);
    }
}
