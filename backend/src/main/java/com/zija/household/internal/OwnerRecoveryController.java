package com.zija.household.internal;

import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/owner-recovery")
class OwnerRecoveryController {

    private final OwnerRecoveryService recoveryService;

    OwnerRecoveryController(OwnerRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    public record InspectRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record InspectResponse(boolean valid, String ownerDisplayName) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 200) String newPassword) {
    }

    @PostMapping("/inspect")
    InspectResponse inspect(@Valid @RequestBody InspectRequest request) {
        Optional<OwnerRecoveryTokenEntity> token = recoveryService.inspect(request.token());
        return new InspectResponse(token.isPresent(), null);
    }

    @PostMapping("/reset-password")
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recoveryService.resetPassword(request.token(), request.newPassword());
    }
}
