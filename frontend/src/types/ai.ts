export interface AiSettings {
  enabled: boolean;
  providerId: string;
  credentialConfigured: boolean;
  outboundEnabled: boolean;
  requestsPerMinute: number;
  maxContextTokens: number;
  maxConcurrentRequests: number;
  requestTimeoutSeconds: number;
  version: number;
}

export interface AiStatus {
  available: boolean;
  reasonCode: string;
  detail: string;
  providerId: string;
  chatModel: string | null;
  embeddingModel: string | null;
  outboundEnabled: boolean;
  requestsPerMinute: number;
  maxContextTokens: number;
  maxConcurrentRequests: number;
  requestTimeoutSeconds: number;
}

export interface AiSettingsUpdate {
  enabled: boolean;
  providerId: string;
  credential?: string;
  clearCredential: boolean;
  outboundEnabled: boolean;
  requestsPerMinute: number;
  maxContextTokens: number;
  maxConcurrentRequests: number;
  requestTimeoutSeconds: number;
  version: number;
}

/** 知识来源准备状态（与后端 KnowledgeSourceStates 一致）。 */
export type KnowledgeSourceStatus = "PROCESSING" | "AVAILABLE" | "FAILED" | "DISABLED";

/** 知识来源停用原因。 */
export type KnowledgeSourceDisabledReason = "CANCELLED" | "RECYCLED";

/** 知识来源对外视图：按附件 id 与附件列表关联展示。 */
export interface KnowledgeSourceInfo {
  fileId: string;
  status: KnowledgeSourceStatus;
  failureCode?: string;
  failureMessage?: string;
  disabledReason?: KnowledgeSourceDisabledReason;
  /** 下次自动重试时间（仅失败且自动重试未耗尽时返回，用于感知待重试状态）。 */
  nextRetryAt?: string;
  processingVersion: number;
  selectedAt: string;
  processedAt?: string;
  updatedAt: string;
}

/** 首期可处理为知识来源的媒体类型（与后端 KnowledgeSourceStates 一致）。 */
export const KNOWLEDGE_SOURCE_MEDIA_TYPES: readonly string[] = [
  "application/pdf",
  "text/markdown",
  "text/plain",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation"
];

// ==================== 家庭事实问答 ====================

/** 家庭事实问题请求。 */
export interface HouseholdFactQuestion {
  question: string;
  scope?: QaQuestionScope;
  answerScope?: QaAnswerScope;
  pageContext?: QaQuestionScope;
  confirmedScopes?: QaQuestionScope[];
}

export type QaAnswerScope = "AUTO" | "HOUSEHOLD_FACT" | "KNOWLEDGE_SOURCE" | "BOTH";

export interface QaQuestionScope {
  type: "ITEM" | "LOT" | "LOCATION";
  id: string;
  label?: string;
}

export interface QaQuestionOptions {
  answerScope?: QaAnswerScope;
  scope?: QaQuestionScope;
  pageContext?: QaQuestionScope;
  confirmedScopes?: QaQuestionScope[];
}

/** 一组确定性结构化结果（行为列名 → 展示值）。 */
export interface QaStructuredResult {
  kind: string;
  title: string;
  rows: Array<Record<string, string>>;
}

/** 回答来源与数据时间。 */
export interface QaAnswerSource {
  category: string;
  label: string;
  dataTime: string;
  available: boolean;
  note?: string;
  attachmentId?: string;
  attachmentName?: string;
  attachmentUrl?: string;
  mountType?: "HOUSEHOLD" | "ITEM" | "LOT";
  mountId?: string;
  mountLabel?: string;
  pageNumber?: number;
  sectionPath?: string;
  excerpt?: string;
  charStart?: number;
  charEnd?: number;
}

/** 权威页面跳转。type ∈ ITEM / LOT / LOCATION / MOVEMENT / REMINDER / ATTACHMENT。 */
export interface QaJump {
  type: string;
  label: string;
  itemId?: string;
  lotId?: string;
  locationId?: string;
  attachmentId?: string;
}

export interface QaScopeCandidate {
  type: "ITEM" | "LOT" | "LOCATION";
  id: string;
  label: string;
  detail: string;
}

export interface QaAnswerPart {
  category: "HOUSEHOLD_FACT" | "KNOWLEDGE_SOURCE";
  label: string;
  reasonCode: string;
  summary: string;
  available: boolean;
}

export interface QaSourceConflict {
  kind: "QUANTITY" | "DATE" | "LOCATION" | "STATUS" | "UNIT";
  factValue: string;
  knowledgeValue: string;
  note: string;
}

/** 家庭事实问答答案。 */
export interface HouseholdFactAnswer {
  question: string;
  modelAvailable: boolean;
  reasonCode: string;
  summary: string;
  structuredResults: QaStructuredResult[];
  sources: QaAnswerSource[];
  jumps: QaJump[];
  dataTime: string;
  recommendedAnswerScope?: Exclude<QaAnswerScope, "AUTO">;
  usedAnswerScope?: Exclude<QaAnswerScope, "AUTO">;
  scopeReason?: string;
  targetScope?: QaQuestionScope;
  candidates?: QaScopeCandidate[];
  answerParts?: QaAnswerPart[];
  conflicts?: QaSourceConflict[];
}
