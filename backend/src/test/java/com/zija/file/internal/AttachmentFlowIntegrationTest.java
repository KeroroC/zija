package com.zija.file.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.file.FileApi;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 附件主脉络集成测试（真实文件模块 + 真实文件卷）：
 * 带挂载点上传、列表筛选、改名、重名拒绝、回收站不占名、三入口改挂、封面指定/取消/换封面、
 * 删除进回收站、恢复回原挂载点且非封面、回收站内容可下载、保留期满物理清除、家庭隔离、普通成员可写。
 */
@AutoConfigureMockMvc
class AttachmentFlowIntegrationTest extends AbstractMockMvcIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired FileApi fileApi;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private UUID accountId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbcTemplate);

        householdId = seedHousehold();
        accountId = UUID.randomUUID();
        seedAccount(accountId, "owner", "所有者");
        seedMember(householdId, accountId, "OWNER");
        principal = new ZijaPrincipal(accountId, "owner", "所有者", "hash", true);
    }

    @Test
    void uploadToItemAndLotMountsThenFilterList() throws Exception {
        UUID itemId = seedItem("吸尘器");
        UUID lotId = seedLot(itemId);

        // 挂到物品
        mockMvc.perform(multipart("/api/v1/items/{id}/attachments", itemId)
                        .file(file("说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountType").value("ITEM"))
                .andExpect(jsonPath("$.mountId").value(itemId.toString()))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        // 挂到批次
        mockMvc.perform(multipart("/api/v1/inventory/lots/{id}/attachments", lotId)
                        .file(file("小票.jpg", "image/jpeg", jpegBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountType").value("LOT"))
                .andExpect(jsonPath("$.mountId").value(lotId.toString()));

        // 家庭附件
        mockMvc.perform(multipart("/api/v1/files")
                        .file(file("户口本.jpg", "image/jpeg", jpegBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 全家列表：3 份，按挂载类型筛选
        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
        mockMvc.perform(get("/api/v1/files?mountType=ITEM").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].mountId").value(itemId.toString()));
        mockMvc.perform(get("/api/v1/files?mountType=LOT&mountId=" + lotId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/files?q=户口").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("户口本.jpg"));

        // 物品/批次详情窄接口
        mockMvc.perform(get("/api/v1/items/{id}/attachments", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/inventory/lots/{id}/attachments", lotId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void differentMountsAllowSameNameButSameMountRejects() throws Exception {
        UUID itemId = seedItem("吸尘器");
        UUID item2Id = seedItem("吸尘器2号");
        UUID lotId = seedLot(itemId);

        // 两个不同物品 + 批次 + 家庭各一份同名附件
        mockMvc.perform(multipart("/api/v1/items/{id}/attachments", itemId)
                        .file(file("说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/items/{id}/attachments", item2Id)
                        .file(file("说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/inventory/lots/{id}/attachments", lotId)
                        .file(file("说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 同一物品上第二份同名 → 拒绝
        mockMvc.perform(multipart("/api/v1/items/{id}/attachments", itemId)
                        .file(file("说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NAME_DUPLICATE"));
    }

    @Test
    void recycleBinDoesNotOccupyNameAndRestoreConflictsRequireRename() throws Exception {
        UUID itemId = seedItem("吸尘器");
        String id = uploadItemAttachment(itemId, "说明书.pdf");

        // 删除进回收站
        mockMvc.perform(delete("/api/v1/files/{id}", id).with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").exists());
        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // 回收站不占名：同挂载点可再传同名
        String newId = uploadItemAttachment(itemId, "说明书.pdf");
        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        // 恢复撞名 → 必须改名
        mockMvc.perform(post("/api/v1/files/{id}/restore", id).with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NAME_DUPLICATE"));

        // 改名后可恢复
        mockMvc.perform(patch("/api/v1/files/{id}", id).with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"说明书-旧版.pdf\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/files/{id}/restore", id).with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").doesNotExist());

        // 恢复回到删除前的挂载点（物品上）
        mockMvc.perform(get("/api/v1/items/{id}/attachments", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        // 回收站清空
        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void remountThroughAllThreeEntries() throws Exception {
        UUID itemA = seedItem("吸尘器A");
        UUID itemB = seedItem("吸尘器B");
        UUID lotId = seedLot(itemA);

        String id = uploadItemAttachment(itemA, "小票.jpg");

        // 入口一：物品 → 批次（inventory 入口）
        mockMvc.perform(patch("/api/v1/inventory/lots/{lotId}/attachments/{fileId}/mount", lotId, id)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountType").value("LOT"));
        mockMvc.perform(get("/api/v1/items/{id}/attachments", itemA).with(auth()))
                .andExpect(jsonPath("$.total").value(0));

        // 入口二：批次 → 家庭（file 入口）
        mockMvc.perform(patch("/api/v1/files/{fileId}/mount", id).with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mountType\":\"HOUSEHOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountType").value("HOUSEHOLD"));

        // 入口三：家庭 → 物品（catalog 入口）
        mockMvc.perform(patch("/api/v1/items/{itemId}/attachments/{fileId}/mount", itemB, id)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountId").value(itemB.toString()));
        mockMvc.perform(get("/api/v1/items/{id}/attachments", itemB).with(auth()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void remountConflictingNameIsRejected() throws Exception {
        UUID itemA = seedItem("吸尘器A");
        UUID itemB = seedItem("吸尘器B");
        String a = uploadItemAttachment(itemA, "小票.jpg");
        uploadItemAttachment(itemB, "小票.jpg");

        // B 上已有同名附件 → 把 A 的改挂到 B 被拒
        mockMvc.perform(patch("/api/v1/items/{itemId}/attachments/{fileId}/mount", itemB, a)
                        .with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NAME_DUPLICATE"));

        // 仍在 A 上
        mockMvc.perform(get("/api/v1/files?mountType=ITEM&mountId=" + itemA).with(auth()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void deleteCoverAttachmentRestoresAsOrdinaryAttachmentWithoutCover() throws Exception {
        UUID itemId = seedItem("吸尘器");
        String coverId = uploadCover(itemId);

        // 删除封面附件 → 物品不再有封面
        mockMvc.perform(delete("/api/v1/files/{id}", coverId).with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/items/{id}", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverFileId").value(org.hamcrest.Matchers.nullValue()));

        // 恢复后是普通附件，不恢复封面指定
        mockMvc.perform(post("/api/v1/files/{id}/restore", coverId).with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/items/{id}", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverFileId").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/v1/items/{id}/attachments", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void recycledAttachmentContentIsStillDownloadable() throws Exception {
        String id = uploadHousehold("户口本.jpg", jpegBytes());

        mockMvc.perform(delete("/api/v1/files/{id}", id).with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/{id}/content", id).with(auth()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                org.hamcrest.Matchers.containsString(
                                        "filename*=UTF-8''%E6%88%B7%E5%8F%A3%E6%9C%AC.jpg")));
    }

    @Test
    void purgeExpiredPhysicallyDeletesContentAndRow() throws Exception {
        String id = uploadHousehold("过期.pdf", pdfBytes());

        mockMvc.perform(delete("/api/v1/files/{id}", id).with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 保留期内：直接调用清除方法（截止时刻在未来）→ 不删
        int purged = fileApi.purgeExpired(OffsetDateTime.now().minusDays(1));
        assert purged == 0;

        // 拨过保留期 → 物理删除：元数据与内容都消失
        int purged2 = fileApi.purgeExpired(OffsetDateTime.now().plusDays(31));
        assert purged2 == 1;

        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/files/{id}/content", id).with(auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void purgePermanentlyDeletesRecycledAttachment() throws Exception {
        String id = uploadHousehold("敏感.pdf", pdfBytes());
        String siblingId = uploadHousehold("户口本.jpg", jpegBytes());

        // 删除进回收站（保留期内仍可下载）
        mockMvc.perform(delete("/api/v1/files/{id}", id).with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 永久删除：跳过保留期，立即生效
        mockMvc.perform(delete("/api/v1/files/{id}?permanent=true", id).with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purged").value(true))
                .andExpect(jsonPath("$.id").value(id));

        // 回收站清空，内容不再可下载
        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/files/{id}/content", id).with(auth()))
                .andExpect(status().isNotFound());

        // 同挂载点的其他附件不受影响
        mockMvc.perform(get("/api/v1/files/{id}/content", siblingId).with(auth()))
                .andExpect(status().isOk());

        // 记审计 FILE_PURGED（区别于进回收站的 FILE_DELETED）
        var audits = jdbcTemplate.queryForList("SELECT action FROM audit_log WHERE household_id = ?", householdId);
        assertThat(audits).anyMatch(row -> "FILE_PURGED".equals(row.get("action")));
    }

    @Test
    void purgeRejectsLiveAttachment() throws Exception {
        String id = uploadHousehold("户口本.jpg", jpegBytes());

        mockMvc.perform(delete("/api/v1/files/{id}?permanent=true", id).with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NOT_IN_RECYCLE_BIN"));

        // 附件仍存在、可下载，未被误删
        mockMvc.perform(get("/api/v1/files/{id}/content", id).with(auth()))
                .andExpect(status().isOk());
    }

    @Test
    void purgeReturns404ForAlreadyPurgedOrUnknown() throws Exception {
        String id = uploadHousehold("过期.pdf", pdfBytes());
        mockMvc.perform(delete("/api/v1/files/{id}", id).with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/files/{id}?permanent=true", id).with(auth()).with(csrf()))
                .andExpect(status().isOk());

        // 再次永久删除（行已不在）→ 404
        mockMvc.perform(delete("/api/v1/files/{id}?permanent=true", id).with(auth()).with(csrf()))
                .andExpect(status().isNotFound());

        // 不存在的附件 → 404
        mockMvc.perform(delete("/api/v1/files/{id}?permanent=true", UUID.randomUUID())
                        .with(auth()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ordinaryMemberCanPurgeRecycledAttachment() throws Exception {
        UUID memberAccount = UUID.randomUUID();
        seedAccount(memberAccount, "member", "成员");
        seedMember(householdId, memberAccount, "MEMBER");
        var memberPrincipal = new ZijaPrincipal(memberAccount, "member", "成员", "hash", true);

        String id = uploadHouseholdAs("备注.txt", "text/plain",
                "使用说明\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), memberPrincipal);
        mockMvc.perform(delete("/api/v1/files/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/files/{id}?permanent=true", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purged").value(true));
    }

    @Test
    void deleteWithoutPermanentStillGoesToRecycleBin() throws Exception {
        String id = uploadHousehold("户口本.jpg", jpegBytes());

        mockMvc.perform(delete("/api/v1/files/{id}", id).with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").exists());

        // 进回收站而非物理删除：仍可下载、仍在回收站列表
        mockMvc.perform(get("/api/v1/files/{id}/content", id).with(auth()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files?recycled=true").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void nonMemberCannotListOrDownloadAttachments() throws Exception {
        String id = uploadHousehold("户口本.jpg", jpegBytes());

        // 未加入家庭的账户：不能列出也不能下载
        UUID outsider = UUID.randomUUID();
        seedAccount(outsider, "outsider", "外人");
        var outsiderPrincipal = new ZijaPrincipal(outsider, "outsider", "外人", "hash", true);

        mockMvc.perform(get("/api/v1/files")
                        .with(SecurityMockMvcRequestPostProcessors.user(outsiderPrincipal)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/files/{id}/content", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(outsiderPrincipal)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryMemberCanUploadRenameDeleteAndRestore() throws Exception {
        UUID memberAccount = UUID.randomUUID();
        seedAccount(memberAccount, "member", "成员");
        seedMember(householdId, memberAccount, "MEMBER");
        var memberPrincipal = new ZijaPrincipal(memberAccount, "member", "成员", "hash", true);

        String id = uploadHouseholdAs("备注.txt", "text/plain",
                "使用说明\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), memberPrincipal);

        mockMvc.perform(patch("/api/v1/files/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新备注.txt\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/files/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/files/{id}/restore", id)
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新备注.txt"));
    }

    // ==================== Seed helpers ====================

    private MockMultipartFile file(String filename, String mediaType, byte[] content) {
        return new MockMultipartFile("file", filename, mediaType, content);
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

    private UUID seedItem(String name) throws Exception {
        UUID unitId = seedUnit();
        var body = mockMvc.perform(post("/api/v1/items").with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","managementType":"DURABLE","unitId":"%s",
                                "expiryReminderMode":"INHERIT","lowStockMode":"INHERIT"}
                                """.formatted(name, unitId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID seedLot(UUID itemId) throws Exception {
        UUID locationId = seedLocation();
        var body = mockMvc.perform(post("/api/v1/inventory/lots").with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":"%s","quantity":1,"locationId":"%s"}
                                """.formatted(itemId, locationId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.lotId"));
    }

    private String uploadItemAttachment(UUID itemId, String name) throws Exception {
        var body = mockMvc.perform(multipart("/api/v1/items/{id}/attachments", itemId)
                        .file(file(name, "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String uploadCover(UUID itemId) throws Exception {
        var itemBody = mockMvc.perform(get("/api/v1/items/{id}", itemId).with(auth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(itemBody, "$.version");
        var body = mockMvc.perform(multipart("/api/v1/items/{id}/cover", itemId)
                        .file(file("cover.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(version))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String uploadHousehold(String name, byte[] content) throws Exception {
        String mediaType = name.endsWith(".pdf") ? "application/pdf"
                : name.endsWith(".txt") ? "text/plain"
                : name.endsWith(".md") ? "text/markdown"
                : "image/jpeg";
        return uploadHouseholdAs(name, mediaType, content, principal);
    }

    private String uploadHouseholdAs(String name, String mediaType, byte[] content,
                                     ZijaPrincipal who) throws Exception {
        var body = mockMvc.perform(multipart("/api/v1/files")
                        .file(file(name, mediaType, content))
                        .with(SecurityMockMvcRequestPostProcessors.user(who)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家" + UUID.randomUUID().toString().substring(0, 6));
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private void seedAccount(UUID id, String username, String displayName) {
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, ?, ?, '{bcrypt}$2a$10$examplehash', ?, 'ACTIVE')
                """, id, username, username, displayName);
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
        String name = "个" + unitId.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, unitId, householdId, name, name);
        return unitId;
    }

    private UUID seedLocation() {
        UUID id = UUID.randomUUID();
        String name = "位置" + id.toString().substring(0, 6);
        jdbcTemplate.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
        return id;
    }

    private RequestPostProcessor auth() {
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }

    private RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}
