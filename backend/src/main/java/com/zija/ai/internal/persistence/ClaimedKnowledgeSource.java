package com.zija.ai.internal.persistence;

import java.util.UUID;

/**
 * 认领结果：被认领的知识来源 id 与本次认领递增后的处理版本号（栅栏令牌）。
 *
 * <p>认领时 {@code processing_version} 原子自增，后续所有状态写入与分块变更都携带
 * 该版本作并发栅栏：租约到期被更高版本认领接管后，过期工作者的写入全部落空，
 * 不会覆盖现任认领者或误删其分块。</p>
 */
public class ClaimedKnowledgeSource {

    private UUID id;
    private Integer processingVersion;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getProcessingVersion() { return processingVersion; }
    public void setProcessingVersion(Integer processingVersion) { this.processingVersion = processingVersion; }
}
