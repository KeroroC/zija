package com.zija.file.internal;

import com.zija.SharedPostgres;
import com.zija.TestDb;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8 迁移升级路径测试：模拟「V7 时代只有封面槽位」的既有数据，
 * 直接执行迁移文件中的回填 UPDATE，验证既有封面升级为挂在物品上的附件
 * 且封面指定保持、名字规范化与应用侧口径一致。
 *
 * <p>测试容器总是 fresh DB（V1→V8 顺序执行），迁移文件的 UPDATE 在空表上是
 * no-op，因此必须单独重放该语句才能覆盖用户故事 45 的升级场景。</p>
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class CoverMigrationUpgradeIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbcTemplate);

        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("升级测试家");
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        householdId = h.getId();
    }

    @Test
    void v8BackfillTurnsCoverSlotFilesIntoItemMountedAttachments() throws Exception {
        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, unitId, householdId, "个", "个");

        UUID itemA = seedItemWithCover(unitId, "冰箱");
        UUID itemB = seedItemWithCover(unitId, "吸尘器");
        UUID coverA = seedCoverFile("coverA.jpg", itemA);
        UUID coverB = seedCoverFile("coverB.jpg", itemB);

        executeV8BackfillUpdate();

        // 既有封面升级为挂在对应物品上的附件，name_normalized 与应用侧口径一致
        var rowA = jdbcTemplate.queryForMap(
                "SELECT mount_type, mount_id, name_normalized FROM stored_file WHERE id = ?", coverA);
        assertThat(rowA.get("mount_type")).isEqualTo("ITEM");
        assertThat(rowA.get("mount_id")).isEqualTo(itemA);
        assertThat(rowA.get("name_normalized"))
                .isEqualTo(FileService.normalizeName("coverA.jpg"));

        var rowB = jdbcTemplate.queryForMap(
                "SELECT mount_type, mount_id, name_normalized FROM stored_file WHERE id = ?", coverB);
        assertThat(rowB.get("mount_type")).isEqualTo("ITEM");
        assertThat(rowB.get("mount_id")).isEqualTo(itemB);

        // 封面指定保持
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cover_file_id FROM catalog_item WHERE id = ?", UUID.class, itemA))
                .isEqualTo(coverA);

        // 已有规范化名不被覆盖（COALESCE）
        UUID itemC = seedItemWithCover(unitId, "烤箱");
        UUID coverC = seedCoverFile("coverC.jpg", itemC);
        jdbcTemplate.update("UPDATE stored_file SET name_normalized = '既有名' WHERE id = ?", coverC);
        executeV8BackfillUpdate();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name_normalized FROM stored_file WHERE id = ?", String.class, coverC))
                .isEqualTo("既有名");

        // 两个物品同名封面不冲突（唯一索引按 mount_id 区分）
        UUID itemD = seedItemWithCover(unitId, "空调");
        UUID coverD = seedCoverFile("coverA.jpg", itemD);
        executeV8BackfillUpdate();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT mount_id FROM stored_file WHERE id = ?", UUID.class, coverD))
                .isEqualTo(itemD);
    }

    private UUID seedItemWithCover(UUID unitId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, status, version)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ACTIVE', 0)
                """, id, householdId, name, unitId);
        return id;
    }

    private UUID seedCoverFile(String filename, UUID itemId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO stored_file
                    (id, household_id, storage_key, original_filename, declared_media_type,
                     detected_media_type, byte_size, sha256, created_at)
                VALUES (?, ?, ?, ?, 'image/jpeg', 'image/jpeg', 10, 'hash', CURRENT_TIMESTAMP)
                """, id, householdId, "2026/07/" + id + ".jpg", filename);
        jdbcTemplate.update("UPDATE catalog_item SET cover_file_id = ? WHERE id = ?", id, itemId);
        return id;
    }

    /** 从迁移文件提取并执行回填 UPDATE（测试的就是迁移文件里的真实语句）。 */
    private void executeV8BackfillUpdate() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V8__attachment_recycle_bin.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String update = java.util.Arrays.stream(sql.split(";"))
                .filter(stmt -> stmt.contains("UPDATE stored_file sf"))
                .map(stmt -> stmt.substring(stmt.indexOf("UPDATE stored_file sf")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("V8 迁移文件中找不到回填 UPDATE"));
        jdbcTemplate.execute(update);
    }
}
