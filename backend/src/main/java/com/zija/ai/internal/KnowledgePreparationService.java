package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeChunker.Chunk;
import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import com.zija.ai.internal.exception.KnowledgeExtractionException;
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
 * <p>认领与处理分离：认领（单条 UPDATE ... RETURNING + SKIP LOCKED）先占住行并顺延
 * 处理租约，处理过程不持有数据库事务（嵌入是长时间外部调用）；失败标记由
 * {@link KnowledgeSourceStateStore} 的小事务落库，不被长流程回滚吞掉。</p>
 */
@Service
class KnowledgePreparationService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePreparationService.class);

    private final KnowledgeSourceMapper mapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeSourceStateStore stateStore;
    private final FileApi fileApi;
    private final KnowledgeTextExtractor extractor;
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
        List<UUID> claimed = mapper.claimDue(now, leaseUntil, KnowledgeSourceStates.CLAIM_BATCH_SIZE);
        for (UUID id : claimed) {
            try {
                processOne(id, now);
            } catch (RuntimeException ex) {
                log.warn("知识来源准备出现未预期异常: id={}", id, ex);
                stateStore.markFailed(id, now, KnowledgeSourceStates.FAILURE_PREPARATION_FAILED,
                        message(ex));
            }
        }
        return claimed.size();
    }

    /** 处理单个知识来源（不持有数据库事务；状态转换经 StateStore 独立事务）。 */
    void processOne(UUID knowledgeSourceId, OffsetDateTime now) {
        KnowledgeSourceEntity entity = mapper.selectById(knowledgeSourceId);
        if (entity == null || !KnowledgeSourceStates.STATUS_PROCESSING.equals(entity.getStatus())) {
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
            stateStore.markFailed(knowledgeSourceId, now, KnowledgeSourceStates.FAILURE_FORMAT_UNSUPPORTED,
                    "该格式暂不支持作为知识来源: " + attachment.mediaType());
            return;
        }

        byte[] content = fileApi.readContent(householdId, fileId).orElse(null);
        if (content == null) {
            // 读取与查找之间附件被物理删除
            chunkMapper.deleteByAttachment(householdId, fileId);
            stateStore.deleteRow(knowledgeSourceId);
            return;
        }
        try {
            List<TextUnit> units = extractor.extract(attachment.mediaType(), content);
            if (units.isEmpty()) {
                stateStore.markFailed(knowledgeSourceId, now,
                        KnowledgeSourceStates.FAILURE_TEXT_NOT_EXTRACTABLE,
                        "未在文档中提取到文字，可能是扫描件或空文档");
                return;
            }
            List<Chunk> chunks = chunker.chunk(units);
            if (chunks.isEmpty()) {
                stateStore.markFailed(knowledgeSourceId, now,
                        KnowledgeSourceStates.FAILURE_TEXT_NOT_EXTRACTABLE,
                        "未在文档中提取到可嵌入的文字");
                return;
            }

            int nextVersion = (entity.getProcessingVersion() == null ? 0 : entity.getProcessingVersion()) + 1;
            var scope = scopeResolver.resolve(householdId, entity.getMountType(), entity.getMountId());
            var documents = documentFactory.build(attachment, entity, scope, chunks, nextVersion);
            try {
                chunkMapper.deleteByAttachment(householdId, fileId);
                vectorStore.add(documents);
                // 全部写入成功后整批翻转为 AVAILABLE（部分失败不残留可检索分块）
                chunkMapper.markAllAvailable(householdId, fileId);
            } catch (DataAccessException ex) {
                chunkMapper.deleteByAttachment(householdId, fileId);
                stateStore.markFailed(knowledgeSourceId, now,
                        KnowledgeSourceStates.FAILURE_INDEX_WRITE_FAILED, message(ex));
                return;
            } catch (RuntimeException ex) {
                chunkMapper.deleteByAttachment(householdId, fileId);
                stateStore.markFailed(knowledgeSourceId, now,
                        KnowledgeSourceStates.FAILURE_PROVIDER_UNAVAILABLE, message(ex));
                return;
            }
            int marked = stateStore.markAvailable(knowledgeSourceId, nextVersion, now);
            if (marked == 0) {
                // 处理期间被取消/回收：撤掉刚写入的分块
                chunkMapper.deleteByAttachment(householdId, fileId);
                log.info("知识来源在处理期间被停用，已撤销分块: fileId={}", fileId);
                return;
            }
            log.info("知识来源准备完成: fileId={} chunks={} version={}", fileId, documents.size(), nextVersion);
        } catch (KnowledgeExtractionException ex) {
            stateStore.markFailed(knowledgeSourceId, now,
                    KnowledgeSourceStates.FAILURE_EXTRACTION_FAILED, message(ex));
        } catch (RuntimeException ex) {
            stateStore.markFailed(knowledgeSourceId, now,
                    KnowledgeSourceStates.FAILURE_PREPARATION_FAILED, message(ex));
        }
    }

    private static String message(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
