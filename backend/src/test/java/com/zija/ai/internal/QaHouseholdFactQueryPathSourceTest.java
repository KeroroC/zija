package com.zija.ai.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 问答路径不得再「列出全部活跃物品再逐件打库存」。
 */
class QaHouseholdFactQueryPathSourceTest {

    @Test
    void householdFactQueriesAndScopePlannerDoNotScanAllActiveItems() throws IOException {
        String queries = Files.readString(source("HouseholdFactQueries.java"));
        String planner = Files.readString(source("QaScopePlanner.java"));

        assertThat(queries).doesNotContain("listActiveItems");
        assertThat(planner).doesNotContain("listActiveItems");
        assertThat(planner).doesNotContain("lotsOfItem");
        assertThat(queries).doesNotContain("reporting.internal");
        assertThat(queries).doesNotContain("reporting_stock_flat");
        assertThat(planner).doesNotContain("reporting.internal");
        assertThat(queries).doesNotContain("dumpItems");
        assertThat(planner).doesNotContain("dumpItems");
    }

    private static Path source(String fileName) {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("backend/src/main/java/com/zija/ai/internal/" + fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 " + fileName);
    }
}
