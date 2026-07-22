package com.zija.location.internal;

public class LocationCycleException extends RuntimeException {
    public LocationCycleException() {
        super("location move would create a cycle");
    }
}
