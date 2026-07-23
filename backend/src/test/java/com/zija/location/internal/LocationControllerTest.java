package com.zija.location.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.location.LocationApi;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean LocationService locationService;
    @MockitoBean HouseholdMapper householdMapper;
    @MockitoBean MemberMapper memberMapper;
    @MockitoBean InvitationMapper invitationMapper;
    @MockitoBean OwnerRecoveryTokenMapper ownerRecoveryTokenMapper;
    @MockitoBean AccountMapper accountMapper;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;
    @MockitoBean DataSource dataSource;

    private UUID accountId;
    private UUID householdId;
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
        principal = new ZijaPrincipal(accountId, "testuser", "测试用户", "hash", true);

        // Configure memberMapper so HouseholdService.requireActiveMember() and hasAtLeastRole() work
        var memberEntity = new MemberEntity();
        memberEntity.setId(UUID.randomUUID());
        memberEntity.setHouseholdId(householdId);
        memberEntity.setAccountId(accountId);
        memberEntity.setRole("MEMBER");
        memberEntity.setStatus("ACTIVE");
        when(memberMapper.selectByAccount(accountId)).thenReturn(Optional.of(memberEntity));
    }

    @Test
    void getTreeReturnsTreeForAuthenticatedUser() throws Exception {
        var tree = new LocationApi.LocationTree(List.of(
                new LocationApi.LocationNode(UUID.randomUUID(), null, "客厅", 0, false, 1, List.of())
        ));
        when(locationService.tree(householdId)).thenReturn(tree);

        mockMvc.perform(get("/api/v1/locations/tree")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roots").isArray())
                .andExpect(jsonPath("$.roots[0].name").value("客厅"));
    }

    @Test
    void getTreeReturns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/locations/tree"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLocationCreatesAndReturnsInfo() throws Exception {
        var locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setParentId(null);
        entity.setName("厨房");
        entity.setSortOrder(0);
        entity.setEverReferenced(false);
        entity.setVersion(1);

        when(locationService.createLocation(eq(householdId), eq("厨房"), isNull(), eq(0)))
                .thenReturn(entity);

        mockMvc.perform(post("/api/v1/locations")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"厨房\",\"parentId\":null,\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("厨房"))
                .andExpect(jsonPath("$.id").value(locationId.toString()))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void renameLocationRenamesLocation() throws Exception {
        var locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setName("新厨房");
        entity.setSortOrder(0);
        entity.setEverReferenced(false);
        entity.setVersion(2);

        when(locationService.renameLocation(eq(householdId), eq(locationId), eq("新厨房"), eq(1)))
                .thenReturn(entity);

        mockMvc.perform(put("/api/v1/locations/{id}", locationId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新厨房\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新厨房"))
                .andExpect(jsonPath("$.id").value(locationId.toString()));
    }

    @Test
    void moveLocationMovesLocation() throws Exception {
        var locationId = UUID.randomUUID();
        var targetParentId = UUID.randomUUID();

        doNothing().when(locationService)
                .moveLocation(eq(householdId), eq(locationId), eq(targetParentId), eq(5), eq(1));

        mockMvc.perform(put("/api/v1/locations/{id}/position", locationId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + targetParentId + "\",\"sortOrder\":5,\"version\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteLocationDeletesLocation() throws Exception {
        var locationId = UUID.randomUUID();

        doNothing().when(locationService).deleteLocation(eq(householdId), eq(locationId), eq(1));

        mockMvc.perform(delete("/api/v1/locations/{id}", locationId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
    }
}
