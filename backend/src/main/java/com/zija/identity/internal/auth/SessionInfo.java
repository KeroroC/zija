package com.zija.identity.internal.auth;

import java.util.UUID;

public record SessionInfo(
        boolean authenticated,
        UUID accountId,
        String username,
        String displayName
) {
    public static SessionInfo anonymous() {
        return new SessionInfo(false, null, null, null);
    }
}
