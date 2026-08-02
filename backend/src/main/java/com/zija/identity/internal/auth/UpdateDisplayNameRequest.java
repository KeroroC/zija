package com.zija.identity.internal.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 100, message = "长度不能超过 {max} 个字符")
        String displayName
) {
}
