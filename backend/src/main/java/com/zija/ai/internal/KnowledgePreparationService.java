package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeChunker.Chunk;
import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import com.zija.ai.internal.exception.KnowledgeExtractionException;
import com.zija.ai.internal.persistence.ClaimedKnowledgeSource;
import com.zija.ai.internal.persistence.KnowledgeChunkMapper;
import com.zija.ai.internal.persistence.KnowledgeSourceEntity;
import com.zija.ai.internal.persistence.KnowledgeSourceMapper;
import com.zija.file.FileApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识来源异步准备：认领到期来源，抽取正文 → 分块 → 嵌入 → 写入向量库，
 * 全部成功后才标记可用；任何一步失败都落为 FAILED 并按有限自动重试退避调度。
 *
 * <p>认领与处理分离：认领（单条 UPDATE ... RETURNING + SKIP LOCKED）先占住行、
 * 顺延处理租约并原子自增栅栏版本，处理过程不持有数据库事务（嵌入是长时间外部调用）；
 * 失败标记由 {@link KnowledgeSourceStateStore} 的小事务落库，不被长流程回滚吞掉。
 * 租约到期（进程崩溃兜底）后来源可被重新认领，此时先前批次的全部状态写入与
 * 分块变更都因栅栏版本过期而落空：过期分块永不翻转为可检索、也不会误删现任
 * 认领者的分块，只可能残留不可见的 PROCESSING 行，由下一次成功准备顺带清理。</p>
 */
