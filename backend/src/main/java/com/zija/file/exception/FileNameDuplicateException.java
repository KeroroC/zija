package com.zija.file.exception;

public class FileNameDuplicateException extends RuntimeException {
    public FileNameDuplicateException(String name) {
        super("duplicate attachment name: " + name);
    }
}
