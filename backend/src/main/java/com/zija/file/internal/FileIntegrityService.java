package com.zija.file.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import com.zija.shared.ZijaDigests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class FileIntegrityService {

  private final StoredFileMapper storedFileMapper;
  private final String storageRoot;

  private static final int MAX_DETAIL_ITEMS = 100;

  FileIntegrityService(StoredFileMapper storedFileMapper,
      @Value("${zija.file.storage-path}") String storageRoot) {
    this.storedFileMapper = storedFileMapper;
    this.storageRoot = storageRoot;
  }

  FileIntegrityReport check() {
    Path root = Path.of(storageRoot);
    List<StoredFileEntity> allFiles = storedFileMapper.selectList(
        new LambdaQueryWrapper<StoredFileEntity>()
            .select(StoredFileEntity::getStorageKey,
                    StoredFileEntity::getSha256,
                    StoredFileEntity::getByteSize));

    Set<String> dbKeys = allFiles.stream()
        .map(StoredFileEntity::getStorageKey)
        .collect(Collectors.toSet());

    List<String> missing = new ArrayList<>();
    List<String> hashMismatch = new ArrayList<>();
    long checkedCount = 0;
    long byteSizeMismatchCount = 0;

    for (StoredFileEntity entity : allFiles) {
      checkedCount++;
      Path filePath = root.resolve(entity.getStorageKey());
      if (!Files.exists(filePath)) {
        if (missing.size() < MAX_DETAIL_ITEMS) missing.add(entity.getStorageKey());
        continue;
      }
      try {
        byte[] actual = Files.readAllBytes(filePath);
        String actualHash = sha256Hex(actual);
        if (!actualHash.equals(entity.getSha256())) {
          if (hashMismatch.size() < MAX_DETAIL_ITEMS) hashMismatch.add(entity.getStorageKey());
        }
        if (actual.length != entity.getByteSize()) {
          byteSizeMismatchCount++;
        }
      } catch (IOException e) {
        if (missing.size() < MAX_DETAIL_ITEMS) missing.add(entity.getStorageKey());
      }
    }

    // 计算孤儿文件（卷上存在但 DB 无引用）
    long orphanCount = 0;
    try (var walk = Files.walk(root)) {
      orphanCount = walk
          .filter(Files::isRegularFile)
          .map(p -> root.relativize(p).toString().replace('\\', '/'))
          .filter(k -> !dbKeys.contains(k))
          .count();
    } catch (IOException ignored) {
      // 无法遍历卷时不计入失败
    }

    return new FileIntegrityReport(
        checkedCount,
        missing.size(),
        hashMismatch.size(),
        byteSizeMismatchCount,
        orphanCount,
        missing,
        hashMismatch);
  }

  static String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance(ZijaDigests.SHA_256);
      byte[] digest = md.digest(data);
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