@Service
class KnowledgePreparationService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePreparationService.class);

    private final KnowledgeSourceMapper mapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeSourceStateStore stateStore;
    private final FileApi fileApi;
    private final KnowledgeTextExtractor extractor;
    private final KnowledgeTextQualityGate textQualityGate;
    private final KnowledgeChunker chunker;
    private final KnowledgeChunkDocumentFactory documentFactory;
    private final AiKnowledgeVectorStore vectorStore;
    private final KnowledgeScopeResolver scopeResolver;

    KnowledgePreparationService(
            KnowledgeSourceMapper mapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeSourceStateStore stateStore,
            FileApi fileApi,
            KnowledgeTextExtractor extractor,
            KnowledgeTextQualityGate textQualityGate,
            KnowledgeChunker chunker,
            KnowledgeChunkDocumentFactory documentFactory,
            AiKnowledgeVectorStore vectorStore,
            KnowledgeScopeResolver scopeResolver
    ) {
        this.mapper = mapper;
        this.chunkMapper = chunkMapper;
        this.stateStore = stateStore;
        this.fileApi = fileApi;
        this.extractor = extractor;
        this.textQualityGate = textQualityGate;
        this.chunker = chunker;
        this.documentFactory = documentFactory;
        this.vectorStore = vectorStore;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 认领到期知识来源并逐个处理（调度器与测试入口）。
     *
     * @return 本次认领并尝试处理的数量
     */
    public int prepareDue(OffsetDateTime now) {
        OffsetDateTime leaseUntil = now.plusSeconds(KnowledgeSourceStates.PROCESSING_LEASE_SECONDS);
        List<ClaimedKnowledgeSource> claimed = mapper.claimDue(now, leaseUntil, KnowledgeSourceStates.CLAIM_BATCH_SIZE);
        for (ClaimedKnowledgeSource claim : claimed) {
            try {
                processOne(claim.getId(), claim.getProcessingVersion(), now);
            } catch (RuntimeException ex) {
                log.warn("知识来源准备出现未预期异常: id={}", claim.getId(), ex);
                stateStore.markFailed(claim.getId(), claim.getProcessingVersion(), now,
                        KnowledgeSourceStates.FAILURE_PREPARATION_FAILED, message(ex));
            }
        }
        return claimed.size();
    }

    /** 处理单个知识来源（不持有数据库事务；状态转换经 StateStore 独立事务并带栅栏版本）。 */
    void processOne(UUID knowledgeSourceId, int claimedVersion, OffsetDateTime now) {
        KnowledgeSourceEntity entity = mapper.selectById(knowledgeSourceId);
        if (entity == null || !KnowledgeSourceStates.STATUS_PROCESSING.equals(entity.getStatus())
                || entity.getProcessingVersion() == null || entity.getProcessingVersion() != claimedVersion) {
            return;
        }
        UUID householdId = entity.getHouseholdId();
        UUID fileId = entity.getFileId();

        var attachment = fileApi.findAttachment(householdId, fileId).orElse(null);
        if (attachment == null) {
            // 附件已永久删除：清除来源与分块
            chunkMapper.deleteByAttachment(householdId, fileId);
            stateStore.deleteRow(knowledgeSourceId);
            return;
        }
        if (attachment.deletedAt() != null) {
            // 处理期间附件进入回收站：停用并排除分块
            stateStore.disable(knowledgeSourceId, KnowledgeSourceStates.DISABLED_RECYCLED, now);
            chunkMapper.deleteByAttachment(householdId, fileId);
            return;
        }
        if (!KnowledgeSourceStates.SUPPORTED_MEDIA_TYPES.contains(attachment.mediaType())) {
            stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                    KnowledgeSourceStates.FAILURE_FORMAT_UNSUPPORTED,
                    "该格式暂不支持作为知识来源: " + attachment.mediaType());
            return;
        }

        byte[] content;
        try {
            content = fileApi.readContent(householdId, fileId).orElse(null);
        } catch (RuntimeException ex) {
            // 物理文件读取失败（存储卷异常等）：与解析失败区分的专用失败码
            log.warn("知识来源附件内容读取失败: fileId={}", fileId, ex);
            stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                    KnowledgeSourceStates.FAILURE_CONTENT_UNREADABLE, "附件内容读取失败，请稍后重试或重新上传");
            return;
        }
        if (content == null) {
            // 读取与查找之间附件被物理删除
            chunkMapper.deleteByAttachment(householdId, fileId);
            stateStore.deleteRow(knowledgeSourceId);
            return;
        }
        try {
            List<TextUnit> units = extractor.extract(attachment.mediaType(), content);
            if (units.isEmpty()) {
                stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                        KnowledgeSourceStates.FAILURE_TEXT_NOT_EXTRACTABLE,
                        "未在文档中提取到文字，可能是扫描件或空文档");
                return;
            }
            var quality = textQualityGate.validate(attachment.mediaType(), units);
            if (!quality.accepted()) {
                stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                        KnowledgeSourceStates.FAILURE_TEXT_NOT_EXTRACTABLE,
                        quality.failureMessage());
                return;
            }
            List<Chunk> chunks = chunker.chunk(units);
            if (chunks.isEmpty()) {
                stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                        KnowledgeSourceStates.FAILURE_TEXT_NOT_EXTRACTABLE,
                        "未在文档中提取到可嵌入的文字");
                return;
            }

            var scope = scopeResolver.resolve(householdId, entity.getMountType(), entity.getMountId());
            var documents = documentFactory.build(attachment, entity, scope, chunks, claimedVersion);
            try {
                chunkMapper.deleteByAttachmentIfCurrent(householdId, fileId, knowledgeSourceId, claimedVersion);
                vectorStore.add(documents);
                // 全部写入成功后整批翻转为 AVAILABLE（部分失败不残留可检索分块）
                chunkMapper.markAllAvailableIfCurrent(householdId, fileId, knowledgeSourceId, claimedVersion);
            } catch (DataAccessException ex) {
                chunkMapper.deleteByAttachmentIfCurrent(householdId, fileId, knowledgeSourceId, claimedVersion);
                stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                        KnowledgeSourceStates.FAILURE_INDEX_WRITE_FAILED, message(ex));
                return;
            } catch (RuntimeException ex) {
                chunkMapper.deleteByAttachmentIfCurrent(householdId, fileId, knowledgeSourceId, claimedVersion);
                stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                        KnowledgeSourceStates.FAILURE_PROVIDER_UNAVAILABLE, message(ex));
                return;
            }
            int marked = stateStore.markAvailable(knowledgeSourceId, claimedVersion, now);
            if (marked == 0) {
                // 写入被栅栏拒绝：区分「被停用/取消」与「租约到期被接管」
                var current = mapper.selectById(knowledgeSourceId);
                if (current != null && KnowledgeSourceStates.STATUS_DISABLED.equals(current.getStatus())) {
                    // 处理期间被取消/回收：撤掉刚写入的分块（用户意图优先，无条件清理）
                    chunkMapper.deleteByAttachment(householdId, fileId);
                    log.info("知识来源在处理期间被停用，已撤销分块: fileId={}", fileId);
                } else {
                    // 被更高版本认领接管：放弃过期批次，不动现任认领者的分块
                    log.info("知识来源处理租约被接管，放弃过期批次: fileId={} staleVersion={}", fileId, claimedVersion);
                }
                return;
            }
            log.info("知识来源准备完成: fileId={} chunks={} version={}", fileId, documents.size(), claimedVersion);
        } catch (KnowledgeExtractionException ex) {
            stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                    KnowledgeSourceStates.FAILURE_EXTRACTION_FAILED, message(ex));
        } catch (RuntimeException ex) {
            stateStore.markFailed(knowledgeSourceId, claimedVersion, now,
                    KnowledgeSourceStates.FAILURE_PREPARATION_FAILED, message(ex));
        }
    }

    private static String message(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
