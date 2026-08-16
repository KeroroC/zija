package com.zija.ai.internal;

import com.zija.ZijaPrincipal;
import com.zija.ai.AiApi;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequireAdmin
class AiConfigurationController {

    private final AiSettingsService settingsService;
    private final AiService aiService;
    private final HouseholdApi householdApi;

    AiConfigurationController(AiSettingsService settingsService, AiService aiService, HouseholdApi householdApi) {
        this.settingsService = settingsService;
        this.aiService = aiService;
        this.householdApi = householdApi;
    }

    record SettingsResponse(
            boolean enabled,
            String providerId,
            boolean credentialConfigured,
            boolean outboundEnabled,
            int requestsPerMinute,
            int maxContextTokens,
            int maxConcurrentRequests,
            int requestTimeoutSeconds,
            int version
    ) {
        static SettingsResponse from(AiSettingsService.SettingsView view) {
            return new SettingsResponse(view.enabled(), view.providerId(), view.credentialConfigured(),
                    view.outboundEnabled(), view.requestsPerMinute(), view.maxContextTokens(),
                    view.maxConcurrentRequests(), view.requestTimeoutSeconds(), view.version());
        }
    }

    record SettingsUpdateRequest(
            boolean enabled,
            @NotBlank @Size(max = 50) String providerId,
            @Size(max = 4096) String credential,
            boolean clearCredential,
            boolean outboundEnabled,
            @Min(1) @Max(600) int requestsPerMinute,
            @Min(256) @Max(131072) int maxContextTokens,
            @Min(1) @Max(32) int maxConcurrentRequests,
            @Min(1) @Max(300) int requestTimeoutSeconds,
            @Min(0) int version
    ) {
    }

    @GetMapping("/settings")
    SettingsResponse settings() {
        return SettingsResponse.from(settingsService.currentView());
    }

    @PutMapping("/settings")
    SettingsResponse update(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody SettingsUpdateRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var updated = settingsService.update(member.householdId(), principal.getAccountId(),
                new AiSettingsService.UpdateCommand(
                        request.enabled(), request.providerId(), request.credential(), request.clearCredential(),
                        request.outboundEnabled(), request.requestsPerMinute(), request.maxContextTokens(),
                        request.maxConcurrentRequests(), request.requestTimeoutSeconds(), request.version()));
        return SettingsResponse.from(updated);
    }

    @GetMapping("/status")
    AiApi.AiStatus status() {
        return aiService.status();
    }
}
