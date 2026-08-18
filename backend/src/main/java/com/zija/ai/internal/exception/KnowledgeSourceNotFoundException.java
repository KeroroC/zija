package com.zija.ai.internal.exception;

/** 附件不存在、不属于当前家庭或从未被选择为知识来源。 */
public class KnowledgeSourceNotFoundException extends RuntimeException {

    public KnowledgeSourceNotFoundException(java.util.UUID fileId) {
        super("知识来源不存在: " + fileId);
    }
}
