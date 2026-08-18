package com.zija.ai.internal.exception;

/** 附件媒体类型不属于首期可处理的知识来源格式（图片、HEIC、旧版 Office 等）。 */
public class KnowledgeSourceFormatUnsupportedException extends RuntimeException {

    public KnowledgeSourceFormatUnsupportedException(String mediaType) {
        super("该格式暂不支持作为知识来源: " + mediaType);
    }
}
