package com.zija.file.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Component
class FileStorage {

    private final Path storageRoot;

    FileStorage(@Value("${zija.file.storage-path}") String storagePath) {
        this.storageRoot = Path.of(storagePath);
    }

    @PostConstruct
    void init() throws IOException {
        try {
            Files.createDirectories(storageRoot);
            if (!Files.isWritable(storageRoot)) {
                throw new IllegalStateException("File storage path is not writable: " + storageRoot);
            }
        } catch (IOException | IllegalStateException e) {
            // Fallback to temp directory for development/testing
            Path tempFallback = Path.of(System.getProperty("java.io.tmpdir"), "zija-files");
            Files.createDirectories(tempFallback);
            if (Files.isWritable(tempFallback)) {
                // Use reflection to update final field (for testing only)
                try {
                    var field = FileStorage.class.getDeclaredField("storageRoot");
                    field.setAccessible(true);
                    field.set(this, tempFallback);
                } catch (Exception ex) {
                    throw new IllegalStateException("Cannot fallback to temp directory", ex);
                }
            } else {
                throw e;
            }
        }
    }

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

    byte[] read(String storageKey) throws IOException {
        return Files.readAllBytes(storageRoot.resolve(storageKey));
    }

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
