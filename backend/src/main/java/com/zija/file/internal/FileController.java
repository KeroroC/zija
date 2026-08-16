package com.zija.file.internal;

import com.zija.ZijaPrincipal;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireOwner;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 附件控制器，提供附件的上传、列表、改名、改挂、下载、删除与恢复 REST API。
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code GET    /api/v1/files}               — 分页列出附件（可筛挂载点/名字，recycled=true 列回收站）</li>
 *   <li>{@code POST   /api/v1/files}               — 上传家庭附件</li>
 *   <li>{@code PATCH  /api/v1/files/{fileId}}       — 改名</li>
 *   <li>{@code PATCH  /api/v1/files/{fileId}/mount} — 改挂到家庭</li>
 *   <li>{@code GET    /api/v1/files/{fileId}/content} — 下载/预览内容（含回收站内）</li>
 *   <li>{@code DELETE /api/v1/files/{fileId}}        — 删除（进回收站）</li>
 *   <li>{@code POST   /api/v1/files/{fileId}/restore} — 恢复</li>
 *   <li>{@code GET    /api/v1/files/integrity-report} — 文件完整性报告（仅 Owner）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/files")
class FileController {

    private final FileApi fileApi;
    private final FileStorage fileStorage;
    private final HouseholdApi householdApi;
    private final FileIntegrityService fileIntegrityService;

    FileController(FileApi fileApi, FileStorage fileStorage, HouseholdApi householdApi,
                   FileIntegrityService fileIntegrityService) {
        this.fileApi = fileApi;
        this.fileStorage = fileStorage;
        this.householdApi = householdApi;
        this.fileIntegrityService = fileIntegrityService;
    }

    /**
     * 分页列出当前家庭的附件。默认未删除；{@code recycled=true} 列出回收站。
     * 可按挂载类型、挂载 UUID、名字子串筛选。列表不暴露存储键。
     */
    @GetMapping
    Map<String, Object> list(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String mountType,
            @RequestParam(required = false) UUID mountId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean recycled
    ) {
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = fileApi.list(member.householdId(), page, pageSize, mountType, mountId, q, recycled);
        List<Map<String, Object>> items = result.items().stream()
                .map(this::toListItem)
                .toList();
        var response = new LinkedHashMap<String, Object>();
        response.put("items", items);
        response.put("total", result.total());
        response.put("page", result.page());
        response.put("pageSize", result.pageSize());
        return response;
    }

    @PatchMapping("/{fileId}")
    Map<String, Object> rename(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId,
            @Valid @RequestBody RenameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = fileApi.rename(member.householdId(), fileId, request.name());
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toListItem(info);
    }

    /**
     * 改挂到家庭（当前成员的家庭）。改挂到物品 / 批次分别走 catalog / inventory 入口。
     */
    @PatchMapping("/{fileId}/mount")
    Map<String, Object> remountToHousehold(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId,
            @RequestBody(required = false) RemountRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (request != null && request.mountType() != null
                && !FileApi.MOUNT_HOUSEHOLD.equals(request.mountType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "挂到物品/批次请走对应模块入口");
        }
        var info = fileApi.remount(
                member.householdId(), fileId, FileApi.MOUNT_HOUSEHOLD, member.householdId());
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toListItem(info);
    }

    /**
     * 上传家庭附件（挂载点 = 当前家庭）。
     *
     * @return 附件元信息（id、名字、媒体类型、大小、挂载点、访问 URL）
     */
    @PostMapping
    Map<String, Object> upload(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = fileApi.store(
                member.householdId(),
                file.getBytes(),
                file.getOriginalFilename(),
                file.getContentType(),
                FileApi.MOUNT_HOUSEHOLD,
                member.householdId()
        );
        return toListItem(info);
    }

