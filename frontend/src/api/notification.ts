import { getJson, postJson } from "./http";
import type { NotificationItem } from "../types/notification";
import type { Page } from "../types/common";

export type { NotificationItem };

// ==================== Notifications ====================

export function fetchNotifications(
  page = 1,
  pageSize = 20,
  unreadOnly = false,
): Promise<Page<NotificationItem>> {
  return getJson<Page<NotificationItem>>(
    `/api/v1/notifications?page=${page}&pageSize=${pageSize}&unreadOnly=${unreadOnly}`,
  );
}

export function fetchUnreadCount(): Promise<{ count: number }> {
  return getJson<{ count: number }>("/api/v1/notifications/unread-count");
}

export function markNotificationRead(id: string): Promise<void> {
  return postJson<void>(`/api/v1/notifications/${id}/read`, {});
}

export function markAllNotificationsRead(): Promise<void> {
  return postJson<void>("/api/v1/notifications/read-all", {});
}
