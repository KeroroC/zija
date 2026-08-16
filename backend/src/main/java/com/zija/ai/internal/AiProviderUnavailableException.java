package com.zija.ai.internal;

class AiProviderUnavailableException extends RuntimeException {

    AiProviderUnavailableException(String message) {
        super(message);
    }

    AiProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
