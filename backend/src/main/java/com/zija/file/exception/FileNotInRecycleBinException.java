package com.zija.file.exception;

import java.util.UUID;

/**
 * 附件不在回收站、无法永久删除：目标仍是活附件（从未删除），或已在并发中
 * 被恢复 / 被定时任务物理清除，本次永久删除需要重试或放弃。
 */
public class FileNotInRecycleBinException extends RuntimeException {

    private final UUID fileId;

    public FileNotInRecycleBinException(UUID fileId) {
        super("附件不在回收站，无法永久删除: " + fileId);
        this.fileId = fileId;
    }

    public UUID getFileId() {
        return fileId;
    }
}
