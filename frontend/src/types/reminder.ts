import type { Page } from "./common";

// Re-export for consumers that import from this module
export type { Page };

// ==================== Reminder Rule ====================

export interface ReminderRule {
  expiryDisabled: boolean;
  expiryReminderDays: number[];
  lowStockDisabled: boolean;
  lowStockThreshold: string;
  version: number;
}

/** Same shape as ReminderRule — the update endpoint accepts the full rule body. */
export type ReminderRuleUpdate = ReminderRule;

// ==================== Reminder Task ====================

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

// ==================== Dashboard ====================

export interface DashboardItem {
  taskId: string;
  kind: ReminderTask["kind"];
  severity: ReminderTask["severity"];
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
