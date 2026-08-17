package com.zija.ai.internal.exception;

/** 知识来源当前状态不允许目标操作（重复处理中、已可用、已停用等）。 */
public class KnowledgeSourceStateConflictException extends RuntimeException {

    public KnowledgeSourceStateConflictException(String message) {
        super(message);
    }
}
