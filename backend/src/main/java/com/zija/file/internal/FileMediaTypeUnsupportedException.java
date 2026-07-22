package com.zija.file.internal;

public class FileMediaTypeUnsupportedException extends RuntimeException {
    public FileMediaTypeUnsupportedException(String detected) {
        super("unsupported media type: " + detected);
    }
}
