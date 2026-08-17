package com.zija.ai.internal;

/**
 * 知识来源领域常量：状态、失败原因码、停用原因与处理参数。
 * 状态机：显式选择 → PROCESSING → AVAILABLE / FAILED；取消或附件回收 → DISABLED；
 * 附件恢复 → PROCESSING；FAILED 支持有限自动重试与手动重试。
 */
final class KnowledgeSourceStates {

    /** 处理中：尚未完成内容准备，不参与回答。 */
    static final String STATUS_PROCESSING = "PROCESSING";
    /** 可用：可以参与回答。 */
    static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 失败：处理未完成，可查看原因并重试。 */
    static final String STATUS_FAILED = "FAILED";
    /** 已停用：当前不参与回答（成员取消或附件生命周期触发）。 */
    static final String STATUS_DISABLED = "DISABLED";

    /** 停用原因：成员取消选定。 */
    static final String DISABLED_CANCELLED = "CANCELLED";
    /** 停用原因：附件进入回收站。 */
    static final String DISABLED_RECYCLED = "RECYCLED";

    /** 失败原因码：格式本身不支持作为知识来源（图片/HEIC/旧版 Office）。 */
    static final String FAILURE_FORMAT_UNSUPPORTED = "FORMAT_UNSUPPORTED";
    /** 失败原因码：可处理格式但抽取不到文字（扫描版 PDF 等）。 */
    static final String FAILURE_TEXT_NOT_EXTRACTABLE = "TEXT_NOT_EXTRACTABLE";
    /** 失败原因码：附件内容读取失败。 */
    static final String FAILURE_CONTENT_UNREADABLE = "CONTENT_UNREADABLE";
    /** 失败原因码：正文抽取过程异常。 */
    static final String FAILURE_EXTRACTION_FAILED = "EXTRACTION_FAILED";
    /** 失败原因码：模型提供方不可用或返回非法向量。 */
    static final String FAILURE_PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    /** 失败原因码：向量分块写入失败。 */
    static final String FAILURE_INDEX_WRITE_FAILED = "INDEX_WRITE_FAILED";
    /** 失败原因码：未预期的准备失败。 */
    static final String FAILURE_PREPARATION_FAILED = "PREPARATION_FAILED";

    /**
     * 自动重试预算：失败次数未达到该值时安排下一次退避重试，达到后停止自动重试
     * （即首轮处理失败后最多再自动重试 {@code MAX_AUTO_RETRIES - 1} 次；手动重试重置计数）。
     */
    static final int MAX_AUTO_RETRIES = 3;
    /** 自动重试退避基数（秒），第 n 次失败后等待 base &lt;&lt; n 秒。 */
    static final int RETRY_BACKOFF_BASE_SECONDS = 30;
    /** 处理认领租约（秒）：认领后到期仍未完成则由下轮扫描重新认领（进程崩溃兜底）。 */
    static final int PROCESSING_LEASE_SECONDS = 300;
    /** 每轮扫描最多认领的知识来源数。 */
    static final int CLAIM_BATCH_SIZE = 10;

    /** 首期可处理为知识来源的媒体类型。 */
    static final java.util.Set<String> SUPPORTED_MEDIA_TYPES = java.util.Set.of(
            "application/pdf",
            "text/markdown",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private KnowledgeSourceStates() {
    }
}
