import { getJson, postJson } from "./http";

export interface NotificationItem {
  id: string;
  scope: string;
  title: string;
  message: string | null;
  sourceTaskId: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  total: number;
  page: number;
  pageSize: number;
}

export const fetchNotifications = (page = 1, pageSize = 20, unreadOnly = false) =>
  getJson<NotificationPage>(`/api/v1/notifications?page=${page}&pageSize=${pageSize}&unreadOnly=${unreadOnly}`);

export const fetchUnreadCount = () =>
  getJson<{ count: number }>(`/api/v1/notifications/unread-count`);

export const markNotificationRead = (id: string) =>
  postJson<void>(`/api/v1/notifications/${id}/read`, {});

export const markAllNotificationsRead = () =>
  postJson<void>(`/api/v1/notifications/read-all`, {});
