package com.zija.reminder.internal;

public class ReminderTaskInvalidTransitionException extends RuntimeException {
    public ReminderTaskInvalidTransitionException() { super(); }
    public ReminderTaskInvalidTransitionException(String m) { super(m); }
}
