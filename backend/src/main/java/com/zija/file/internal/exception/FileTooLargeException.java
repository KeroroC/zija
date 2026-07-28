package com.zija.file.internal.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(long byteSize) {
        super("file too large: " + byteSize + " bytes (max 5242880)");
    }
}
