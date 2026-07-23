package com.zija.file.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.file.FileApi;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class FileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean FileApi fileApi;
    @MockitoBean FileStorage fileStorage;
    @MockitoBean HouseholdMapper householdMapper;
    @MockitoBean MemberMapper memberMapper;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;
    @MockitoBean DataSource dataSource;

    private UUID accountId;
    private UUID householdId;
    private UUID memberId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        DatabaseMetaData metaData = org.mockito.Mockito.mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getAutoCommit()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);

        accountId = UUID.randomUUID();
        householdId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        principal = new ZijaPrincipal(accountId, "owner", "所有者", "{bcrypt}hash", true);

        var memberEntity = new MemberEntity();
        memberEntity.setId(memberId);
        memberEntity.setHouseholdId(householdId);
        memberEntity.setAccountId(accountId);
        memberEntity.setRole("OWNER");
        memberEntity.setStatus("ACTIVE");
        when(memberMapper.selectByAccount(accountId)).thenReturn(Optional.of(memberEntity));
    }

    @Test
    void uploadReturnsFileInfoForAuthenticatedUser() throws Exception {
        UUID fileId = UUID.randomUUID();
        var fileInfo = new FileApi.StoredFileInfo(
                fileId, householdId, "2026/07/uuid.jpg", "photo.jpg",
                "image/jpeg", 1024L, "abc123");
        when(fileApi.store(eq(householdId), any(byte[].class), eq("photo.jpg"), eq("image/jpeg")))
                .thenReturn(fileInfo);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/api/v1/files").file(file)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.storageKey").value("2026/07/uuid.jpg"))
                .andExpect(jsonPath("$.originalFilename").value("photo.jpg"))
                .andExpect(jsonPath("$.detectedMediaType").value("image/jpeg"))
                .andExpect(jsonPath("$.byteSize").value(1024))
                .andExpect(jsonPath("$.sha256").value("abc123"))
                .andExpect(jsonPath("$.url").value("/api/v1/files/" + fileId + "/content"));
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
}
