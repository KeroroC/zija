package com.zija.catalog.internal;

import com.zija.TestDb;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class ItemEndpointIntegrationTest {

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
    @Autowired ItemService itemService;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private UUID accountId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbcTemplate);

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

    // --- Cover endpoint tests ---

    @Test
    void uploadCoverSetsCoverFileIdAndBumpsVersion() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var version = itemService.findItem(householdId, item.getId()).getVersion();

        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(file)
                        .param("version", String.valueOf(version))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("cover.jpg"))
                .andExpect(jsonPath("$.mediaType").value("image/jpeg"))
                .andExpect(jsonPath("$.byteSize").value(jpegBytes().length))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/v1/files/")))
                .andExpect(jsonPath("$.version").value(version + 1));

        var updated = itemService.findItem(householdId, item.getId());
        assert updated.getCoverFileId() != null;
        assert updated.getVersion() == version + 1;

        // 封面附件挂在物品上，不出现在家庭挂载的附件列表
        mockMvc.perform(get("/api/v1/files?mountType=HOUSEHOLD").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void uploadCoverWithStaleVersionReturns409AndRollsBackAttachment() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var version = itemService.findItem(householdId, item.getId()).getVersion();

        // 先传一个封面
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(version))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        String oldCoverId = itemService.findItem(householdId, item.getId()).getCoverFileId().toString();

        // 用过期版本上传第二个封面 → 409，且新附件随事务回滚（不留孤儿）
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover2.jpg", "image/jpeg", jpegBytes()))
                        .param("version", "999")
                        .with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_VERSION_CONFLICT"));

        var afterConflict = itemService.findItem(householdId, item.getId());
        assert afterConflict.getCoverFileId().toString().equals(oldCoverId);

        mockMvc.perform(get("/api/v1/items/{id}/attachments", item.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void uploadCoverRejectsNonExistentItem() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/v1/items/{id}/cover", UUID.randomUUID())
                        .file(file)
                        .param("version", "0")
                        .with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_ARCHIVED_DICTIONARY"));
    }

    @Test
    void removeCoverClearsCoverFileIdAndKeepsAttachmentOnItem() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 只取消指定：附件仍留在物品上，不送回收站
        var afterUpload = itemService.findItem(householdId, item.getId());
        String coverId = afterUpload.getCoverFileId().toString();
        mockMvc.perform(delete("/api/v1/items/{id}/cover", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + afterUpload.getVersion() + "}"))
                .andExpect(status().isOk());

        var afterRemove = itemService.findItem(householdId, item.getId());
        assert afterRemove.getCoverFileId() == null;
        assert afterRemove.getVersion() == afterUpload.getVersion() + 1;

        mockMvc.perform(get("/api/v1/items/{id}/attachments", item.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(coverId));
    }

    @Test
    void removeCoverWithStaleVersionReturns409() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/items/{id}/cover", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_VERSION_CONFLICT"));
    }

    @Test
    void removeCoverRejectsItemWithoutCover() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var version = itemService.findItem(householdId, item.getId()).getVersion();

        mockMvc.perform(delete("/api/v1/items/{id}/cover", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_ARCHIVED_DICTIONARY"));
    }

    @Test
    void replaceCoverDefaultKeepsOldCoverAsOrdinaryAttachment() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterFirst = itemService.findItem(householdId, item.getId());
        String oldCoverId = afterFirst.getCoverFileId().toString();

        // 未带 oldCoverAction → 旧封面留作普通附件
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover2.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(afterFirst.getVersion()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("cover2.jpg"));

        var afterReplace = itemService.findItem(householdId, item.getId());
        assert !afterReplace.getCoverFileId().toString().equals(oldCoverId);

        // 两份附件都在物品上（未删除）
        mockMvc.perform(get("/api/v1/items/{id}/attachments", item.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void replaceCoverWithRecycleMovesOldCoverToRecycleBin() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterFirst = itemService.findItem(householdId, item.getId());
        String oldCoverId = afterFirst.getCoverFileId().toString();

        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover2.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(afterFirst.getVersion()))
                        .param("oldCoverAction", "RECYCLE")
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 旧封面进了回收站：物品附件列表只剩新封面
        mockMvc.perform(get("/api/v1/items/{id}/attachments", item.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("cover2.jpg"));

        // 回收站里能找到旧封面
        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldCoverId));
    }

    @Test
    void replaceCoverWithRecycleAllowsSameFilename() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterFirst = itemService.findItem(householdId, item.getId());
        String oldCoverId = afterFirst.getCoverFileId().toString();

        // 同文件名 + RECYCLE：旧封面即将释放名字，替换应当成功而不是撞名 409
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(afterFirst.getVersion()))
                        .param("oldCoverAction", "RECYCLE")
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("cover1.jpg"))
                .andExpect(jsonPath("$.version").value(afterFirst.getVersion() + 1));

        var afterReplace = itemService.findItem(householdId, item.getId());
        assert !afterReplace.getCoverFileId().toString().equals(oldCoverId);

        // 物品附件列表只剩新封面
        mockMvc.perform(get("/api/v1/items/{id}/attachments", item.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("cover1.jpg"));

        // 回收站里能找到旧封面
        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldCoverId));
    }

    @Test
    void replaceCoverKeepingOldRejectsSameFilename() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterFirst = itemService.findItem(householdId, item.getId());
        String oldCoverId = afterFirst.getCoverFileId().toString();

        // 缺省 oldCoverAction（=KEEP）：旧封面名字仍被占用，同文件名替换必须 409
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover1.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(afterFirst.getVersion()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NAME_DUPLICATE"));

        // 旧封面仍是封面，物品上仍只有一份附件
        var afterFail = itemService.findItem(householdId, item.getId());
        assert afterFail.getCoverFileId().toString().equals(oldCoverId);
        mockMvc.perform(get("/api/v1/items/{id}/attachments", item.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void designateExistingEligibleAttachmentAsCover() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var version = itemService.findItem(householdId, item.getId()).getVersion();

        // 先作为普通附件上传
        var uploadBody = mockMvc.perform(multipart("/api/v1/items/{id}/attachments", item.getId())
                        .file(new MockMultipartFile("file", "铭牌.jpg", "image/jpeg", jpegBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath.read(uploadBody, "$.id");

        // 指定为封面
        mockMvc.perform(put("/api/v1/items/{id}/cover", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + fileId + "\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.version").value(version + 1));

        var updated = itemService.findItem(householdId, item.getId());
        assert updated.getCoverFileId().toString().equals(fileId);
    }

    @Test
    void designateCoverRejectsPdfAttachment() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var version = itemService.findItem(householdId, item.getId()).getVersion();

        var uploadBody = mockMvc.perform(multipart("/api/v1/items/{id}/attachments", item.getId())
                        .file(new MockMultipartFile("file", "说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath.read(uploadBody, "$.id");

        mockMvc.perform(put("/api/v1/items/{id}/cover", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + fileId + "\",\"version\":" + version + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_COVER_NOT_ELIGIBLE"));
    }

    @Test
    void designateCoverRejectsAttachmentMountedOnAnotherItem() throws Exception {
        var unitId = seedUnit();
        var itemA = itemService.createItem(householdId, "冰箱A", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var itemB = itemService.createItem(householdId, "冰箱B", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var versionA = itemService.findItem(householdId, itemA.getId()).getVersion();

        var uploadBody = mockMvc.perform(multipart("/api/v1/items/{id}/attachments", itemA.getId())
                        .file(new MockMultipartFile("file", "铭牌.jpg", "image/jpeg", jpegBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath.read(uploadBody, "$.id");

        // 挂载在 A 上的附件不能指定为 B 的封面
        mockMvc.perform(put("/api/v1/items/{id}/cover", itemB.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + fileId + "\",\"version\":" + versionA + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_COVER_NOT_ELIGIBLE"));
    }

    @Test
    void deletingCoverAttachmentClearsItemCover() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "冰箱", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, item.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", item.getId())
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterUpload = itemService.findItem(householdId, item.getId());
        String coverId = afterUpload.getCoverFileId().toString();

        // 从附件总列表删除封面附件 → 进回收站，且物品不再有封面
        mockMvc.perform(delete("/api/v1/files/{id}", coverId).with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterDelete = itemService.findItem(householdId, item.getId());
        assert afterDelete.getCoverFileId() == null;
    }

    @Test
    void remountingCoverAttachmentToAnotherItemClearsCover() throws Exception {
        var unitId = seedUnit();
        var itemA = itemService.createItem(householdId, "冰箱A", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        var itemB = itemService.createItem(householdId, "冰箱B", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);

        var v = itemService.findItem(householdId, itemA.getId()).getVersion();
        mockMvc.perform(multipart("/api/v1/items/{id}/cover", itemA.getId())
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(v))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterUpload = itemService.findItem(householdId, itemA.getId());
        String coverId = afterUpload.getCoverFileId().toString();

        // 改挂封面附件到另一物品 → 原物品封面被清除
        mockMvc.perform(patch("/api/v1/items/{id}/attachments/{fileId}/mount", itemB.getId(), coverId)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountId").value(itemB.getId().toString()));

        var afterRemount = itemService.findItem(householdId, itemA.getId());
        assert afterRemount.getCoverFileId() == null;

        // 附件出现在 B 的附件列表
        mockMvc.perform(get("/api/v1/items/{id}/attachments", itemB.getId()).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void archivedItemStillAllowsAttachmentUploadAndCoverDesignation() throws Exception {
        var unitId = seedUnit();
        var item = itemService.createItem(householdId, "旧手机", "DURABLE", null, null, unitId, null,
                "INHERIT", null, "INHERIT", null, null);
        itemService.archiveItem(householdId, item.getId(), accountId, item.getVersion());

        var body = mockMvc.perform(multipart("/api/v1/items/{id}/attachments", item.getId())
                        .file(new MockMultipartFile("file", "发票.jpg", "image/jpeg", jpegBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        var archived = itemService.findItem(householdId, item.getId());
        mockMvc.perform(put("/api/v1/items/{id}/cover", item.getId())
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + fileId + "\",\"version\":" + archived.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(archived.getVersion() + 1));
    }

    private static byte[] jpegBytes() {
        byte[] jpeg = new byte[16];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[3] = (byte) 0xE0;
        return jpeg;
    }

    private static byte[] pdfBytes() {
        return new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', '%', 0, 0, 0, 0};
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