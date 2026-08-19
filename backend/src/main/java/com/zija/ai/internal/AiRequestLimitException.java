package com.zija.ai.internal;

class AiRequestLimitException extends RuntimeException {

    private final String reasonCode;

    AiRequestLimitException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    String reasonCode() {
        return reasonCode;
    }
}
