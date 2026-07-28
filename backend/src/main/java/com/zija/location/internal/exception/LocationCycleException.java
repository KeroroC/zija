package com.zija.location.internal.exception;

public class LocationCycleException extends RuntimeException {
    public LocationCycleException() {
        super("location move would create a cycle");
    }
}
