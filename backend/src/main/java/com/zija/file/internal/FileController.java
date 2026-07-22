package com.zija.file.internal;

import com.zija.ZijaPrincipal;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
class FileController {

    private final FileApi fileApi;
    private final FileStorage fileStorage;
    private final HouseholdApi householdApi;

    FileController(FileApi fileApi, FileStorage fileStorage, HouseholdApi householdApi) {
        this.fileApi = fileApi;
        this.fileStorage = fileStorage;
        this.householdApi = householdApi;
    }

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

    @DeleteMapping("/{fileId}")
    void remove(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        fileApi.release(member.householdId(), fileId);
    }
}
