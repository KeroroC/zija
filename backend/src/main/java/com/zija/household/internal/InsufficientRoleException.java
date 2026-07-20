package com.zija.household.internal;

public class InsufficientRoleException extends RuntimeException {
    public InsufficientRoleException() {
        super("insufficient role");
    }
}
