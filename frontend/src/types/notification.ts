// ==================== Notification ====================

export interface NotificationItem {
  id: string;
  scope: string;
  title: string;
  message: string | null;
  sourceTaskId: string | null;
  read: boolean;
  createdAt: string;
}
