package com.zija.file.internal;

import com.zija.file.exception.FileMediaTypeUnsupportedException;
import com.zija.file.exception.FileSignatureMismatchException;
import com.zija.file.exception.FileTooLargeException;
import com.zija.shared.ZijaDigests;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件内容检查器。
 * <p>
 * 在文件存储前进行安全校验，包括：文件大小限制（图片 5MiB、文档 20MiB）、通过魔数检测真实媒体类型、
 * 声明类型与实际类型的一致性校验、文件名清理以及 SHA-256 哈希计算。
 * 仅允许 JPEG、PNG、WebP、HEIC、PDF、Markdown、TXT 与 Office 等封闭类型（见 ALLOWED_TYPES）。
 */
@Component
class FileContentInspector {

    private static final String MEDIA_TYPE_JPEG = "image/jpeg";
    private static final String MEDIA_TYPE_PNG = "image/png";
    private static final String MEDIA_TYPE_WEBP = "image/webp";
    private static final String MEDIA_TYPE_PDF = "application/pdf";
    private static final String MEDIA_TYPE_MARKDOWN = "text/markdown";
    private static final String MEDIA_TYPE_PLAIN = "text/plain";
    private static final String MEDIA_TYPE_HEIC = "image/heic";
    private static final String MEDIA_TYPE_HEIF = "image/heif";
    private static final String MEDIA_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MEDIA_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MEDIA_TYPE_PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String MEDIA_TYPE_DOC = "application/msword";
    private static final String MEDIA_TYPE_XLS = "application/vnd.ms-excel";
    private static final String MEDIA_TYPE_PPT = "application/vnd.ms-powerpoint";
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_SIGNATURE = {0x57, 0x45, 0x42, 0x50};
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F'};
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 0x03, 0x04};
    private static final byte[] OLE_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] FTYP_SIGNATURE = {'f', 't', 'y', 'p'};
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix");
    private static final Set<String> HEIF_BRANDS = Set.of("heif", "heis", "mif1", "msf1");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_DOCUMENT_SIZE = 20 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of(
            MEDIA_TYPE_JPEG, MEDIA_TYPE_PNG, MEDIA_TYPE_WEBP, MEDIA_TYPE_HEIC, MEDIA_TYPE_HEIF
    );
    private static final Set<String> ALLOWED_TYPES = Set.of(
            MEDIA_TYPE_JPEG, MEDIA_TYPE_PNG, MEDIA_TYPE_WEBP, MEDIA_TYPE_PDF,
            MEDIA_TYPE_MARKDOWN, MEDIA_TYPE_PLAIN, MEDIA_TYPE_HEIC, MEDIA_TYPE_HEIF,
            MEDIA_TYPE_DOCX, MEDIA_TYPE_XLSX, MEDIA_TYPE_PPTX,
            MEDIA_TYPE_DOC, MEDIA_TYPE_XLS, MEDIA_TYPE_PPT
    );
    private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[\\p{Cntrl}\"]");
    private static final Pattern PATH_SEPARATOR = Pattern.compile("[/\\\\]");

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (content[offset + i] != signature[i]) return false;
        }
        return true;
    }

    /**
     * 检查文件内容的合法性，包括大小、媒体类型检测、声明一致性校验，并返回检查结果。
     *
     * @throws FileTooLargeException          文件为空、图片超过 5MiB 或文档超过 20MiB
     * @throws FileMediaTypeUnsupportedException 不支持的媒体类型
     * @throws FileSignatureMismatchException 声明类型与实际检测类型不一致
     */
    InspectionResult inspect(byte[] content, String originalFilename, String declaredMediaType) {
        if (content.length == 0 || content.length > MAX_DOCUMENT_SIZE) {
            throw new FileTooLargeException(content.length);
        }

        String detected = detectMediaType(content, originalFilename);
        if (!ALLOWED_TYPES.contains(detected)) {
            throw new FileMediaTypeUnsupportedException(detected);
        }

        long max = IMAGE_TYPES.contains(detected) ? MAX_IMAGE_SIZE : MAX_DOCUMENT_SIZE;
        if (content.length > max) {
            throw new FileTooLargeException(content.length);
        }

        if (declaredMediaType != null && !declaredMediaType.isBlank()) {
            String normalizedDeclared = normalizeDeclared(declaredMediaType);
            if (!normalizedDeclared.isEmpty()
                    && !normalizedDeclared.equals("application/octet-stream")
                    && !declaredMatches(normalizedDeclared, detected)) {
                throw new FileSignatureMismatchException(normalizedDeclared, detected);
            }
        }

        String sanitized = sanitizeBasename(originalFilename, detected);
        String sha256 = computeSha256(content);

        return new InspectionResult(detected, sanitized, sha256);
    }

    private static String normalizeDeclared(String declaredMediaType) {
        String normalized = declaredMediaType.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return normalized;
    }

    private static boolean declaredMatches(String declared, String detected) {
        if (declared.equals(detected)) {
            return true;
        }
        if (IMAGE_TYPES.contains(declared) && IMAGE_TYPES.contains(detected)
                && isHeifFamily(declared) && isHeifFamily(detected)) {
            return true;
        }
        return MEDIA_TYPE_PLAIN.equals(declared) && MEDIA_TYPE_MARKDOWN.equals(detected);
    }

    private static boolean isHeifFamily(String mediaType) {
        return MEDIA_TYPE_HEIC.equals(mediaType) || MEDIA_TYPE_HEIF.equals(mediaType);
    }

    private String detectMediaType(byte[] content, String originalFilename) {
        if (content.length >= 3 && startsWith(content, 0, JPEG_SIGNATURE)) return MEDIA_TYPE_JPEG;
        if (content.length >= 4 && startsWith(content, 0, PNG_SIGNATURE)) return MEDIA_TYPE_PNG;
        if (content.length >= 12 && startsWith(content, 0, RIFF_SIGNATURE)
                && startsWith(content, 8, WEBP_SIGNATURE)) {
            return MEDIA_TYPE_WEBP;
        }
        if (content.length >= 4 && startsWith(content, 0, PDF_SIGNATURE)) return MEDIA_TYPE_PDF;
        String heif = detectHeif(content);
        if (heif != null) return heif;
        String zipFamily = detectZipFamily(content);
        if (zipFamily != null) return zipFamily;
        String ole = detectOleOffice(content, originalFilename);
        if (ole != null) return ole;
        String textType = detectTextByExtension(content, originalFilename);
        if (textType != null) return textType;
        throw new FileMediaTypeUnsupportedException("unknown");
    }

    private String detectHeif(byte[] content) {
        if (content.length < 12 || !startsWith(content, 4, FTYP_SIGNATURE)) {
            return null;
        }
        String brand = new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                .toLowerCase(Locale.ROOT);
        if (HEIC_BRANDS.contains(brand)) return MEDIA_TYPE_HEIC;
        if (HEIF_BRANDS.contains(brand)) return MEDIA_TYPE_HEIF;
        return null;
    }

    private String detectZipFamily(byte[] content) {
        if (!startsWith(content, 0, ZIP_SIGNATURE)) {
            return null;
        }
        try (var zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/")) {
                    return MEDIA_TYPE_DOCX;
                }
                if (name.startsWith("xl/")) {
                    return MEDIA_TYPE_XLSX;
                }
                if (name.startsWith("ppt/")) {
                    return MEDIA_TYPE_PPTX;
                }
            }
            return "application/zip";
        } catch (IOException e) {
            return "application/zip";
        }
    }

    private String detectOleOffice(byte[] content, String originalFilename) {
        if (!startsWith(content, 0, OLE_SIGNATURE)) {
            return null;
        }
        return switch (extensionOf(originalFilename)) {
            case ".doc" -> MEDIA_TYPE_DOC;
            case ".xls" -> MEDIA_TYPE_XLS;
            case ".ppt" -> MEDIA_TYPE_PPT;
            default -> "application/x-ole-storage";
        };
    }

    private String detectTextByExtension(byte[] content, String originalFilename) {
        if (containsNul(content)) {
            return null;
        }
        String ext = extensionOf(originalFilename);
        if (".md".equals(ext) || ".markdown".equals(ext)) {
            return MEDIA_TYPE_MARKDOWN;
        }
        if (".txt".equals(ext)) {
            return MEDIA_TYPE_PLAIN;
        }
        return null;
    }

    private static boolean containsNul(byte[] content) {
        int limit = Math.min(content.length, 512);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) return true;
        }
        return false;
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) return "";
        String basename = originalFilename;
        int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (slash >= 0) basename = basename.substring(slash + 1);
        int dot = basename.lastIndexOf('.');
        if (dot < 0) return "";
        return basename.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * 清洗展示名：只保留路径最后一段，去除控制符与双引号，并去首尾空白。
     * 上传与改名共用，保证存储名不会破坏 Content-Disposition 等下游输出
     * （双引号会破坏 quoted-string 语法并允许拼接伪造的头参数）。
     */
    static String sanitizeName(String name) {
        String basename = name;
        String[] parts = PATH_SEPARATOR.split(basename);
        if (parts.length > 0) {
            basename = parts[parts.length - 1];
        }
        basename = UNSAFE_NAME_CHARS.matcher(basename).replaceAll("");
        return basename.trim();
    }

    private String sanitizeBasename(String originalFilename, String detectedMediaType) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file" + extensionFor(detectedMediaType);
        }
        String basename = sanitizeName(originalFilename);
        if (basename.isEmpty()) {
            return "file" + extensionFor(detectedMediaType);
        }
        return basename;
    }

    private static String extensionFor(String detectedMediaType) {
        return switch (detectedMediaType) {
            case MEDIA_TYPE_JPEG -> ".jpg";
            case MEDIA_TYPE_PNG -> ".png";
            case MEDIA_TYPE_WEBP -> ".webp";
            case MEDIA_TYPE_PDF -> ".pdf";
            case MEDIA_TYPE_MARKDOWN -> ".md";
            case MEDIA_TYPE_PLAIN -> ".txt";
            case MEDIA_TYPE_HEIC -> ".heic";
            case MEDIA_TYPE_HEIF -> ".heif";
            case MEDIA_TYPE_DOCX -> ".docx";
            case MEDIA_TYPE_XLSX -> ".xlsx";
            case MEDIA_TYPE_PPTX -> ".pptx";
            case MEDIA_TYPE_DOC -> ".doc";
            case MEDIA_TYPE_XLS -> ".xls";
            case MEDIA_TYPE_PPT -> ".ppt";
            default -> ".bin";
        };
    }

    private String computeSha256(byte[] content) {
        try {
            var md = java.security.MessageDigest.getInstance(ZijaDigests.SHA_256);
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
