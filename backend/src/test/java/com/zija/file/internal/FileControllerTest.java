package com.zija.file.internal;

import com.zija.AbstractWebMvcSliceTest;
import com.zija.ZijaPrincipal;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.HouseholdAuthzTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileController.class)
@Import({HouseholdAuthzTestSupport.class, FileExceptionHandler.class})
class FileControllerTest extends AbstractWebMvcSliceTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean FileApi fileApi;
    @MockitoBean FileStorage fileStorage;
    @MockitoBean FileIntegrityService fileIntegrityService;
    @MockitoBean HouseholdApi householdApi;

    private UUID accountId;
    private UUID householdId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        principal = new ZijaPrincipal(accountId, "owner", "所有者", "{bcrypt}hash", true);

        var member = new HouseholdApi.MemberInfo(
                UUID.randomUUID(), householdId, accountId,
                "owner", "所有者",
                HouseholdApi.MemberRole.OWNER, "ACTIVE");
        when(householdApi.requireActiveMember(accountId)).thenReturn(member);
        when(householdApi.hasAtLeastRole(accountId, HouseholdApi.MemberRole.OWNER))
                .thenReturn(true);
    }

    @Test
    void uploadReturnsAttachmentForAuthenticatedUser() throws Exception {
        UUID fileId = UUID.randomUUID();
        var attachment = new FileApi.AttachmentInfo(
                fileId, householdId, "photo.jpg", "image/jpeg", 1024L,
                "HOUSEHOLD", householdId, OffsetDateTime.now(), null);
        when(fileApi.store(eq(householdId), any(byte[].class), eq("photo.jpg"), eq("image/jpeg"),
                eq("HOUSEHOLD"), eq(householdId)))
                .thenReturn(attachment);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.name").value("photo.jpg"))
                .andExpect(jsonPath("$.mediaType").value("image/jpeg"))
                .andExpect(jsonPath("$.byteSize").value(1024))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.url").value("/api/v1/files/" + fileId + "/content"));
    }

    @Test
    void listPassesFiltersAndRecycledFlag() throws Exception {
        UUID lotId = UUID.randomUUID();
        var attachment = new FileApi.AttachmentInfo(
                UUID.randomUUID(), householdId, "小票.jpg", "image/jpeg", 10L,
                "LOT", lotId, OffsetDateTime.now(), OffsetDateTime.now());
        when(fileApi.list(eq(householdId), eq(1), eq(20), eq("LOT"), eq(lotId), eq("票"), eq(true)))
                .thenReturn(new FileApi.AttachmentPage(
                        java.util.List.of(attachment), 1, 1, 20));

        mockMvc.perform(get("/api/v1/files?mountType=LOT&mountId=" + lotId + "&q=票&recycled=true")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].mountType").value("LOT"))
                .andExpect(jsonPath("$.items[0].deletedAt").exists())
                .andExpect(jsonPath("$.items[0].storageKey").doesNotExist());
    }

    @Test
    void downloadRequiresAuthentication() throws Exception {
        UUID fileId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/files/{fileId}/content", fileId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadReturns404ForMissingFile() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileApi.findInfo(householdId, fileId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/files/{fileId}/content", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRequiresAuthentication() throws Exception {
        UUID fileId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteSendsAttachmentToRecycleBin() throws Exception {
        UUID fileId = UUID.randomUUID();
        var attachment = new FileApi.AttachmentInfo(
                fileId, householdId, "photo.jpg", "image/jpeg", 10L,
                "HOUSEHOLD", householdId, OffsetDateTime.now(), OffsetDateTime.now());
        when(fileApi.recycle(householdId, fileId)).thenReturn(attachment);

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").exists());
    }

    @Test
    void deleteReturns404ForMissingFile() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileApi.recycle(householdId, fileId)).thenReturn(null);

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreReturnsAttachment() throws Exception {
        UUID fileId = UUID.randomUUID();
        var attachment = new FileApi.AttachmentInfo(
                fileId, householdId, "photo.jpg", "image/jpeg", 10L,
                "HOUSEHOLD", householdId, OffsetDateTime.now(), null);
        when(fileApi.restore(householdId, fileId)).thenReturn(attachment);

        mockMvc.perform(post("/api/v1/files/{fileId}/restore", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.deletedAt").doesNotExist());
    }

    @Test
    void remountToHouseholdUpdatesMount() throws Exception {
        UUID fileId = UUID.randomUUID();
        var attachment = new FileApi.AttachmentInfo(
                fileId, householdId, "小票.jpg", "image/jpeg", 10L,
                "HOUSEHOLD", householdId, OffsetDateTime.now(), null);
        when(fileApi.remount(eq(householdId), eq(fileId), eq("HOUSEHOLD"), eq(householdId)))
                .thenReturn(attachment);

        mockMvc.perform(patch("/api/v1/files/{fileId}/mount", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"mountType\":\"HOUSEHOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mountType").value("HOUSEHOLD"));
    }

    @Test
    void remountToItemViaFileEndpointIsRejected() throws Exception {
        UUID fileId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/files/{fileId}/mount", fileId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"mountType\":\"ITEM\"}"))
                .andExpect(status().isBadRequest());
    }
}
