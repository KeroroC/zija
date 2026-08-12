package com.zija.reporting.internal.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * CSV 写出工具。UTF-8 BOM + RFC 4180 转义。
 */
public class CsvWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * 写出 CSV 到输出流。先写 BOM，再写表头行，最后逐行写数据。
     */
    public static void write(OutputStream out, List<String> headers,
                              List<Map<String, Object>> rows) throws IOException {
        out.write(BOM);
        var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        // 表头
        writer.write(String.join(",", headers.stream()
                .map(CsvWriter::escapeField).toList()));
        writer.write("\r\n");

        // 数据行
        for (var row : rows) {
            var line = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) line.append(",");
                Object val = row.get(headers.get(i));
                line.append(escapeField(val == null ? "" : val.toString()));
            }
            writer.write(line.toString());
            writer.write("\r\n");
        }
        writer.flush();
    }

    /**
     * RFC 4180 转义：含逗号/双引号/换行的字段用双引号包裹，内部双引号转义为两个双引号。
     * <p>
     * 同时防御 CSV 公式注入（XSS 变体）：以 {@code =}, {@code +}, {@code -}, {@code @} 或
     * 制表符/回车开头的字段会被 Excel/LibreOffice 当作公式执行，统一以单引号前缀中和。
     */
    static String escapeField(String field) {
        if (field == null) return "";
        if (needsFormulaNeutralization(field)) {
            field = "'" + field;
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")
                || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static boolean needsFormulaNeutralization(String field) {
        if (field.isEmpty()) return false;
        char c = field.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }
}
