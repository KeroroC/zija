package com.zija.catalog.internal;

import com.zija.AbstractWebMvcSliceTest;
import com.zija.ZijaPrincipal;
import com.zija.catalog.internal.persistence.BrandEntity;
import com.zija.catalog.internal.persistence.CategoryEntity;
import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.HouseholdAuthzTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatalogDictionaryController.class)
@Import({HouseholdAuthzTestSupport.class, CatalogExceptionHandler.class})
class CatalogDictionaryControllerTest extends AbstractWebMvcSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CatalogDictionaryService dictionaryService;
    @MockitoBean HouseholdApi householdApi;

    private static final UUID HOUSEHOLD_ID = UUID.randomUUID();

    private UUID accountId;
    private ZijaPrincipal principal;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        principal = new ZijaPrincipal(accountId, "member", "成员", "hash", true);

        var member = new HouseholdApi.MemberInfo(
                UUID.randomUUID(), HOUSEHOLD_ID, accountId,
                "member", "成员",
                HouseholdApi.MemberRole.ADMIN, "ACTIVE");
        when(householdApi.requireActiveMember(accountId)).thenReturn(member);
        when(householdApi.hasAtLeastRole(eq(accountId), any(HouseholdApi.MemberRole.class)))
                .thenReturn(true);
    }

    // --- GET /api/v1/categories/tree ---

    @Test
    void getCategoryTreeReturnsCategoriesForAuthenticatedUser() throws Exception {
        var category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setHouseholdId(HOUSEHOLD_ID);
        category.setName("家电");
        category.setStatus("ACTIVE");
        category.setSortOrder(0);

        when(dictionaryService.findCategoryTree(HOUSEHOLD_ID, false))
                .thenReturn(List.of(category));

        mockMvc.perform(get("/api/v1/categories/tree")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("家电"));
    }

    @Test
    void getCategoryTreeReturns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/v1/brands (MEMBER allowed) ---

    @Test
    void createBrandCreatesBrandForMemberRole() throws Exception {
        var brand = new BrandEntity();
        brand.setId(UUID.randomUUID());
        brand.setHouseholdId(HOUSEHOLD_ID);
        brand.setName("Sony");
        brand.setStatus("ACTIVE");

        when(dictionaryService.createBrand(HOUSEHOLD_ID, "Sony")).thenReturn(brand);

        mockMvc.perform(post("/api/v1/brands")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sony\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sony"))
                .andExpect(jsonPath("$.id").value(brand.getId().toString()));
    }

    // --- POST /api/v1/categories (ADMIN required) ---

    @Test
    void createCategoryReturns200ForAdminRole() throws Exception {
        var category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setHouseholdId(HOUSEHOLD_ID);
        category.setName("家电");
        category.setStatus("ACTIVE");

        when(dictionaryService.createCategory(eq(HOUSEHOLD_ID), eq("家电"), isNull(), eq(0)))
                .thenReturn(category);

        mockMvc.perform(post("/api/v1/categories")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"家电\",\"parentId\":null,\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("家电"));
    }

    // --- POST /api/v1/units (ADMIN required) ---

    @Test
    void createUnitReturns200ForAdminRole() throws Exception {
        var unit = new UnitEntity();
        unit.setId(UUID.randomUUID());
        unit.setHouseholdId(HOUSEHOLD_ID);
        unit.setName("个");
        unit.setDecimalScale((short) 0);
        unit.setStatus("ACTIVE");

        when(dictionaryService.createUnit(HOUSEHOLD_ID, "个", 0)).thenReturn(unit);

        mockMvc.perform(post("/api/v1/units")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"个\",\"decimalScale\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("个"));
    }
}
