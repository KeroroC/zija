package com.zija.household.internal;

public class MemberConcurrentUpdateException extends RuntimeException {
    public MemberConcurrentUpdateException() {
        super("member was modified concurrently");
    }
}
