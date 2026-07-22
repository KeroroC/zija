package com.zija.location.internal;

public class LocationVersionConflictException extends RuntimeException {
    public LocationVersionConflictException() {
        super("version conflict");
    }
}
