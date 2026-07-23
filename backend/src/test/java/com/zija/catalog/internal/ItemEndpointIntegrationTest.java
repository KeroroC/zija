package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class ItemEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired ItemService itemService;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private UUID accountId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE catalog_item_tag, catalog_item, catalog_unit, catalog_brand,
                             catalog_category, catalog_tag, audit_log, member, household, account
                RESTART IDENTITY CASCADE
                """);

        householdId = seedHousehold();
        accountId = UUID.randomUUID();
        seedAccount(accountId);
        seedMember(householdId, accountId, "OWNER");

        principal = new ZijaPrincipal(accountId, "owner", "所有者", "hash", true);
    }

    @Test
    void getListReturnsPaginatedItems() throws Exception {
        var unitId = seedUnit();
        itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, "厨房",
                "INHERIT", null, "INHERIT", null, null);
        itemService.createItem(householdId, "大米", "CONSUMABLE", null, null, unitId, "主食",
                "INHERIT", null, "INHERIT", null, null);

        mockMvc.perform(get("/api/v1/items").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].managementType").exists())
                .andExpect(jsonPath("$.items[0].unitId").value(unitId.toString()));
    }

    @Test
    void getListFiltersByStatus() throws Exception {
        var unitId = seedUnit();
        var active = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        itemService.archiveItem(householdId, active.getId(), accountId, active.getVersion());

        mockMvc.perform(get("/api/v1/items?status=ARCHIVED").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("冰箱"))
                .andExpect(jsonPath("$.items[0].status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/items?status=ACTIVE").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void getListFiltersByNameQuery() throws Exception {
        var unitId = seedUnit();
        itemService.createItem(householdId, "双开门冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        itemService.createItem(householdId, "东北大米", "CONSUMABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        mockMvc.perform(get("/api/v1/items?q=冰").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("双开门冰箱"));
    }

    @Test
    void getListPaginatesByPageAndPageSize() throws Exception {
        var unitId = seedUnit();
        for (int i = 0; i < 25; i++) {
            itemService.createItem(householdId, "物品" + i, "DURABLE", null, null, unitId, null,
                    "INHERIT", null, "INHERIT", null, null);
        }

        mockMvc.perform(get("/api/v1/items?page=1&pageSize=10").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.items.length()").value(10));

        mockMvc.perform(get("/api/v1/items?page=3&pageSize=10").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void getListSortsByNameAscendingAndDescending() throws Exception {
        var unitId = seedUnit();
        itemService.createItem(householdId, "大米", "CONSUMABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        itemService.createItem(householdId, "空调", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        // name ASC by Unicode: 冰(U+51B0) < 大(U+5927) < 空(U+7A7A)
        mockMvc.perform(get("/api/v1/items?sort=name").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("冰箱"))
                .andExpect(jsonPath("$.items[1].name").value("大米"))
                .andExpect(jsonPath("$.items[2].name").value("空调"));

        mockMvc.perform(get("/api/v1/items?sort=-name").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("空调"))
                .andExpect(jsonPath("$.items[1].name").value("大米"))
                .andExpect(jsonPath("$.items[2].name").value("冰箱"));
    }

    @Test
    void updateItemChangesNameAndBumpsVersion() throws Exception {
        var unitId = seedUnit();
        var created = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var originalVersion = itemService.findItem(householdId, created.getId()).getVersion();

        mockMvc.perform(put("/api/v1/items/{id}", created.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"大冰箱\",\"memo\":\"厨房电器\",\"version\":" + originalVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("大冰箱"))
                .andExpect(jsonPath("$.memo").value("厨房电器"))
                .andExpect(jsonPath("$.version").value(originalVersion + 1))
                .andExpect(jsonPath("$.id").value(created.getId().toString()));
    }

    @Test
    void updateItemWithStaleVersionReturns409() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        mockMvc.perform(put("/api/v1/items/{id}", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"version\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_VERSION_CONFLICT"));
    }

    @Test
    void updateItemRejectsBlankName() throws Exception {
        var unitId = seedUnit();
        var created = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var version = itemService.findItem(householdId, created.getId()).getVersion();

        mockMvc.perform(put("/api/v1/items/{id}", created.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"version\":" + version + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void updateItemRejectsNonExistentItem() throws Exception {
        mockMvc.perform(put("/api/v1/items/{id}", UUID.randomUUID())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_ARCHIVED_DICTIONARY"));
    }

    // --- Helpers ---

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家");
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private void seedAccount(UUID id) {
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, 'owner', 'owner', '{bcrypt}$2a$10$examplehash', '所有者', 'ACTIVE')
                """, id);
    }

    private void seedMember(UUID householdId, UUID accountId, String role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
    }

    private UUID seedUnit() {
        var unitId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, unitId, householdId, "个", "个");
        return unitId;
    }

    private RequestPostProcessor auth() {
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }

    private RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}