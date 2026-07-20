package com.zija.household.internal;

public class InvalidInvitationException extends RuntimeException {
    public InvalidInvitationException() {
        super("invalid invitation");
    }
}
