package com.zija.file.internal;

import com.zija.AbstractMockMvcIntegrationTest;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FileIntegrityReportControllerTest extends AbstractMockMvcIntegrationTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean FileApi fileApi;
    @MockitoBean FileStorage fileStorage;
    @MockitoBean FileIntegrityService fileIntegrityService;
    @MockitoBean HouseholdMapper householdMapper;
    @MockitoBean MemberMapper memberMapper;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private UUID accountId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        principal = new ZijaPrincipal(accountId, "owner", "所有者", "{bcrypt}hash", true);

        var memberEntity = new MemberEntity();
        memberEntity.setId(UUID.randomUUID());
        memberEntity.setHouseholdId(UUID.randomUUID());
        memberEntity.setAccountId(accountId);
        memberEntity.setRole("OWNER");
        memberEntity.setStatus("ACTIVE");
        when(memberMapper.selectByAccount(accountId)).thenReturn(Optional.of(memberEntity));
    }

    @Test
    void ownerCanCallIntegrityReport() throws Exception {
        var report = new FileIntegrityReport(10, 0, 0, 0, 0, List.of(), List.of());
        when(fileIntegrityService.check()).thenReturn(report);

        mockMvc.perform(get("/api/v1/files/integrity-report")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedCount").value(10))
                .andExpect(jsonPath("$.missingCount").value(0))
                .andExpect(jsonPath("$.hashMismatchCount").value(0));
    }

    @Test
    void memberCannotCallIntegrityReport() throws Exception {
        // Override to return MEMBER role
        var memberEntity = new MemberEntity();
        memberEntity.setId(UUID.randomUUID());
        memberEntity.setHouseholdId(UUID.randomUUID());
        memberEntity.setAccountId(accountId);
        memberEntity.setRole("MEMBER");
        memberEntity.setStatus("ACTIVE");
        when(memberMapper.selectByAccount(accountId)).thenReturn(Optional.of(memberEntity));

        mockMvc.perform(get("/api/v1/files/integrity-report")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCallIntegrityReport() throws Exception {
        mockMvc.perform(get("/api/v1/files/integrity-report"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class DataSourceConfig {
        @Bean
        DataSource dataSource() throws Exception {
            DataSource ds = org.mockito.Mockito.mock(DataSource.class);
            Connection conn = org.mockito.Mockito.mock(Connection.class);
            DatabaseMetaData meta = org.mockito.Mockito.mock(DatabaseMetaData.class);
            org.mockito.Mockito.when(conn.getMetaData()).thenReturn(meta);
            org.mockito.Mockito.when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
            org.mockito.Mockito.when(conn.getAutoCommit()).thenReturn(true);
            org.mockito.Mockito.when(ds.getConnection()).thenReturn(conn);
            return ds;
        }
    }
}