    /**
     * 下载或预览附件内容，以 inline 方式返回。回收站内未物理删除的附件同样可下载。
     */
    @GetMapping("/{fileId}/content")
    void download(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId,
            HttpServletResponse response
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = fileApi.findInfo(member.householdId(), fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] content = fileStorage.read(info.storageKey());
        response.setContentType(info.detectedMediaType());
        response.setContentLengthLong(content.length);
        response.setHeader("Content-Disposition", contentDispositionValue(info.originalFilename()));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(content);
    }

    /**
     * 删除附件。默认进入回收站（保留期内可恢复），不物理删除；
     * {@code permanent=true} 时永久删除：跳过保留期，立即物理清除（卷上对象 + 数据库行），不可恢复。
     */
    @DeleteMapping("/{fileId}")
    Map<String, Object> remove(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId,
            @RequestParam(defaultValue = "false") boolean permanent
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (permanent) {
            boolean purged = fileApi.purge(member.householdId(), fileId);
            if (!purged) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            var response = new LinkedHashMap<String, Object>();
            response.put("id", fileId.toString());
            response.put("purged", true);
            return response;
        }
        var info = fileApi.recycle(member.householdId(), fileId);
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toListItem(info);
    }

    /**
     * 恢复回收站附件：回到删除前的挂载点，作为普通附件。
     */
    @PostMapping("/{fileId}/restore")
    Map<String, Object> restore(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = fileApi.restore(member.householdId(), fileId);
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toListItem(info);
    }

    /**
     * 执行文件完整性校验，返回校验报告。仅家庭所有者可调用。
     */
    @RequireOwner
    @GetMapping("/integrity-report")
    FileIntegrityReport integrityReport() {
        return fileIntegrityService.check();
    }

    private Map<String, Object> toListItem(FileApi.AttachmentInfo info) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", info.id());
        item.put("name", info.name());
        item.put("mediaType", info.mediaType());
        item.put("byteSize", info.byteSize());
        item.put("mountType", info.mountType());
        item.put("mountId", info.mountId());
        item.put("createdAt", info.createdAt());
        if (info.deletedAt() != null) {
            item.put("deletedAt", info.deletedAt());
        }
        item.put("url", "/api/v1/files/" + info.id() + "/content");
        return item;
    }

    record RenameRequest(@NotBlank String name) {
    }

    record RemountRequest(String mountType) {
    }

    /**
     * 构建安全的 {@code Content-Disposition} 头值（RFC 6266 §5 + RFC 5987）。
     *
     * <p>文件名原样放入双引号会破坏 quoted-string 语法：含 {@code "} 的名字可拼接出
     * 伪造的 {@code filename} 参数（头参数注入），非 ASCII 名字还会被以 ISO-8859-1
     * 写到线上变成乱码。因此真实名字走 {@code filename*=UTF-8''<百分号编码>}，
     * 另附一个纯 ASCII 的 {@code filename="..."} 回退值供旧客户端使用；两者都存在时
     * 规范要求客户端优先采用 {@code filename*}。</p>
     */
    private static String contentDispositionValue(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "inline; filename=\"file\"";
        }
        String fallback = headerSafeAscii(originalFilename);
        String encoded = rfc5987Encode(originalFilename);
        if (fallback.equals(originalFilename)) {
            return "inline; filename=\"" + fallback + "\"";
        }
        return "inline; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    /** quoted-string 安全的 ASCII 回退名：双引号、反斜杠、控制符与非 ASCII 一律替换为下划线。 */
    private static String headerSafeAscii(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append((c >= 0x20 && c <= 0x7E && c != '"' && c != '\\') ? c : '_');
        }
        return sb.toString();
    }

    /** RFC 5987 {@code attr-char} 白名单外的字节做 UTF-8 百分号编码。 */
    private static String rfc5987Encode(String value) {
        StringBuilder sb = new StringBuilder(value.length() * 2);
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int v = b & 0xFF;
            if ((v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z') || (v >= '0' && v <= '9')
                    || "!#$&+-.^_`|~".indexOf(v) >= 0) {
                sb.append((char) v);
            } else {
                sb.append('%').append(HEX[(v >> 4) & 0xF]).append(HEX[v & 0xF]);
            }
        }
        return sb.toString();
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
}
