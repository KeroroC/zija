package com.zija.reporting.internal.search;

import com.zija.reporting.internal.persistence.SearchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    private SearchMapper searchMapper;
    private SearchService searchService;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        searchMapper = mock(SearchMapper.class);
        searchService = new SearchService(searchMapper);
    }

    @Test
    void searchKeywordHitsItemNameReturnsItemsWithMatchedFields() {
        var item = new LinkedHashMap<String, Object>();
        item.put("name", "洗衣液");
        item.put("brand", "蓝月亮");
        item.put("tags", "清洁");
        item.put("category", "日用品");
        when(searchMapper.searchItems(householdId, "洗衣", 5)).thenReturn(List.of(item));
        when(searchMapper.searchLots(eq(householdId), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchLocations(eq(householdId), anyString(), anyInt())).thenReturn(List.of());

        var result = searchService.search(householdId, "洗衣", 5);

        var items = (List<Map<String, Object>>) (List<?>) result.get("items");
        assertThat(items).hasSize(1);
        var matchedFields = (List<String>) (List<?>) items.get(0).get("matchedFields");
        assertThat(matchedFields).contains("name");
    }

    @Test
    void searchKeywordHitsLotNumberReturnsLots() {
        var lot = new LinkedHashMap<String, Object>();
        lot.put("lotNumber", "LOT-2026-001");
        lot.put("serialNumber", null);
        when(searchMapper.searchItems(eq(householdId), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchLots(householdId, "LOT-2026", 5)).thenReturn(List.of(lot));
        when(searchMapper.searchLocations(eq(householdId), anyString(), anyInt())).thenReturn(List.of());

        var result = searchService.search(householdId, "LOT-2026", 5);

        var lots = (List<Map<String, Object>>) (List<?>) result.get("lots");
        assertThat(lots).hasSize(1);
        var matchedFields = (List<String>) (List<?>) lots.get(0).get("matchedFields");
        assertThat(matchedFields).contains("lotNumber");
    }

    @Test
    void searchKeywordHitsLocationPathReturnsLocations() {
        var loc = new LinkedHashMap<String, Object>();
        loc.put("name", "厨房");
        loc.put("path", "厨房/柜子");
        when(searchMapper.searchItems(eq(householdId), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchLots(eq(householdId), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchLocations(householdId, "厨房", 5)).thenReturn(List.of(loc));

        var result = searchService.search(householdId, "厨房", 5);

        var locations = (List<Map<String, Object>>) (List<?>) result.get("locations");
        assertThat(locations).hasSize(1);
        var matchedFields = (List<String>) (List<?>) locations.get(0).get("matchedFields");
        assertThat(matchedFields).contains("name", "path");
    }

    @Test
    void noMatchReturnsEmptyArrays() {
        when(searchMapper.searchItems(eq(householdId), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchLots(eq(householdId), anyString(), anyInt())).thenReturn(List.of());
        when(searchMapper.searchLocations(eq(householdId), anyString(), anyInt())).thenReturn(List.of());

        var result = searchService.search(householdId, "不存在的关键词", 5);

        assertThat((List<?>) result.get("items")).isEmpty();
        assertThat((List<?>) result.get("lots")).isEmpty();
        assertThat((List<?>) result.get("locations")).isEmpty();
    }

    @Test
    void limitPerGroupIsPassedToMapper() {
        when(searchMapper.searchItems(householdId, "test", 3)).thenReturn(List.of());
        when(searchMapper.searchLots(householdId, "test", 3)).thenReturn(List.of());
        when(searchMapper.searchLocations(householdId, "test", 3)).thenReturn(List.of());

        searchService.search(householdId, "test", 3);

        verify(searchMapper).searchItems(householdId, "test", 3);
        verify(searchMapper).searchLots(householdId, "test", 3);
        verify(searchMapper).searchLocations(householdId, "test", 3);
    }
}
