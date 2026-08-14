package com.zija.shared;

/**
 * 摘要算法常量。
 * <p>
 * 适用于 {@link java.security.MessageDigest#getInstance(String)} 等算法查找调用，
 * 保证全库使用统一的算法标识。
 */
public final class ZijaDigests {

    /** SHA-256 算法名。 */
    public static final String SHA_256 = "SHA-256";

    private ZijaDigests() {
    }
}
