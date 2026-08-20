package com.zija;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约 Guard：前端 HTTP 错误码目录必须与后端 RFC 7807 {@code errorCode} 全集集合相等。
 *
 * <p>权威源是 Java 常量（源文件解析，不用反射——部分常量是包内可见或 private）。
 * 不启 Spring；随 {@code ./mvnw verify} 运行。
 *
 * <p>后端全集：
 * <ul>
 *   <li>各模块 {@code internal/ErrorCodes} 的每个 {@code static final String} 取值；</li>
 *   <li>{@link com.zija.shared.ZijaErrorCodes#VALIDATION_FAILED}（仅此一项；不扫整类）；</li>
 *   <li>安全层 {@link ZijaProblemHandlers} 中的 HTTP 错误码常量。</li>
 * </ul>
 *
 * <p>{@link com.zija.shared.ZijaErrorCodes#UNKNOWN_ERROR}（{@code UnknownError}）是事件死信内部名，
 * 不是 HTTP Problem Details 的 {@code errorCode}，显式排除。不要把它挪出 {@code ZijaErrorCodes}。
 *
 * <p>前端客户端自造名（{@code http_error}、{@code invalid_csrf_response}）不在本契约任一侧。
 */
class HttpErrorCodeCatalogContractTest {

    private static final Pattern JAVA_STRING_CONSTANT = Pattern.compile(
            "(?:(?:public|private|protected)\\s+)?static\\s+final\\s+String\\s+\\w+\\s*=\\s*\"([^\"]*)\"\\s*;");

    private static final Pattern TS_EXPORTED_STRING_CONSTANT = Pattern.compile(
            "export\\s+const\\s+\\w+\\s*=\\s*\"([^\"]*)\"\\s*;");

    /**
     * {@link com.zija.shared.ZijaErrorCodes#UNKNOWN_ERROR} 的取值。
     * 事件死信内部名，从不作为 HTTP {@code errorCode} 返回；不得纳入本契约。
     */
    private static final String EXCLUDED_DEAD_LETTER_NAME = "UnknownError";

    /** {@link com.zija.shared.ZijaErrorCodes} 中唯一属于 HTTP {@code errorCode} 的常量。 */
    private static final String SHARED_HTTP_ERROR_CODE = "VALIDATION_FAILED";

    @Test
    void frontendHttpErrorCodeCatalogEqualsBackendHttpErrorCodeSet() throws IOException {
        Path repoRoot = findRepoRoot();
        Set<String> backend = collectBackendHttpErrorCodes(repoRoot);
        Set<String> frontend = collectFrontendHttpErrorCodes(repoRoot);

        Set<String> missing = new TreeSet<>(backend);
        missing.removeAll(frontend);

        Set<String> extras = new TreeSet<>(frontend);
        extras.removeAll(backend);

        assertThat(new TreeSet<>(frontend))
                .as("前端 errorCodes.ts 与后端 HTTP errorCode 集合必须相等。"
                        + " missing(仅后端有)=%s extras(仅前端有)=%s", missing, extras)
                .isEqualTo(new TreeSet<>(backend));
    }

    private static Set<String> collectBackendHttpErrorCodes(Path repoRoot) throws IOException {
        Path javaRoot = repoRoot.resolve("backend/src/main/java");
        Set<String> codes = new LinkedHashSet<>();

        try (Stream<Path> errorCodesFiles = Files.walk(javaRoot.resolve("com/zija"))
                .filter(p -> p.getFileName().toString().equals("ErrorCodes.java"))) {
            for (Path file : errorCodesFiles.toList()) {
                codes.addAll(parseJavaStringConstants(file));
            }
        }

        // 只纳入 VALIDATION_FAILED；UnknownError 是死信内部名，明确排除，不扫整文件以免误收非 HTTP 常量。
        Set<String> shared = parseJavaStringConstants(javaRoot.resolve("com/zija/shared/ZijaErrorCodes.java"));
        assertThat(shared)
                .as("ZijaErrorCodes 应包含 VALIDATION_FAILED，且 UnknownError 仍保留在该类中（仅排除出 HTTP 集合）")
                .contains(SHARED_HTTP_ERROR_CODE, EXCLUDED_DEAD_LETTER_NAME);
        codes.add(SHARED_HTTP_ERROR_CODE);

        codes.addAll(parseJavaStringConstants(javaRoot.resolve("com/zija/ZijaProblemHandlers.java")));

        assertThat(codes)
                .as("UnknownError 不得进入 HTTP errorCode 全集")
                .doesNotContain(EXCLUDED_DEAD_LETTER_NAME);

        return codes;
    }

    private static Set<String> collectFrontendHttpErrorCodes(Path repoRoot) throws IOException {
        Path catalog = repoRoot.resolve("frontend/src/types/errorCodes.ts");
        assertThat(catalog)
                .as("前端错误码目录必须存在: %s", catalog)
                .exists();
        return parseTsExportedStringConstants(catalog);
    }

    private static Set<String> parseJavaStringConstants(Path file) throws IOException {
        String source = Files.readString(file);
        Matcher matcher = JAVA_STRING_CONSTANT.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> parseTsExportedStringConstants(Path file) throws IOException {
        String source = Files.readString(file);
        Matcher matcher = TS_EXPORTED_STRING_CONSTANT.matcher(source);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Path findRepoRoot() {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("frontend/src/types/errorCodes.ts"))
                    && Files.isDirectory(dir.resolve("backend/src/main/java"))) {
                return dir;
            }
        }
        throw new IllegalStateException(
                "无法定位仓库根目录（需同时包含 frontend/src/types/errorCodes.ts 与 backend/src/main/java）。"
                        + " user.dir=" + start
                        + " candidates=" + Stream.iterate(start, p -> p != null, Path::getParent)
                        .map(Path::toString)
                        .collect(Collectors.joining(" -> ")));
    }
}
