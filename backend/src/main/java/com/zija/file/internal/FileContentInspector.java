package com.zija.file.internal;

import com.zija.file.internal.exception.FileMediaTypeUnsupportedException;
import com.zija.file.internal.exception.FileSignatureMismatchException;
import com.zija.file.internal.exception.FileTooLargeException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件内容检查器。
 * <p>
 * 在文件存储前进行安全校验，包括：文件大小限制（最大 5MB）、通过魔数（magic bytes）检测真实媒体类型、
 * 声明类型与实际类型的一致性校验、文件名清理（去除控制字符和路径分隔符）以及 SHA-256 哈希计算。
 * 仅允许 JPEG、PNG、WebP 三种图片格式。
 */
@Component
class FileContentInspector {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern PATH_SEPARATOR = Pattern.compile("[/\\\\]");

    /**
     * 检查文件内容的合法性，包括大小、媒体类型检测、声明一致性校验，并返回检查结果。
     *
     * @throws FileTooLargeException          文件为空或超过 5MB
     * @throws FileMediaTypeUnsupportedException 不支持的媒体类型
     * @throws FileSignatureMismatchException 声明类型与实际检测类型不一致
     */
    InspectionResult inspect(byte[] content, String originalFilename, String declaredMediaType) {
        if (content.length == 0 || content.length > MAX_SIZE) {
            throw new FileTooLargeException(content.length);
        }

        String detected = detectMediaType(content);
        if (!ALLOWED_TYPES.contains(detected)) {
            throw new FileMediaTypeUnsupportedException(detected);
        }

        if (declaredMediaType != null && !declaredMediaType.isBlank()) {
            String normalizedDeclared = declaredMediaType.trim().toLowerCase(Locale.ROOT);
            if (!normalizedDeclared.equals(detected)) {
                throw new FileSignatureMismatchException(normalizedDeclared, detected);
            }
        }

        String sanitized = sanitizeBasename(originalFilename, detected);
        String sha256 = computeSha256(content);

        return new InspectionResult(detected, sanitized, sha256);
    }

    private String detectMediaType(byte[] content) {
        if (content.length < 4) {
            throw new FileMediaTypeUnsupportedException("too short to detect");
        }
        // JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47
        if ((content[0] & 0xFF) == 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return "image/png";
        }
        // WEBP: RIFF....WEBP
        if (content.length >= 12
                && content[0] == 0x52 && content[1] == 0x49 && content[2] == 0x46 && content[3] == 0x46
                && content[8] == 0x57 && content[9] == 0x45 && content[10] == 0x42 && content[11] == 0x50) {
            return "image/webp";
        }
        throw new FileMediaTypeUnsupportedException("unknown");
    }

    private String sanitizeBasename(String originalFilename, String detectedMediaType) {
        if (originalFilename == null || originalFilename.isBlank()) {
            String ext = switch (detectedMediaType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".bin";
            };
            return "file" + ext;
        }
        String basename = originalFilename;
        String[] parts = PATH_SEPARATOR.split(basename);
        if (parts.length > 0) {
            basename = parts[parts.length - 1];
        }
        basename = CONTROL_CHARS.matcher(basename).replaceAll("");
        basename = basename.trim();
        if (basename.isEmpty()) {
            return sanitizeBasename(null, detectedMediaType);
        }
        return basename;
    }

    private String computeSha256(byte[] content) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    record InspectionResult(String detectedMediaType, String sanitizedBasename, String sha256) {
    }
}
