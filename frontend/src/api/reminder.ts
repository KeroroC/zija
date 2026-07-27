import { getJson, postJson, putJson } from "./http";
import type {
  ReminderRule,
  ReminderRuleUpdate,
  ReminderTask,
  Page,
  Dashboard,
} from "../types/reminder";

// ==================== Rules ====================

export function fetchRules(): Promise<ReminderRule> {
  return getJson<ReminderRule>("/api/v1/reminder/rules");
}

export function updateRules(body: ReminderRuleUpdate): Promise<ReminderRule> {
  return putJson<ReminderRule>("/api/v1/reminder/rules", body);
}

// ==================== Tasks ====================

export function fetchTasks(params?: {
  kind?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}): Promise<Page<ReminderTask>> {
  const query = new URLSearchParams();
  if (params?.kind) query.set("kind", params.kind);
  if (params?.status) query.set("status", params.status);
  if (params?.page) query.set("page", String(params.page));
  if (params?.pageSize) query.set("pageSize", String(params.pageSize));
  const qs = query.toString();
  return getJson<Page<ReminderTask>>(
    `/api/v1/reminder/tasks${qs ? "?" + qs : ""}`,
  );
}

export function snoozeTask(id: string, until: string): Promise<void> {
  return postJson<void>(`/api/v1/reminder/tasks/${id}/snooze`, { until });
}

export function completeTask(id: string): Promise<void> {
  return postJson<void>(`/api/v1/reminder/tasks/${id}/complete`, {});
}

export function ignoreTask(id: string): Promise<void> {
  return postJson<void>(`/api/v1/reminder/tasks/${id}/ignore`, {});
}

export function reopenTask(id: string): Promise<void> {
  return postJson<void>(`/api/v1/reminder/tasks/${id}/reopen`, {});
}

// ==================== Dashboard ====================

export function fetchDashboard(days = 7, topN = 8): Promise<Dashboard> {
  return getJson<Dashboard>(
    `/api/v1/reminder/dashboard?days=${days}&topN=${topN}`,
  );
}
