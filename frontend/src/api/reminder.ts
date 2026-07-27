import { getJson, postJson, putJson } from "./http";

export interface ReminderRule {
  expiryDisabled: boolean;
  expiryReminderDays: number[];
  lowStockDisabled: boolean;
  lowStockThreshold: string;
  version: number;
}

export interface ReminderRuleUpdate {
  expiryDisabled: boolean;
  expiryReminderDays: number[];
  lowStockDisabled: boolean;
  lowStockThreshold: string;
  version: number;
}

export interface ReminderTask {
  id: string;
  kind: "EXPIRY" | "LOW_STOCK";
  lotId: string | null;
  itemId: string;
  status: "OPEN" | "SNOOZED" | "DONE" | "IGNORED";
  dueAt: string;
  severity: "INFO" | "WARN" | "URGENT";
  snoozedUntil: string | null;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface DashboardItem {
  taskId: string;
  kind: string;
  severity: string;
  title: string;
  dueAt: string;
  itemId: string;
  lotId: string | null;
}

export interface DashboardGroup {
  count: number;
  items: DashboardItem[];
}

export interface Dashboard {
  expiryWithin7Days: DashboardGroup;
  lowStockItems: DashboardGroup;
  priorityTasks: DashboardGroup;
  generatedAt: string;
}

export const fetchRules = () =>
  getJson<ReminderRule>("/api/v1/reminder/rules");

export const updateRules = (body: ReminderRuleUpdate) =>
  putJson<ReminderRule>("/api/v1/reminder/rules", body);

export const fetchTasks = (params: URLSearchParams) =>
  getJson<Page<ReminderTask>>(`/api/v1/reminder/tasks?${params.toString()}`);

export const snoozeTask = (id: string, until: string) =>
  postJson<void>(`/api/v1/reminder/tasks/${id}/snooze`, { until });

export const completeTask = (id: string) =>
  postJson<void>(`/api/v1/reminder/tasks/${id}/complete`, {});

export const ignoreTask = (id: string) =>
  postJson<void>(`/api/v1/reminder/tasks/${id}/ignore`, {});

export const reopenTask = (id: string) =>
  postJson<void>(`/api/v1/reminder/tasks/${id}/reopen`, {});

export const fetchDashboard = (days = 7, topN = 8) =>
  getJson<Dashboard>(`/api/v1/reminder/dashboard?days=${days}&topN=${topN}`);
