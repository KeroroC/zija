package com.zija.identity.internal.auth;

import com.zija.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) @Utf8ByteLength(max = 72) String newPassword
) {
}
