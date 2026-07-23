package com.zija.identity;

import java.util.UUID;

/**
 * 当前请求的会话信息，封装认证状态与登录用户的基本信息。
 */
public record SessionInfo(
        boolean authenticated,
        UUID accountId,
        String username,
        String displayName
) {
    /** 创建匿名会话信息（未认证状态）。 */
    public static SessionInfo anonymous() {
        return new SessionInfo(false, null, null, null);
    }
}
