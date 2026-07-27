package com.zija.file.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zija.file.internal.persistence.StoredFileEntity;
import com.zija.file.internal.persistence.StoredFileMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileIntegrityServiceTest {

  @TempDir Path storageRoot;

  StoredFileMapper storedFileMapper;
  FileIntegrityService service;

  @BeforeAll
  static void initMybatisPlusLambdaCache() {
    // Register entity metadata so LambdaQueryWrapper.resolve() works outside Spring context
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""),
        StoredFileEntity.class);
  }

  @BeforeEach
  void setUp() {
    storedFileMapper = mock(StoredFileMapper.class);
    service = new FileIntegrityService(storedFileMapper, storageRoot.toString());
  }

  @Test
  void allFilesPresentAndMatch_returnsZeroCounts() throws Exception {
    Path file = storageRoot.resolve("ab/cd/test.jpg");
    Files.createDirectories(file.getParent());
    byte[] content = "hello".getBytes();
    Files.write(file, content);

    String sha256 = sha256Hex(content);
    String storageKey = "ab/cd/test.jpg";

    StoredFileEntity entity = new StoredFileEntity();
    entity.setStorageKey(storageKey);
    entity.setSha256(sha256);
    entity.setByteSize((long) content.length);

    when(storedFileMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(entity));

    FileIntegrityReport report = service.check();

    assertThat(report.checkedCount()).isEqualTo(1);
    assertThat(report.missingCount()).isEqualTo(0);
    assertThat(report.hashMismatchCount()).isEqualTo(0);
    assertThat(report.byteSizeMismatchCount()).isEqualTo(0);
    assertThat(report.orphanCount()).isEqualTo(0);
    assertThat(report.missing()).isEmpty();
    assertThat(report.hashMismatch()).isEmpty();
  }

  @Test
  void missingFile_reportsMissing() {
    StoredFileEntity entity = new StoredFileEntity();
    entity.setStorageKey("nonexistent/file.jpg");
    entity.setSha256("abc123");
    entity.setByteSize(100L);

    when(storedFileMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(entity));

    FileIntegrityReport report = service.check();

    assertThat(report.checkedCount()).isEqualTo(1);
    assertThat(report.missingCount()).isEqualTo(1);
    assertThat(report.missing()).containsExactly("nonexistent/file.jpg");
  }

  @Test
  void corruptedFile_reportsHashMismatch() throws Exception {
    Path file = storageRoot.resolve("2026/07/corrupted.jpg");
    Files.createDirectories(file.getParent());
    byte[] originalContent = "original".getBytes();
    Files.write(file, originalContent);

    StoredFileEntity entity = new StoredFileEntity();
    entity.setStorageKey("2026/07/corrupted.jpg");
    entity.setSha256("expected_sha_that_does_not_match");
    entity.setByteSize((long) originalContent.length);

    when(storedFileMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(entity));

    FileIntegrityReport report = service.check();

    assertThat(report.checkedCount()).isEqualTo(1);
    assertThat(report.hashMismatchCount()).isEqualTo(1);
    assertThat(report.hashMismatch()).containsExactly("2026/07/corrupted.jpg");
  }

  @Test
  void byteSizeMismatch_reportsByteSizeMismatch() throws Exception {
    Path file = storageRoot.resolve("2026/07/sized.jpg");
    Files.createDirectories(file.getParent());
    byte[] content = "hello".getBytes();
    Files.write(file, content);

    String sha256 = sha256Hex(content);

    StoredFileEntity entity = new StoredFileEntity();
    entity.setStorageKey("2026/07/sized.jpg");
    entity.setSha256(sha256);
    entity.setByteSize(999L); // wrong size

    when(storedFileMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(entity));

    FileIntegrityReport report = service.check();

    assertThat(report.byteSizeMismatchCount()).isEqualTo(1);
    // Hash should still match since content is the same
    assertThat(report.hashMismatchCount()).isEqualTo(0);
  }

  @Test
  void orphanFile_onlyReportsNotFailure() throws Exception {
    // Create a file on disk that is NOT in the DB
    Path orphanFile = storageRoot.resolve("2026/07/orphan.png");
    Files.createDirectories(orphanFile.getParent());
    Files.write(orphanFile, "orphan".getBytes());

    // DB has no files
    when(storedFileMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of());

    FileIntegrityReport report = service.check();

    assertThat(report.checkedCount()).isEqualTo(0);
    assertThat(report.orphanCount()).isEqualTo(1);
  }

  @Test
  void multipleIssues_reportsAll() throws Exception {
    Path goodFile = storageRoot.resolve("2026/07/good.jpg");
    Files.createDirectories(goodFile.getParent());
    byte[] goodContent = "good".getBytes();
    Files.write(goodFile, goodContent);
    String goodSha = sha256Hex(goodContent);

    StoredFileEntity goodEntity = new StoredFileEntity();
    goodEntity.setStorageKey("2026/07/good.jpg");
    goodEntity.setSha256(goodSha);
    goodEntity.setByteSize((long) goodContent.length);

    StoredFileEntity missingEntity = new StoredFileEntity();
    missingEntity.setStorageKey("2026/07/missing.jpg");
    missingEntity.setSha256("abc");
    missingEntity.setByteSize(10L);

    when(storedFileMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(goodEntity, missingEntity));

    // Also create an orphan file
    Path orphanFile = storageRoot.resolve("2026/07/orphan.png");
    Files.write(orphanFile, "orphan".getBytes());

    FileIntegrityReport report = service.check();

    assertThat(report.checkedCount()).isEqualTo(2);
    assertThat(report.missingCount()).isEqualTo(1);
    assertThat(report.hashMismatchCount()).isEqualTo(0);
    assertThat(report.orphanCount()).isEqualTo(1);
  }

  private static String sha256Hex(byte[] data) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(data);
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}
