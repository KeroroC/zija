package com.zija.location.internal.exception;

public class LocationVersionConflictException extends RuntimeException {
    public LocationVersionConflictException() {
        super("version conflict");
    }
}
