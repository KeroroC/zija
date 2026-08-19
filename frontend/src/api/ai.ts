import { deleteJson, getJson, postJson, putJson } from "./http";
import type {
  AiSettings,
  AiSettingsUpdate,
  AiStatus,
  HouseholdFactAnswer,
  HouseholdFactQuestion,
  QaQuestionOptions,
  KnowledgeSourceInfo
} from "../types/ai";

export type {
  AiSettings,
  AiSettingsUpdate,
  AiStatus,
  HouseholdFactAnswer,
  HouseholdFactQuestion,
  KnowledgeSourceInfo
} from "../types/ai";

export function fetchAiSettings(): Promise<AiSettings> {
  return getJson<AiSettings>("/api/v1/ai/settings");
}

export function fetchAiStatus(): Promise<AiStatus> {
  return getJson<AiStatus>("/api/v1/ai/status");
}

export function updateAiSettings(body: AiSettingsUpdate): Promise<AiSettings> {
  return putJson<AiSettings>("/api/v1/ai/settings", body);
}

/** 统一问答：家庭由服务端推导；知识问答只传物品或批次范围，不传家庭 ID。 */
export function askHouseholdQuestion(
  question: string,
  options: QaQuestionOptions = {}
): Promise<HouseholdFactAnswer> {
  const body: HouseholdFactQuestion = { question, ...options };
  return postJson<HouseholdFactAnswer>("/api/v1/ai/qa", body);
}

/** 列出当前家庭全部知识来源（含处理状态与失败原因）。 */
export async function fetchKnowledgeSources(): Promise<KnowledgeSourceInfo[]> {
  const res = await getJson<{ items: KnowledgeSourceInfo[] }>("/api/v1/ai/knowledge-sources");
  return res.items;
}

/** 选择附件为知识来源（进入异步准备）。 */
export function selectKnowledgeSource(fileId: string): Promise<KnowledgeSourceInfo> {
  return putJson<KnowledgeSourceInfo>(`/api/v1/ai/knowledge-sources/${fileId}`);
}

/** 取消选定（已停用，不参与检索）。 */
export function cancelKnowledgeSource(fileId: string): Promise<KnowledgeSourceInfo> {
  return deleteJson<KnowledgeSourceInfo>(`/api/v1/ai/knowledge-sources/${fileId}`);
}

/** 手动重试失败的知识来源。 */
export function retryKnowledgeSource(fileId: string): Promise<KnowledgeSourceInfo> {
  return postJson<KnowledgeSourceInfo>(`/api/v1/ai/knowledge-sources/${fileId}/retry`);
}
