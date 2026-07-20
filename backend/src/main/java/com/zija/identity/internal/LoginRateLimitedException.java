package com.zija.identity.internal;

public class LoginRateLimitedException extends RuntimeException {
    public LoginRateLimitedException(String message) {
        super(message);
    }
}
