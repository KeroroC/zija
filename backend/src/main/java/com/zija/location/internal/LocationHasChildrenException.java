package com.zija.location.internal;

public class LocationHasChildrenException extends RuntimeException {
    public LocationHasChildrenException() {
        super("location has children");
    }
}
