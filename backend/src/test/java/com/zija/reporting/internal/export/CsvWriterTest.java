package com.zija.reporting.internal.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void outputStartsWithUtf8Bom() throws IOException {
        var out = new ByteArrayOutputStream();
        CsvWriter.write(out, List.of("col1"), List.of());
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo(BOM[0]);
        assertThat(bytes[1]).isEqualTo(BOM[1]);
        assertThat(bytes[2]).isEqualTo(BOM[2]);
    }

    @Test
    void fieldContainingCommaIsQuoted() throws IOException {
        var out = new ByteArrayOutputStream();
        var row = new LinkedHashMap<String, Object>();
        row.put("name", "a,b");
        CsvWriter.write(out, List.of("name"), List.of(row));
        String csv = out.toString(StandardCharsets.UTF_8);
        // BOM + header + CRLF + data + CRLF
        assertThat(csv).contains("\"a,b\"");
    }

    @Test
    void fieldContainingDoubleQuoteIsEscaped() throws IOException {
        var out = new ByteArrayOutputStream();
        var row = new LinkedHashMap<String, Object>();
        row.put("name", "say \"hello\"");
        CsvWriter.write(out, List.of("name"), List.of(row));
        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"say \"\"hello\"\"\"");
    }

    @Test
    void nullValueOutputsEmptyString() throws IOException {
        var out = new ByteArrayOutputStream();
        var row = new LinkedHashMap<String, Object>();
        row.put("name", null);
        row.put("value", "ok");
        CsvWriter.write(out, List.of("name", "value"), List.of(row));
        String csv = out.toString(StandardCharsets.UTF_8);
        // null should be rendered as empty between the commas
        assertThat(csv).contains(",ok");
    }

    @Test
    void escapeFieldReturnsEmptyForNull() {
        assertThat(CsvWriter.escapeField(null)).isEmpty();
    }

    @Test
    void oneHundredThousandRowsDoesNotThrow() throws IOException {
        var out = new ByteArrayOutputStream();
        var headers = List.of("id", "name");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", String.valueOf(i));
            row.put("name", "item-" + i);
            rows.add(row);
        }
        CsvWriter.write(out, headers, rows);
        // Verify it produced output (not empty)
        assertThat(out.size()).isGreaterThan(0);
    }

    @Test
    void headerRowIsWrittenCorrectly() throws IOException {
        var out = new ByteArrayOutputStream();
        CsvWriter.write(out, List.of("col_a", "col_b"), List.of());
        String csv = out.toString(StandardCharsets.UTF_8);
        // Verify BOM is present and header follows
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("col_a,col_b\r\n");
    }

    @Test
    void multipleDataRowsAreWritten() throws IOException {
        var out = new ByteArrayOutputStream();
        var row1 = new LinkedHashMap<String, Object>();
        row1.put("id", "1");
        var row2 = new LinkedHashMap<String, Object>();
        row2.put("id", "2");
        CsvWriter.write(out, List.of("id"), List.of(row1, row2));
        String csv = out.toString(StandardCharsets.UTF_8);
        // Verify all parts are present
        assertThat(csv).contains("id\r\n");
        assertThat(csv).contains("\r\n1\r\n");
        assertThat(csv).contains("\r\n2\r\n");
    }
}
