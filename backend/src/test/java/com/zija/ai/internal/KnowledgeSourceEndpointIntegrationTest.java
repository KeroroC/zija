package com.zija.ai.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.ai.internal.persistence.ClaimedKnowledgeSource;
import com.zija.ai.internal.persistence.KnowledgeChunkMapper;
import com.zija.ai.internal.persistence.KnowledgeSourceEntity;
import com.zija.ai.internal.persistence.KnowledgeSourceMapper;
import com.zija.file.AttachmentPurgedEvent;
import com.zija.file.FileApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识来源端到端集成测试：选择/取消/重试、异步准备状态机、格式边界、
 * 附件生命周期同步（回收/恢复/改挂/永久删除）、权限与有限自动重试。
 *
 * <p>向量写入经 PgVectorStore 完成，嵌入由确定性的 {@link DeterministicEmbeddingModel}
 * 提供（可切换失败以验证失败与重试），无需真实模型。调度器在测试中禁用，
 * 以直接调用 {@link KnowledgePreparationService#prepareDue} 覆盖。</p>
 */
@AutoConfigureMockMvc
@Import(KnowledgeSourceEndpointIntegrationTest.TestEmbeddingConfiguration.class)
class KnowledgeSourceEndpointIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ITEM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID LOT_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FileApi fileApi;
    @Autowired
    private KnowledgePreparationService preparationService;
    @Autowired
    private KnowledgeSourceStateStore stateStore;
    @Autowired
    private KnowledgeSourceMapper knowledgeSourceMapper;
    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;
    @Autowired
    private DeterministicEmbeddingModel embeddingModel;
    @Autowired
    private BlockingPurgeListener blockingPurgeListener;

    @MockitoBean
    private ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (1, ?, '测试家庭', 'Asia/Shanghai')
                """, HOUSEHOLD_ID);
        insertMember(OWNER_ACCOUNT_ID, "owner", "OWNER");
        insertMember(MEMBER_ACCOUNT_ID, "member", "MEMBER");
        insertItem(LOT_ID, ITEM_ID);
        embeddingModel.setFailEmbedding(false);
        embeddingModel.resetEmbeddingCalls();
        blockingPurgeListener.reset();
    }

    // ---------- 选择 / 取消 / 重试 端点 ----------

    @Test
    void memberCanSelectAndListKnowledgeSource() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "使用说明.pdf");

        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        mvc.perform(get("/api/v1/ai/knowledge-sources").with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PROCESSING"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'AI_KNOWLEDGE_SOURCE_SELECTED'",
                Integer.class)).isOne();
    }

    @Test
    void selectingImageIsRejectedAsUnsupportedFormat() throws Exception {
        UUID fileId = storeAttachment("cover.jpg", "image/jpeg", jpegBytes(), FileApi.MOUNT_ITEM, ITEM_ID);

        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AI_KNOWLEDGE_SOURCE_FORMAT_UNSUPPORTED"));

        assertThat(countSources()).isZero();
    }

    @Test
    void selectingNonExistentOrRecycledAttachmentFails() throws Exception {
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", UUID.randomUUID()).with(auth(member())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AI_KNOWLEDGE_SOURCE_NOT_FOUND"));

        UUID fileId = storePdfAttachment(FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID, "回收的说明.pdf");
        fileApi.recycle(HOUSEHOLD_ID, fileId);
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AI_KNOWLEDGE_SOURCE_STATE_CONFLICT"));
    }

    @Test
    void cancelMarksDisabledAndRemovesChunks() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "可取消的说明.pdf");
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk());
        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(countChunks(fileId)).isEqualTo(2);

        mvc.perform(delete("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.disabledReason").value("CANCELLED"));

        assertThat(countChunks(fileId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'AI_KNOWLEDGE_SOURCE_CANCELLED'",
                Integer.class)).isOne();

        // 重新选择同一附件：重新进入处理中
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void retryIsRejectedForAvailableOrMissingSource() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "重试校验.pdf");
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()));
        preparationService.prepareDue(OffsetDateTime.now());

        mvc.perform(post("/api/v1/ai/knowledge-sources/{fileId}/retry", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AI_KNOWLEDGE_SOURCE_STATE_CONFLICT"));

        mvc.perform(post("/api/v1/ai/knowledge-sources/{fileId}/retry", UUID.randomUUID()).with(auth(member())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AI_KNOWLEDGE_SOURCE_NOT_FOUND"));
    }

    // ---------- 异步准备 / 状态机 ----------

    @Test
    void preparationMakesSourceAvailableWithLocatableChunkMetadata() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "操作手册.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());

        KnowledgeSourceEntity entity = sourceOf(fileId);
        assertThat(entity.getStatus()).isEqualTo("AVAILABLE");
        assertThat(entity.getProcessingVersion()).isEqualTo(1);
        assertThat(entity.getProcessedAt()).isNotNull();

        List<Map<String, Object>> chunks = jdbc.queryForList(
                "SELECT content, metadata, embedding::text AS embedding_text "
                        + "FROM ai_knowledge_chunk WHERE household_id = ? AND attachment_id = ? ORDER BY char_start",
                HOUSEHOLD_ID, fileId.toString());
        assertThat(chunks).hasSize(2);
        Map<String, Object> first = chunks.get(0);
        assertThat((String) first.get("content")).contains("First page content");
        assertThat((String) first.get("embedding_text")).startsWith("[1");
        assertThat(first.get("metadata").toString())
                .contains("\"household_id\": \"" + HOUSEHOLD_ID + "\"")
                .contains("\"mount_type\": \"ITEM\"")
                .contains("\"mount_id\": \"" + ITEM_ID + "\"")
                .contains("\"item_id\": \"" + ITEM_ID + "\"")
                .contains("\"attachment_id\": \"" + fileId + "\"")
                .contains("\"readiness_status\": \"AVAILABLE\"")
                .contains("\"page_number\": 1")
                .contains("\"embedding_dimensions\": 1024")
                .contains("\"chunker_version\": \"1\"")
                .contains("\"processing_version\": 1");
        assertThat(chunks.get(1).get("metadata").toString()).contains("\"page_number\": 2");
    }

    @Test
    void scannedPdfFailsWithTextNotExtractable() throws Exception {
        UUID fileId = storeAttachment("扫描件.pdf", "application/pdf", scannedPdfBytes(),
                FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID);
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());

        KnowledgeSourceEntity entity = sourceOf(fileId);
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getFailureCode()).isEqualTo("TEXT_NOT_EXTRACTABLE");
        assertThat(countChunks(fileId)).isZero();
    }

    @Test
    void providerFailureMarksFailedAndManualRetryRecovers() throws Exception {
        embeddingModel.setFailEmbedding(true);
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "短暂故障.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());

        KnowledgeSourceEntity failed = sourceOf(fileId);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailureCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isNotNull();
        assertThat(countChunks(fileId)).isZero();

        // 手动重试：重置计数并重新进入处理中
        embeddingModel.setFailEmbedding(false);
        mvc.perform(post("/api/v1/ai/knowledge-sources/{fileId}/retry", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        preparationService.prepareDue(OffsetDateTime.now());

        assertThat(sourceOf(fileId).getStatus()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'AI_KNOWLEDGE_SOURCE_RETRIED'",
                Integer.class)).isOne();
    }

    @Test
    void limitedAutoRetryRecoversWhenProviderReturns() throws Exception {
        embeddingModel.setFailEmbedding(true);
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "自动重试恢复.pdf");
        select(fileId);

        OffsetDateTime t0 = OffsetDateTime.now();
        preparationService.prepareDue(t0);
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("FAILED");
        assertThat(sourceOf(fileId).getAttemptCount()).isEqualTo(1);

        embeddingModel.setFailEmbedding(false);
        // 退避到期后由调度重试（不经手动操作）
        preparationService.prepareDue(t0.plusSeconds(61));

        assertThat(sourceOf(fileId).getStatus()).isEqualTo("AVAILABLE");
        assertThat(embeddingModel.embeddingCalls()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void autoRetryBudgetIsLimited() throws Exception {
        embeddingModel.setFailEmbedding(true);
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "自动重试耗尽.pdf");
        select(fileId);

        OffsetDateTime t0 = OffsetDateTime.now();
        preparationService.prepareDue(t0);                    // attempt 1 → 退避 60s
        preparationService.prepareDue(t0.plusSeconds(61));    // attempt 2 → 退避 120s
        preparationService.prepareDue(t0.plusSeconds(181));   // attempt 3 → 不再自动重试

        KnowledgeSourceEntity entity = sourceOf(fileId);
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getAttemptCount()).isEqualTo(3);
        assertThat(entity.getNextAttemptAt()).isNull();

        // 退避预算耗尽后不再被认领
        assertThat(preparationService.prepareDue(t0.plusSeconds(10000))).isZero();
        assertThat(sourceOf(fileId).getAttemptCount()).isEqualTo(3);
    }

    @Test
    void reselectFailedSourceRestartsPreparation() throws Exception {
        embeddingModel.setFailEmbedding(true);
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "失败后重选.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("FAILED");
        assertThat(sourceOf(fileId).getAttemptCount()).isEqualTo(1);
        embeddingModel.setFailEmbedding(false);

        // 对失败来源重新执行选择：重新进入处理中并重置自动重试计数
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        assertThat(sourceOf(fileId).getAttemptCount()).isZero();

        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void contentReadFailureMarksContentUnreadable() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "存储损坏.pdf");
        select(fileId);
        // 物理删除存储卷上的文件，模拟读取失败（存储卷异常）
        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM stored_file WHERE id = ?", String.class, fileId);
        java.nio.file.Files.delete(java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "zija-test-files", storageKey));

        preparationService.prepareDue(OffsetDateTime.now());

        KnowledgeSourceEntity entity = sourceOf(fileId);
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getFailureCode()).isEqualTo("CONTENT_UNREADABLE");
        assertThat(entity.getFailureMessage()).contains("读取失败");
    }

    @Test
    void staleWorkerCannotOverrideNewerClaim() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "租约竞争.pdf");
        select(fileId);
        OffsetDateTime now = OffsetDateTime.now();

        // 第一次认领（栅栏版本 1），随后租约到期被第二次认领接管（栅栏版本 2）
        List<ClaimedKnowledgeSource> first = knowledgeSourceMapper.claimDue(now, now.plusSeconds(300), 10);
        assertThat(first).hasSize(1);
        UUID sourceId = first.getFirst().getId();
        List<ClaimedKnowledgeSource> second =
                knowledgeSourceMapper.claimDue(now.plusSeconds(301), now.plusSeconds(601), 10);
        assertThat(second).hasSize(1);
        assertThat(sourceOf(fileId).getProcessingVersion()).isEqualTo(2);

        // 过期工作者的成功/失败写入与分块清理全部被栅栏拒绝
        assertThat(knowledgeSourceMapper.markAvailableIfProcessing(sourceId, 1, now)).isZero();
        stateStore.markFailed(sourceId, 1, now, "PREPARATION_FAILED", "过期批次");
        assertThat(knowledgeChunkMapper.deleteByAttachmentIfCurrent(HOUSEHOLD_ID, fileId, sourceId, 1)).isZero();
        KnowledgeSourceEntity entity = sourceOf(fileId);
        assertThat(entity.getStatus()).isEqualTo("PROCESSING");
        assertThat(entity.getFailureCode()).isNull();
        assertThat(entity.getAttemptCount()).isZero();

        // 现任认领者（版本 2）不受影响
        assertThat(knowledgeSourceMapper.markAvailableIfProcessing(sourceId, 2, now)).isOne();
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void allSupportedFormatsBecomeAvailable() throws Exception {
        List<UUID> fileIds = List.of(
                storeAttachment("说明.md", "text/markdown", markdownBytes(),
                        FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID),
                storeAttachment("便签.txt", "text/plain", txtBytes(),
                        FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID),
                storeAttachment("资料.docx", docxType(), docxBytes(),
                        FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID),
                storeAttachment("清单.xlsx", xlsxType(), xlsxBytes(),
                        FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID),
                storeAttachment("演示.pptx", pptxType(), pptxBytes(),
                        FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID),
                storePdfAttachment(FileApi.MOUNT_HOUSEHOLD, HOUSEHOLD_ID, "说明.pdf"));
        for (UUID fileId : fileIds) {
            select(fileId);
        }
        preparationService.prepareDue(OffsetDateTime.now());

        for (UUID fileId : fileIds) {
            assertThat(sourceOf(fileId).getStatus())
                    .as("附件 %s 应处理成功", fileId)
                    .isEqualTo("AVAILABLE");
        }
    }

    // ---------- 附件生命周期同步 ----------

    @Test
    void recycleDisablesImmediatelyAndRestoreReprepares() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "生命周期.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(countChunks(fileId)).isEqualTo(2);

        fileApi.recycle(HOUSEHOLD_ID, fileId);
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("DISABLED");
        assertThat(sourceOf(fileId).getDisabledReason()).isEqualTo("RECYCLED");
        // 回收后立即排除分块，即使物理文件仍在
        assertThat(countChunks(fileId)).isZero();

        // 处理中实例不得把已停用来源复活
        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("DISABLED");

        fileApi.restore(HOUSEHOLD_ID, fileId);
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("PROCESSING");
        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("AVAILABLE");
        assertThat(countChunks(fileId)).isEqualTo(2);
    }

    @Test
    void remountFollowsNewMountingScope() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "改挂说明.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());

        fileApi.remount(HOUSEHOLD_ID, fileId, FileApi.MOUNT_LOT, LOT_ID);

        assertThat(sourceOf(fileId).getMountType()).isEqualTo("LOT");
        assertThat(sourceOf(fileId).getMountId()).isEqualTo(LOT_ID);
        String metadata = jdbc.queryForList(
                        "SELECT metadata FROM ai_knowledge_chunk WHERE attachment_id = ?",
                        fileId.toString())
                .getFirst().get("metadata").toString();
        assertThat(metadata)
                .contains("\"mount_type\": \"LOT\"")
                .contains("\"mount_id\": \"" + LOT_ID + "\"")
                .contains("\"item_id\": \"" + ITEM_ID + "\"")
                .contains("\"lot_id\": \"" + LOT_ID + "\"");
        // 内容未变：不重新处理
        assertThat(sourceOf(fileId).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void renameUpdatesAttachmentReferenceWithoutReprocessingContent() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "原始说明.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());
        int processingVersion = sourceOf(fileId).getProcessingVersion();
        int embeddingCalls = embeddingModel.embeddingCalls();

        assertThat(fileApi.rename(HOUSEHOLD_ID, fileId, "更新后的说明.pdf").name())
                .isEqualTo("更新后的说明.pdf");
        mvc.perform(get("/api/v1/ai/knowledge-sources").with(auth(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].processingVersion").value(processingVersion));
        assertThat(embeddingModel.embeddingCalls()).isEqualTo(embeddingCalls);
    }

    @Test
    void purgeClearsSourceRowAndChunks() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "待删除.pdf");
        select(fileId);
        preparationService.prepareDue(OffsetDateTime.now());
        assertThat(countChunks(fileId)).isEqualTo(2);

        fileApi.recycle(HOUSEHOLD_ID, fileId);
        assertThat(fileApi.purge(HOUSEHOLD_ID, fileId)).isTrue();

        assertThat(countSources()).isZero();
        assertThat(countChunks(fileId)).isZero();
    }

    @Test
    void permanentDeletionDuringPreparationLeavesNoLateDerivedChunks() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "并发清理说明.pdf");
        select(fileId);
        embeddingModel.blockNextEmbedding();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var preparation = executor.submit(() -> preparationService.prepareDue(OffsetDateTime.now()));
            assertThat(embeddingModel.awaitBlocked()).isTrue();

            fileApi.recycle(HOUSEHOLD_ID, fileId);
            blockingPurgeListener.block(fileId);
            var purge = executor.submit(() -> fileApi.purge(HOUSEHOLD_ID, fileId));
            assertThat(blockingPurgeListener.awaitBlocked()).isTrue();
            blockingPurgeListener.release();
            assertThat(purge.get(30, TimeUnit.SECONDS)).isTrue();

            embeddingModel.releaseEmbedding();
            assertThat(preparation.get(30, TimeUnit.SECONDS)).isOne();
        } finally {
            embeddingModel.releaseEmbedding();
            blockingPurgeListener.release();
        }

        assertThat(countSources()).isZero();
        assertThat(countChunks(fileId)).isZero();
    }

    @Test
    void restoreLosesCleanlyWhenConcurrentPermanentDeletionWins() throws Exception {
        UUID fileId = storeAttachment(
                "并发删除.txt",
                "text/plain",
                txtBytes(),
                FileApi.MOUNT_ITEM,
                ITEM_ID);
        select(fileId);
        fileApi.recycle(HOUSEHOLD_ID, fileId);

        blockingPurgeListener.block(fileId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var purge = executor.submit(() -> fileApi.purge(HOUSEHOLD_ID, fileId));
            assertThat(blockingPurgeListener.awaitBlocked()).isTrue();

            var restoreStarted = new CountDownLatch(1);
            var restore = executor.submit(() -> {
                restoreStarted.countDown();
                return fileApi.restore(HOUSEHOLD_ID, fileId);
            });
            assertThat(restoreStarted.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(restore).isNotDone();

            blockingPurgeListener.release();
            assertThat(purge.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(restore.get(30, TimeUnit.SECONDS)).isNull();
        } finally {
            blockingPurgeListener.release();
        }

        assertThat(fileApi.findAttachment(HOUSEHOLD_ID, fileId)).isEmpty();
        mvc.perform(get("/api/v1/ai/knowledge-sources").with(auth(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.fileId == '%s')]".formatted(fileId)).isEmpty());
    }

    // ---------- 权限 ----------

    @Test
    void anyActiveMemberCanManageKnowledgeSources() throws Exception {
        UUID fileId = storePdfAttachment(FileApi.MOUNT_ITEM, ITEM_ID, "成员可选.pdf");
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai/knowledge-sources").with(auth(member())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DISABLED"));
    }

    // ---------- 辅助 ----------

    private void select(UUID fileId) throws Exception {
        mvc.perform(put("/api/v1/ai/knowledge-sources/{fileId}", fileId).with(auth(member())).with(csrf()))
                .andExpect(status().isOk());
    }

    private KnowledgeSourceEntity sourceOf(UUID fileId) {
        List<KnowledgeSourceEntity> rows = knowledgeSourceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeSourceEntity>()
                        .eq(KnowledgeSourceEntity::getHouseholdId, HOUSEHOLD_ID)
                        .eq(KnowledgeSourceEntity::getFileId, fileId));
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private int countSources() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_source WHERE household_id = ?",
                Integer.class, HOUSEHOLD_ID);
    }

    private int countChunks(UUID fileId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_knowledge_chunk WHERE household_id = ? AND attachment_id = ?",
                Integer.class, HOUSEHOLD_ID, fileId.toString());
    }

    // ---------- 样本生成 ----------

    private UUID storePdfAttachment(String mountType, UUID mountId, String name) {
        return storeAttachment(name, "application/pdf", pdfBytes("First page content", "Second page content"), mountType, mountId);
    }

    private UUID storeAttachment(String name, String declaredMediaType, byte[] content,
                                 String mountType, UUID mountId) {
        return fileApi.store(HOUSEHOLD_ID, content, name, declaredMediaType, mountType, mountId).id();
    }

    private static byte[] pdfBytes(String... pageTexts) {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (String text : pageTexts) {
                var page = new org.apache.pdfbox.pdmodel.PDPage();
                document.addPage(page);
                try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            try (var out = new java.io.ByteArrayOutputStream()) {
                document.save(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] scannedPdfBytes() {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            try (var out = new java.io.ByteArrayOutputStream()) {
                document.save(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] markdownBytes() {
        return "# 扫地机使用说明\n\n第一步：加注清水。\n\n## 保养\n\n每两周清理尘盒。".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] txtBytes() {
        return "这是一份普通文本说明。\n请按说明书使用。".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String docxType() {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private static byte[] docxBytes() {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            document.createParagraph().createRun().setText("净水器滤芯更换说明");
            try (var out = new java.io.ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String xlsxType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private static byte[] xlsxBytes() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = workbook.createSheet("库存清单");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("物品");
            header.createCell(1).setCellValue("数量");
            var data = sheet.createRow(1);
            data.createCell(0).setCellValue("洗衣液");
            data.createCell(1).setCellValue(3);
            try (var out = new java.io.ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pptxType() {
        return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    }

    private static byte[] pptxBytes() {
        try (org.apache.poi.xslf.usermodel.XMLSlideShow presentation =
                     new org.apache.poi.xslf.usermodel.XMLSlideShow()) {
            var slide = presentation.createSlide();
            var textBox = slide.createTextBox();
            textBox.setText("空气净化器使用注意事项");
            try (var out = new java.io.ByteArrayOutputStream()) {
                presentation.write(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] jpegBytes() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F',
                0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
        };
    }

    private void insertMember(UUID accountId, String username, String role) {
        jdbc.update("""
                INSERT INTO account(id, username, username_normalized, password_hash, display_name)
                VALUES (?, ?, ?, '{bcrypt}test', ?)
                """, accountId, username, username, username);
        jdbc.update("""
                INSERT INTO member(id, household_id, account_id, role, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), HOUSEHOLD_ID, accountId, role);
    }

    private void insertItem(UUID lotId, UUID itemId) {
        UUID unitId = UUID.fromString("30000000-0000-0000-0000-00000000000a");
        jdbc.update("""
                INSERT INTO catalog_unit(id, household_id, name, name_normalized, decimal_scale)
                VALUES (?, ?, '件', '件', 0)
                """, unitId, HOUSEHOLD_ID);
        jdbc.update("""
                INSERT INTO catalog_item(id, household_id, name, management_type, unit_id)
                VALUES (?, ?, '测试物品', 'DURABLE', ?)
                """, itemId, HOUSEHOLD_ID, unitId);
        jdbc.update("""
                INSERT INTO inventory_lot(id, household_id, item_id, lot_number)
                VALUES (?, ?, ?, 'LOT-PINE-001')
                """, lotId, HOUSEHOLD_ID, itemId);
    }

    private ZijaPrincipal member() {
        return new ZijaPrincipal(MEMBER_ACCOUNT_ID, "member", "成员", "{bcrypt}test", true);
    }

    private RequestPostProcessor auth(ZijaPrincipal principal) {
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEmbeddingConfiguration {

        @Bean
        @Primary
        DeterministicEmbeddingModel deterministicEmbeddingModel() {
            return new DeterministicEmbeddingModel();
        }

        @Bean
        BlockingPurgeListener blockingPurgeListener() {
            return new BlockingPurgeListener();
        }
    }

    static final class BlockingPurgeListener {

        private final AtomicReference<UUID> target = new AtomicReference<>();
        private volatile CountDownLatch blocked = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        void block(UUID fileId) {
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
            target.set(fileId);
        }

        boolean awaitBlocked() throws InterruptedException {
            return blocked.await(10, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }

        void reset() {
            release();
            target.set(null);
            blocked = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        @EventListener
        public void onAttachmentPurged(AttachmentPurgedEvent event) {
            if (!event.fileId().equals(target.get())) {
                return;
            }
            blocked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release attachment purge");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("attachment purge interrupted", exception);
            }
        }
    }

    /** 确定性的嵌入模型：默认返回 1024 维固定向量；可切换为失败以验证失败与重试路径。 */
    static final class DeterministicEmbeddingModel implements EmbeddingModel {

        private final AtomicBoolean failEmbedding = new AtomicBoolean(false);
        private final AtomicInteger embeddingCalls = new AtomicInteger();
        private final AtomicBoolean blockNext = new AtomicBoolean();
        private volatile CountDownLatch blocked = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            embeddingCalls.incrementAndGet();
            awaitReleaseIfRequested();
            if (failEmbedding.get()) {
                throw new IllegalStateException("mock embedding provider unavailable");
            }
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(vector(), 0, EmbeddingResultMetadata.EMPTY))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            embeddingCalls.incrementAndGet();
            awaitReleaseIfRequested();
            return vector();
        }

        @Override
        public int dimensions() {
            return 1024;
        }

        void setFailEmbedding(boolean fail) {
            failEmbedding.set(fail);
        }

        int embeddingCalls() {
            return embeddingCalls.get();
        }

        void resetEmbeddingCalls() {
            embeddingCalls.set(0);
            releaseEmbedding();
            blockNext.set(false);
            blocked = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        void blockNextEmbedding() {
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
            blockNext.set(true);
        }

        boolean awaitBlocked() throws InterruptedException {
            return blocked.await(10, TimeUnit.SECONDS);
        }

        void releaseEmbedding() {
            release.countDown();
        }

        private void awaitReleaseIfRequested() {
            if (!blockNext.compareAndSet(true, false)) {
                return;
            }
            blocked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release embedding");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("embedding interrupted", exception);
            }
        }

        private static float[] vector() {
            float[] vector = new float[1024];
            vector[0] = 1.0f;
            return vector;
        }
    }
}
