package com.zija.file.internal.exception;

public class FileNameDuplicateException extends RuntimeException {
    public FileNameDuplicateException(String name) {
        super("duplicate attachment name: " + name);
    }
}
