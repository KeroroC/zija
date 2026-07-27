package com.zija.reminder.internal.mail;

class MailSettingVersionConflictException extends RuntimeException {
    public MailSettingVersionConflictException() { super(); }
    public MailSettingVersionConflictException(String m) { super(m); }
}
