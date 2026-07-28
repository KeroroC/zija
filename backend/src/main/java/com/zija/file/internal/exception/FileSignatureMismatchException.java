package com.zija.file.internal.exception;

public class FileSignatureMismatchException extends RuntimeException {
    public FileSignatureMismatchException(String expected, String detected) {
        super("signature mismatch: declared=" + expected + ", detected=" + detected);
    }
}
