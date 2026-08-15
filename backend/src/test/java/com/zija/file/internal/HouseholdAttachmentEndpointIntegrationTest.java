package com.zija.file.internal;

import com.zija.AbstractMockMvcIntegrationTest;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class HouseholdAttachmentEndpointIntegrationTest extends AbstractMockMvcIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID householdId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbcTemplate);

        householdId = seedHousehold();
        UUID accountId = UUID.randomUUID();
        seedAccount(accountId);
        seedMember(householdId, accountId, "OWNER");
        principal = new ZijaPrincipal(accountId, "owner", "所有者", "hash", true);
    }

    @Test
    void uploadedHouseholdJpegAppearsInListByNameWithoutStorageKey() throws Exception {
        byte[] jpeg = jpegBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "户口本.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items[0].name").value("户口本.jpg"))
                .andExpect(jsonPath("$.items[0].mediaType").value("image/jpeg"))
                .andExpect(jsonPath("$.items[0].byteSize").value(jpeg.length))
                .andExpect(jsonPath("$.items[0].mountType").value("HOUSEHOLD"))
                .andExpect(jsonPath("$.items[0].mountId").value(householdId.toString()))
                .andExpect(jsonPath("$.items[0].url").value(org.hamcrest.Matchers.startsWith("/api/v1/files/")))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].storageKey").doesNotExist());
    }

    @Test
    void uploadedHouseholdPdfAppearsInList() throws Exception {
        byte[] pdf = pdfBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "说明书.pdf", "application/pdf", pdf);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("说明书.pdf"))
                .andExpect(jsonPath("$.items[0].mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.items[0].mountType").value("HOUSEHOLD"));
    }

    @Test
    void renameHouseholdAttachmentChangesDisplayedName() throws Exception {
        String id = uploadHousehold("户口本.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(patch("/api/v1/files/{id}", id)
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"房产证.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("房产证.jpg"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("房产证.jpg"));
    }

    @Test
    void duplicateNameOnSameHouseholdMountIsRejected() throws Exception {
        uploadHousehold("说明书.pdf", "application/pdf", pdfBytes());

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "说明书.pdf", "application/pdf", pdfBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NAME_DUPLICATE"));
    }

    @Test
    void renamingToExistingHouseholdAttachmentNameIsRejected() throws Exception {
        uploadHousehold("户口本.jpg", "image/jpeg", jpegBytes());
        String id = uploadHousehold("房产证.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(patch("/api/v1/files/{id}", id)
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"户口本.jpg\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_NAME_DUPLICATE"));
    }

    @Test
    void uploadedHouseholdMarkdownAppearsInList() throws Exception {
        byte[] markdown = "# 说明书\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "说明书.md", "text/markdown", markdown);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("说明书.md"))
                .andExpect(jsonPath("$.items[0].mediaType").value("text/markdown"));
    }

    @Test
    void uploadedHouseholdTxtAppearsInList() throws Exception {
        byte[] txt = "保修备注\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "备注.txt", "text/plain", txt);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("备注.txt"))
                .andExpect(jsonPath("$.items[0].mediaType").value("text/plain"));
    }

    @Test
    void uploadedHouseholdHeicAppearsInList() throws Exception {
        byte[] heic = heicBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "发票.heic", "image/heic", heic);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("发票.heic"))
                .andExpect(jsonPath("$.items[0].mediaType").value("image/heic"));
    }

    @Test
    void uploadedHouseholdPngAndWebpAppearInList() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "标签.png", "image/png", pngBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "标签.webp", "image/webp", webpBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void gifIsRejectedAsUnsupportedType() throws Exception {
        byte[] gif = new byte[]{'G', 'I', 'F', '8', '9', 'a'};
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "动图.gif", "image/gif", gif))
                        .with(auth()).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("FILE_MEDIA_TYPE_UNSUPPORTED"));
    }

    @Test
    void uploadedHouseholdDocxAppearsInListAndIsNotTreatedAsZip() throws Exception {
        byte[] docx = zipWithEntry("word/document.xml", "<w:document/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "合同.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx);

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("合同.docx"))
                .andExpect(jsonPath("$.items[0].mediaType")
                        .value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void uploadedHouseholdXlsxAppearsInList() throws Exception {
        byte[] xlsx = zipWithEntry("xl/workbook.xml", "<workbook/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile(
                                "file",
                                "清单.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                xlsx))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].mediaType")
                        .value("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void uploadedHouseholdPptxAppearsInList() throws Exception {
        byte[] pptx = zipWithEntry("ppt/presentation.xml", "<p:presentation/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile(
                                "file",
                                "演示.pptx",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                pptx))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].mediaType")
                        .value("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    }

    @Test
    void uploadedHouseholdLegacyOfficeAppearsInList() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "合同.doc", "application/msword", oleBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].mediaType").value("application/msword"));
    }

    @Test
    void uploadedHouseholdLegacyExcelAppearsInList() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile(
                                "file", "清单.xls", "application/vnd.ms-excel", oleBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(jsonPath("$.items[0].mediaType").value("application/vnd.ms-excel"));
    }

    @Test
    void uploadedHouseholdLegacyPowerpointAppearsInList() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile(
                                "file", "演示.ppt", "application/vnd.ms-powerpoint", oleBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(jsonPath("$.items[0].mediaType").value("application/vnd.ms-powerpoint"));
    }

    @Test
    void jpegLargerThanFiveMibIsRejected() throws Exception {
        byte[] jpeg = new byte[5 * 1024 * 1024 + 1];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[3] = (byte) 0xE0;

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "大图.jpg", "image/jpeg", jpeg))
                        .with(auth()).with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));
    }

    @Test
    void pdfLargerThanFiveMibIsAccepted() throws Exception {
        byte[] pdf = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(pdfBytes(), 0, pdf, 0, pdfBytes().length);

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "手册.pdf", "application/pdf", pdf))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void pdfLargerThanTwentyMibIsRejected() throws Exception {
        byte[] pdf = new byte[20 * 1024 * 1024 + 1];
        System.arraycopy(pdfBytes(), 0, pdf, 0, pdfBytes().length);

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "手册.pdf", "application/pdf", pdf))
                        .with(auth()).with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));
    }

    @Test
    void declaredTypeMismatchingContentIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "假的.pdf", "application/pdf", jpegBytes()))
                        .with(auth()).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FILE_SIGNATURE_MISMATCH"));
    }

    @Test
    void zipIsRejectedAsUnsupportedType() throws Exception {
        byte[] zip = new byte[]{'P', 'K', 0x03, 0x04, 0, 0, 0, 0};
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "docs.zip", "application/zip", zip))
                        .with(auth()).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("FILE_MEDIA_TYPE_UNSUPPORTED"));
    }

    @Test
    void ordinaryMemberCanUploadHouseholdAttachment() throws Exception {
        UUID memberAccountId = UUID.randomUUID();
        seedAccount(memberAccountId, "member", "成员");
        seedMember(householdId, memberAccountId, "MEMBER");
        var memberPrincipal = new ZijaPrincipal(memberAccountId, "member", "成员", "hash", true);

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "户口本.jpg", "image/jpeg", jpegBytes()))
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files")
                        .with(SecurityMockMvcRequestPostProcessors.user(memberPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("户口本.jpg"));
    }

    @Test
    void renamedAttachmentIsUsedInDownloadContentDisposition() throws Exception {
        String id = uploadHousehold("户口本.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(patch("/api/v1/files/{id}", id)
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"房产证.jpg\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/{id}/content", id).with(auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("房产证.jpg")));
    }

    @Test
    void itemCoverDoesNotAppearInHouseholdAttachmentListAndCoverSlotStillWorks() throws Exception {
        UUID unitId = seedUnit();
        var created = mockMvc.perform(post("/api/v1/items")
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"冰箱","managementType":"DURABLE","unitId":"%s",
                                "expiryReminderMode":"INHERIT","lowStockMode":"INHERIT"}
                                """.formatted(unitId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String itemId = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        int version = com.jayway.jsonpath.JsonPath.read(created, "$.version");

        mockMvc.perform(multipart("/api/v1/items/{id}/cover", itemId)
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(version))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        var afterCover = mockMvc.perform(get("/api/v1/items/{id}", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverFileId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int coveredVersion = com.jayway.jsonpath.JsonPath.read(afterCover, "$.version");

        mockMvc.perform(multipart("/api/v1/items/{id}/cover", itemId)
                        .file(new MockMultipartFile("file", "cover2.jpg", "image/jpeg", jpegBytes()))
                        .param("version", String.valueOf(coveredVersion))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk());

        var afterReplace = mockMvc.perform(get("/api/v1/items/{id}", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverFileId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int replacedVersion = com.jayway.jsonpath.JsonPath.read(afterReplace, "$.version");

        mockMvc.perform(delete("/api/v1/items/{id}/cover", itemId)
                        .with(auth()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(replacedVersion)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/items/{id}", itemId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverFileId").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/v1/files").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private String uploadHousehold(String filename, String mediaType, byte[] content) throws Exception {
        var body = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", filename, mediaType, content))
                        .with(auth()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
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

    private static byte[] heicBytes() {
        byte[] heic = new byte[16];
        heic[3] = 0x18;
        heic[4] = 'f';
        heic[5] = 't';
        heic[6] = 'y';
        heic[7] = 'p';
        heic[8] = 'h';
        heic[9] = 'e';
        heic[10] = 'i';
        heic[11] = 'c';
        return heic;
    }

    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private static byte[] webpBytes() {
        byte[] webp = new byte[12];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        return webp;
    }

    private static byte[] zipWithEntry(String name, byte[] data) {
        var baos = new java.io.ByteArrayOutputStream();
        try (var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry(name));
            zos.write(data);
            zos.closeEntry();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static byte[] oleBytes() {
        return new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1,
                0, 0, 0, 0, 0, 0, 0, 0
        };
    }

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
        seedAccount(id, "owner", "所有者");
    }

    private void seedAccount(UUID id, String username, String displayName) {
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, ?, ?, '{bcrypt}$2a$10$examplehash', ?, 'ACTIVE')
                """, id, username, username, displayName);
    }

    private UUID seedUnit() {
        var unitId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, unitId, householdId, "个", "个");
        return unitId;
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

    private RequestPostProcessor auth() {
        return SecurityMockMvcRequestPostProcessors.user(principal);
    }

    private RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}
