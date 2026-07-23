package com.zija.file.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * 文件物理存储组件。
 * <p>
 * 管理文件在本地磁盘上的读写和删除操作。文件按年月（yyyy/MM）分目录存储，
 * 使用 UUID 作为文件名以避免冲突。写入采用临时文件 + 原子移动策略，确保写入过程的完整性。
 * 删除文件时自动清理空的父级目录（最多向上两级）。
 */
@Component
class FileStorage {

    private final Path storageRoot;

    FileStorage(@Value("${zija.file.storage-path}") String storagePath) {
        this.storageRoot = Path.of(storagePath);
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageRoot);
        if (!Files.isWritable(storageRoot)) {
            throw new IllegalStateException("File storage path is not writable: " + storageRoot);
        }
    }

    /**
     * 将文件内容写入磁盘，按年月分目录存储，使用原子写入策略。
     *
     * @return 相对于存储根目录的存储键（如 {@code 2026/07/<uuid>.jpg}）
     */
    String store(byte[] content, String extension) throws IOException {
        String datePrefix = java.time.LocalDate.now().toString().replace("-", "/").substring(0, 7);
        String storageKey = datePrefix + "/" + UUID.randomUUID() + extension;
        Path target = storageRoot.resolve(storageKey);

        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".tmp-", "");
        try {
            Files.write(temp, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return storageKey;
    }

    /**
     * 根据存储键读取文件内容。
     */
    byte[] read(String storageKey) throws IOException {
        return Files.readAllBytes(storageRoot.resolve(storageKey));
    }

    /**
     * 删除物理文件，并尝试清理空的父级目录。
     */
    void delete(String storageKey) throws IOException {
        Path target = storageRoot.resolve(storageKey);
        Files.deleteIfExists(target);
        Path parent = target.getParent();
        for (int i = 0; i < 2 && parent != null && !parent.equals(storageRoot); i++) {
            try (var stream = Files.list(parent)) {
                if (stream.findFirst().isEmpty()) {
                    Files.delete(parent);
                    parent = parent.getParent();
                } else {
                    break;
                }
            }
        }
    }

    Path resolve(String storageKey) {
        return storageRoot.resolve(storageKey);
    }
}
