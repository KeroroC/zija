package com.zija.file.internal;

import com.zija.ZijaPrincipal;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireOwner;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 文件管理控制器，提供文件的上传、下载和删除 REST API。
 *
 * <p>文件以家庭为单位隔离存储，通过内容寻址（SHA-256）实现去重。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code POST   /api/v1/files}               — 上传文件</li>
 *   <li>{@code GET    /api/v1/files/{fileId}/content} — 下载/预览文件内容</li>
 *   <li>{@code DELETE /api/v1/files/{fileId}}        — 删除文件</li>
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
     * 上传文件，自动检测媒体类型并通过 SHA-256 去重。
     *
     * @return 文件元信息（id、storageKey、文件名、媒体类型、大小、SHA-256、访问 URL）
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
                file.getContentType()
        );
        return Map.of(
                "id", info.id(),
                "storageKey", info.storageKey(),
                "originalFilename", info.originalFilename(),
                "detectedMediaType", info.detectedMediaType(),
                "byteSize", info.byteSize(),
                "sha256", info.sha256(),
                "url", "/api/v1/files/" + info.id() + "/content"
        );
    }

    /**
     * 下载或预览文件内容，以 inline 方式返回。
     *
     * @param fileId 文件 ID
     * @throws ResponseStatusException 文件不存在时返回 404
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
        response.setHeader("Content-Disposition", "inline; filename=\"" + info.originalFilename() + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(content);
    }

    /**
     * 释放文件引用。当引用计数归零时文件将被实际删除。
     *
     * @param fileId 文件 ID
     */
    @DeleteMapping("/{fileId}")
    void remove(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        fileApi.release(member.householdId(), fileId);
    }

    /**
     * 执行文件完整性校验，返回校验报告。仅家庭所有者可调用。
     *
     * @return 文件完整性报告
     */
    @RequireOwner
    @GetMapping("/integrity-report")
    FileIntegrityReport integrityReport() {
        return fileIntegrityService.check();
    }
}
