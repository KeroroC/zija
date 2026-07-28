package com.zija.household.internal.exception;

public class MemberConcurrentUpdateException extends RuntimeException {
    public MemberConcurrentUpdateException() {
        super("member was modified concurrently");
    }
}
