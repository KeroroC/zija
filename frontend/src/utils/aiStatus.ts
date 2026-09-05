/** AI 状态 reasonCode 的用户可见中文（与设置页语义一致，不展示英文码）。 */
export const AI_STATUS_REASON_LABELS: Record<string, string> = {
  AI_DISABLED: "已停用",
  PROVIDER_NOT_FOUND: "提供方不可用",
  OUTBOUND_DISABLED: "出网已关闭",
  CREDENTIAL_MISSING: "缺少凭据",
  CHAT_MODEL_MISSING: "聊天模型不可用",
  EMBEDDING_MODEL_MISSING: "Embedding 模型不可用",
  EMBEDDING_DIMENSION_MISMATCH: "Embedding 维度不匹配",
  PROVIDER_UNREACHABLE: "提供方不可达",
};

export function aiStatusReasonLabel(reasonCode: string): string {
  return AI_STATUS_REASON_LABELS[reasonCode] ?? "不可用";
}
