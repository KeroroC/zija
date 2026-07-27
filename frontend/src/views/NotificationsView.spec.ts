import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../api/notification", () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

import NotificationsView from "./NotificationsView.vue";
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "../api/notification";

beforeEach(() => {
  vi.clearAllMocks();
  (fetchNotifications as ReturnType<typeof vi.fn>).mockResolvedValue({
    items: [
      {
        id: "n1",
        scope: "TASK_CREATED",
        title: "任务提醒",
        message: "有一个新任务",
        sourceTaskId: null,
        read: false,
        createdAt: "2026-07-27 10:00",
      },
    ],
    total: 1,
    page: 1,
    pageSize: 20,
  });
});

const mountN = () =>
  mount(NotificationsView, {
    global: { plugins: [ElementPlus] },
  });

describe("NotificationsView", () => {
  it("renders notifications", async () => {
    const w = mountN();
    await flushPromises();
    expect(w.text()).toContain("任务提醒");
    expect(w.text()).toContain("有一个新任务");
  });

  it("renders unread badge style for unread notifications", async () => {
    const w = mountN();
    await flushPromises();
    expect(w.find(".unread").exists()).toBe(true);
  });

  it("renders mark-read button for unread items", async () => {
    const w = mountN();
    await flushPromises();
    const btns = w.findAll("button");
    const readBtn = btns.find((b) => b.text().includes("标记已读"));
    expect(readBtn).toBeDefined();
  });

  it("calls markNotificationRead when marking single item", async () => {
    (markNotificationRead as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);
    const w = mountN();
    await flushPromises();
    const btns = w.findAll("button");
    const readBtn = btns.find((b) => b.text().includes("标记已读"));
    await readBtn!.trigger("click");
    await flushPromises();
    expect(markNotificationRead).toHaveBeenCalledWith("n1");
  });

  it("calls markAllNotificationsRead and reloads", async () => {
    (markAllNotificationsRead as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);
    const w = mountN();
    await flushPromises();
    await (w.vm as any).onReadAll();
    expect(markAllNotificationsRead).toHaveBeenCalled();
  });

  it("shows empty state when no notifications", async () => {
    (fetchNotifications as ReturnType<typeof vi.fn>).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    });
    const w = mountN();
    await flushPromises();
    expect(w.text()).toContain("暂无通知");
  });
});
