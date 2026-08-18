package com.zija.ai.internal;

/**
 * 首期可作为知识来源处理的附件格式：媒体类型词汇的唯一来源。
 * 选择校验（{@link KnowledgeSourceStates#SUPPORTED_MEDIA_TYPES}）与
 * 正文抽取路由（{@code KnowledgeTextExtractor}）都从这里取值，避免字符串散落漂移。
 */
enum KnowledgeSourceFormat {

    PDF("application/pdf"),
    MARKDOWN("text/markdown"),
    PLAIN("text/plain"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final String mediaType;

    KnowledgeSourceFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    String mediaType() {
        return mediaType;
    }

    /** 按媒体类型解析；不在首期支持范围内返回 {@code null}。 */
    static KnowledgeSourceFormat fromMediaType(String mediaType) {
        for (KnowledgeSourceFormat format : values()) {
            if (format.mediaType.equals(mediaType)) {
                return format;
            }
        }
        return null;
    }
}
