import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../api/notification", () => ({
  fetchUnreadCount: vi.fn(),
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

import NotificationBell from "./NotificationBell.vue";
import {
  fetchUnreadCount,
  fetchNotifications,
  markAllNotificationsRead,
} from "../api/notification";

beforeEach(() => {
  vi.useFakeTimers();
  (fetchUnreadCount as vi.Mock).mockResolvedValue({ count: 3 });
  (fetchNotifications as vi.Mock).mockResolvedValue({
    items: [
      {
        id: "n1",
        scope: "TASK_CREATED",
        title: "T",
        message: null,
        sourceTaskId: null,
        read: false,
        createdAt: "x",
      },
    ],
    total: 1,
    page: 1,
    pageSize: 5,
  });
});

afterEach(() => vi.useRealTimers());

const mountBell = () =>
  mount(NotificationBell, {
    global: { plugins: [ElementPlus] },
  });

describe("NotificationBell", () => {
  it("loads unread count on mount and shows badge", async () => {
    const w = mountBell();
    await flushPromises();
    expect(fetchUnreadCount).toHaveBeenCalled();
    expect(w.text()).toContain("3");
  });

  it("polls every 30s", async () => {
    mountBell();
    await flushPromises();
    const calls1 = (fetchUnreadCount as vi.Mock).mock.calls.length;
    vi.advanceTimersByTime(30000);
    await flushPromises();
    expect((fetchUnreadCount as vi.Mock).mock.calls.length).toBeGreaterThan(
      calls1,
    );
  });

  it("cleans interval on unmount", async () => {
    const w = mountBell();
    await flushPromises();
    const calls = (fetchUnreadCount as vi.Mock).mock.calls.length;
    w.unmount();
    vi.advanceTimersByTime(60000);
    await flushPromises();
    expect((fetchUnreadCount as vi.Mock).mock.calls.length).toBe(calls);
  });
});
