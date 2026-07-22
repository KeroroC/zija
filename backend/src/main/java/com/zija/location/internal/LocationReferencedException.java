package com.zija.location.internal;

public class LocationReferencedException extends RuntimeException {
    public LocationReferencedException() {
        super("location is referenced by inventory");
    }
}
