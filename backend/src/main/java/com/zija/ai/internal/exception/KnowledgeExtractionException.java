package com.zija.ai.internal.exception;

/** 正文抽取失败（解析异常、损坏文件等）。 */
public class KnowledgeExtractionException extends RuntimeException {

    public KnowledgeExtractionException(String message) {
        super(message);
    }

    public KnowledgeExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
