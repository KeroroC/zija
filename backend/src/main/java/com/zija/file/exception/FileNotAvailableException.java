package com.zija.file.exception;

import java.util.UUID;

/**
 * 附件当前状态不允许该操作（例如改挂回收站内的附件，需先恢复）。
 */
public class FileNotAvailableException extends RuntimeException {

    private final UUID fileId;

    public FileNotAvailableException(UUID fileId) {
        super("附件不可用: " + fileId);
        this.fileId = fileId;
    }

    public UUID getFileId() {
        return fileId;
    }
}
