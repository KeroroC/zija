package com.zija.household.internal.exception;

public class HouseholdAlreadyInitializedException extends RuntimeException {
    public HouseholdAlreadyInitializedException() {
        super("household already initialized");
    }
}
