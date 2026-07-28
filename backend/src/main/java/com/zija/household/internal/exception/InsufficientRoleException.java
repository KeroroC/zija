package com.zija.household.internal.exception;

public class InsufficientRoleException extends RuntimeException {
    public InsufficientRoleException() {
        super("insufficient role");
    }
}
