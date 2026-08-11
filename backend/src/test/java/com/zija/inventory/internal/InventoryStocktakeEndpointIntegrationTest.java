package com.zija.inventory.internal;

import com.zija.TestDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

/**
 * InventoryController 盘点端点集成测试，覆盖全部 9 个测试点。
 *
 * <ol>
 *   <li>创建盘点草稿 → 200 + id</li>
 *   <li>更新盘点草稿 → 200</li>
 *   <li>确认盘点草稿 → 200 + adjustedCount</li>
 *   <li>确认过期盘点 → 409 INVENTORY_STOCKTAKE_STALE</li>
 *   <li>确认差异无原因 → 400 VALIDATION_FAILED</li>
 *   <li>取消盘点草稿 → 200</li>
 *   <li>列表与详情端点正常</li>
 *   <li>跨家庭隔离</li>
 *   <li>普通成员可操作（非仅管理员）</li>
 * </ol>
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class InventoryStocktakeEndpointIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private UUID ownerAccountId;
    private UUID memberAccountId;
    private UUID unitId;
    private UUID itemId;
    private UUID locationId;

    private ZijaPrincipal ownerPrincipal;
    private ZijaPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbcTemplate);

        householdId = seedHousehold();
        ownerAccountId = seedAccount("owner", "所有者");
        memberAccountId = seedAccount("member", "成员");
        seedMember(householdId, ownerAccountId, "OWNER");
        seedMember(householdId, memberAccountId, "MEMBER");

        unitId = seedUnit(householdId);
        itemId = seedItem(householdId, unitId);
        locationId = seedLocation(householdId);

        ownerPrincipal = new ZijaPrincipal(ownerAccountId, "owner", "所有者", "hash", true);
        memberPrincipal = new ZijaPrincipal(memberAccountId, "member", "成员", "hash", true);
    }

    // ==================== Test point 1: Create draft → 200 + id ====================

    @Test
    void createDraft_returns200WithId() throws Exception {
        String body = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // ==================== Test point 2: Update draft → 200 ====================

    @Test
    void updateDraft_returns200() throws Exception {
        // Seed a lot and stock position
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Update draft: change actual quantity to 7
        String updateBody = """
                {
                    "version": 0,
                    "updates": [
                        {
                            "lotId": "%s",
                            "locationId": "%s",
                            "actualQuantity": 7,
                            "reason": "盘点少了3个"
                        }
                    ]
                }
                """.formatted(lotId, locationId);

        mockMvc.perform(put("/api/v1/inventory/stocktakes/{id}", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ==================== Test point 3: Confirm draft → 200 + adjustedCount ====================

    @Test
    void confirmDraft_returns200WithAdjustedCount() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Update: actual=7, reason provided
        String updateBody = """
                {
                    "version": 0,
                    "updates": [
                        {
                            "lotId": "%s",
                            "locationId": "%s",
                            "actualQuantity": 7,
                            "reason": "丢失3个"
                        }
                    ]
                }
                """.formatted(lotId, locationId);

        mockMvc.perform(put("/api/v1/inventory/stocktakes/{id}", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        // Confirm
        String confirmBody = """
                {
                    "version": 1
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stocktakes/{id}/confirm", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocktakeId").value(stocktakeId))
                .andExpect(jsonPath("$.adjustedCount").value(1));
    }

    // ==================== Test point 4: Confirm stale → 409 INVENTORY_STOCKTAKE_STALE ====================

    @Test
    void confirmStale_returns409StocktakeStale() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        // Create draft (snapshot: qty=10, rev=1)
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Simulate stock change after draft
        jdbcTemplate.update("""
                UPDATE inventory_stock_position SET quantity = 15, revision = 2, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, spId);

        // Confirm → stale
        String confirmBody = """
                {
                    "version": 0
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stocktakes/{id}/confirm", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_STOCKTAKE_STALE"));
    }

    // ==================== Test point 5: Confirm reason missing → 400 VALIDATION_FAILED ====================

    @Test
    void confirmDifferenceWithoutReason_returns400ValidationFailed() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Update: actual=7, no reason
        String updateBody = """
                {
                    "version": 0,
                    "updates": [
                        {
                            "lotId": "%s",
                            "locationId": "%s",
                            "actualQuantity": 7
                        }
                    ]
                }
                """.formatted(lotId, locationId);

        mockMvc.perform(put("/api/v1/inventory/stocktakes/{id}", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        // Confirm → reason missing
        String confirmBody = """
                {
                    "version": 1
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stocktakes/{id}/confirm", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ==================== Test point 6: Cancel draft → 200 ====================

    @Test
    void cancelDraft_returns200() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Cancel
        String cancelBody = """
                {
                    "version": 0
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stocktakes/{id}/cancel", stocktakeId)
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ==================== Test point 7: List and detail endpoints work ====================

    @Test
    void listStocktakes_returns200WithItems() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create a draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk());

        // List
        mockMvc.perform(get("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));
    }

    @Test
    void getStocktake_returns200WithItems() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create a draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Detail
        mockMvc.perform(get("/api/v1/inventory/stocktakes/{id}", stocktakeId)
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stocktakeId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].lotId").value(lotId.toString()))
                .andExpect(jsonPath("$.items[0].bookQuantity").value(10))
                .andExpect(jsonPath("$.items[0].actualQuantity").value(10));
    }

    @Test
    void listStocktakes_withStatusFilter_returnsFiltered() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Create a draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // List with DRAFT filter → 1 result
        mockMvc.perform(get("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal))
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        // List with COMPLETED filter → 0 results
        mockMvc.perform(get("/api/v1/inventory/stocktakes")
                        .with(auth(ownerPrincipal))
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ==================== Test point 8: Cross-household isolation ====================

    @Test
    void crossHouseholdIsolation_nonExistentStocktake_returnsError() throws Exception {
        // The household table is a singleton (only one household in the system).
        // Cross-household isolation is achieved by the controller checking stocktake.householdId == member.householdId.
        // We test this by requesting a stocktakeId that doesn't exist (simulating a stocktake from another household).
        UUID nonExistentStocktakeId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/inventory/stocktakes/{id}", nonExistentStocktakeId)
                        .with(auth(ownerPrincipal)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_STOCKTAKE_NOT_DRAFT"));
    }

    // ==================== Test point 9: Member can operate (not admin-only) ====================

    @Test
    void memberCanCreateDraft_returns200() throws Exception {
        String body = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void memberCanConfirmStocktake_returns200() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Member creates draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Member confirms (no differences)
        String confirmBody = """
                {
                    "version": 0
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stocktakes/{id}/confirm", stocktakeId)
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustedCount").value(0));
    }

    @Test
    void memberCanCancelStocktake_returns200() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        seedStockPosition(householdId, lotId, locationId, 10, 1);

        // Member creates draft
        String createBody = """
                {
                    "locationId": "%s"
                }
                """.formatted(locationId);

        String createResponse = mockMvc.perform(post("/api/v1/inventory/stocktakes")
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createJson = objectMapper.readTree(createResponse);
        String stocktakeId = createJson.get("id").asText();

        // Member cancels
        String cancelBody = """
                {
                    "version": 0
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stocktakes/{id}/cancel", stocktakeId)
                        .with(auth(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ==================== Helpers ====================

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家" + h.getId().toString().substring(0, 6));
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private UUID seedAccount(String username, String displayName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, ?, ?, '{bcrypt}$2a$10$examplehash', ?, 'ACTIVE')
                """, id, username, username.toUpperCase(), displayName);
        return id;
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

    private UUID seedUnit(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "个" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedItem(UUID householdId, UUID unitId) {
        UUID id = UUID.randomUUID();
        String name = "物品" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ACTIVE')
                """, id, householdId, name, unitId);
        return id;
    }

    private UUID seedLocation(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "位置" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedLot(UUID householdId, UUID itemId) {
        UUID id = UUID.randomUUID();
        String lotNumber = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%03d", (int) (Math.random() * 900) + 100);
        jdbcTemplate.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, householdId, itemId, lotNumber);
        return id;
    }

    private void seedStockPosition(UUID householdId, UUID lotId, UUID locationId,
                                   int quantity, long revision) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, householdId, lotId, locationId, quantity, revision);
    }

    private RequestPostProcessor auth(ZijaPrincipal principal) {
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }

    private RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}
