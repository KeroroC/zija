import { describe, it, expect, vi, beforeEach } from "vitest";
vi.mock("./http", () => ({ getJson: vi.fn(), postJson: vi.fn() }));
import { getJson, postJson } from "./http";
import { fetchNotifications, fetchUnreadCount, markNotificationRead, markAllNotificationsRead } from "./notification";

const g = getJson as vi.Mock; const p = postJson as vi.Mock;
beforeEach(() => { g.mockReset(); p.mockReset(); });

describe("notification api", () => {
  it("fetchNotifications params", async () => {
    g.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    await fetchNotifications(1, 20, true);
    expect(g).toHaveBeenCalledWith("/api/v1/notifications?page=1&pageSize=20&unreadOnly=true");
  });
  it("fetchUnreadCount", async () => {
    g.mockResolvedValue({ count: 5 });
    const r = await fetchUnreadCount();
    expect(g).toHaveBeenCalledWith("/api/v1/notifications/unread-count");
    expect(r.count).toBe(5);
  });
  it("markNotificationRead", async () => {
    p.mockResolvedValue(undefined);
    await markNotificationRead("n1");
    expect(p).toHaveBeenCalledWith("/api/v1/notifications/n1/read", {});
  });
  it("markAllNotificationsRead", async () => {
    p.mockResolvedValue(undefined);
    await markAllNotificationsRead();
    expect(p).toHaveBeenCalledWith("/api/v1/notifications/read-all", {});
  });
});
