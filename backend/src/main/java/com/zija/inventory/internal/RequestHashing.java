package com.zija.inventory.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 请求哈希工具类，用于生成幂等键对应的请求指纹。
 * <p>
 * 调用方将命令类型与关键业务字段拼接后，通过 {@link #sha256(String)} 生成稳定的十六进制摘要，
 * 作为幂等记录的请求哈希值，用于判断同一幂等键是否携带了不同的请求内容。
 */
final class RequestHashing {

    private RequestHashing() {}

    /**
     * 对输入字符串执行 SHA-256 哈希，返回小写十六进制摘要。
     *
     * @param s 待哈希的字符串（通常为命令类型 + 关键业务字段的拼接结果）
     * @return 64 位小写十六进制 SHA-256 摘要
     */
    static String sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var hex = new StringBuilder();
            for (byte b : md.digest(s.getBytes(StandardCharsets.UTF_8)))
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
