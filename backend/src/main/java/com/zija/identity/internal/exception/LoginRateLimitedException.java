package com.zija.identity.internal.exception;

public class LoginRateLimitedException extends RuntimeException {
    public LoginRateLimitedException(String message) {
        super(message);
    }
}
