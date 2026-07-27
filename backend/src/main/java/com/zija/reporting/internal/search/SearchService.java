package com.zija.reporting.internal.search;

import com.zija.reporting.internal.persistence.SearchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SearchService {

    private final SearchMapper searchMapper;

    public SearchService(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(UUID householdId, String q, int limitPerGroup) {
        var items = searchMapper.searchItems(householdId, q, limitPerGroup);
        var lots = searchMapper.searchLots(householdId, q, limitPerGroup);
        var locations = searchMapper.searchLocations(householdId, q, limitPerGroup);

        addMatchedFields(items, q, "name", "brand", "tags", "category");
        addMatchedFields(lots, q, "lotNumber", "serialNumber");
        addMatchedFields(locations, q, "name", "path");

        return Map.of("items", items, "lots", lots, "locations", locations);
    }

    private void addMatchedFields(List<Map<String, Object>> results, String q, String... fields) {
        for (var row : results) {
            var matched = new ArrayList<String>();
            for (String field : fields) {
                Object val = row.get(field);
                if (val != null && val.toString().toLowerCase().contains(q.toLowerCase())) {
                    matched.add(field);
                }
            }
            row.put("matchedFields", matched);
        }
    }
}
