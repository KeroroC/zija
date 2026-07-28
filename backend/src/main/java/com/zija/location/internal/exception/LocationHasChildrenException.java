package com.zija.location.internal.exception;

public class LocationHasChildrenException extends RuntimeException {
    public LocationHasChildrenException() {
        super("location has children");
    }
}
