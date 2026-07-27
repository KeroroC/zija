import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearCsrf } from "./http";
import {
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
} from "./notification";

function jsonResponse(body: unknown, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

function emptyResponse(): Response {
  return new Response(null, { status: 204 });
}

describe("notification API", () => {
  beforeEach(() => {
    clearCsrf();
  });

  afterEach(() => {
    clearCsrf();
    vi.unstubAllGlobals();
  });

  function mockFetch(responses: Response[]) {
    const fetchMock = vi.fn();
    for (const res of responses) {
      fetchMock.mockResolvedValueOnce(res);
    }
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
  }

  function mockFetchWithCsrf(businessResponse: Response) {
    return mockFetch([
      jsonResponse({ token: "csrf-token" }),
      businessResponse,
    ]);
  }

  // ==================== GET endpoints ====================

  describe("fetchNotifications", () => {
    it("fetches notifications with default params", async () => {
      const page = { items: [], total: 0, page: 1, pageSize: 20 };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
      vi.stubGlobal("fetch", fetchMock);

      const result = await fetchNotifications();
      expect(result).toEqual(page);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe(
        "/api/v1/notifications?page=1&pageSize=20&unreadOnly=false",
      );
    });

    it("passes custom page, pageSize, and unreadOnly params", async () => {
      const page = { items: [], total: 5, page: 2, pageSize: 10 };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(page));
      vi.stubGlobal("fetch", fetchMock);

      await fetchNotifications(2, 10, true);
      const url = fetchMock.mock.calls[0][0] as string;
      expect(url).toContain("page=2");
      expect(url).toContain("pageSize=10");
      expect(url).toContain("unreadOnly=true");
    });
  });

  describe("fetchUnreadCount", () => {
    it("fetches unread count from GET /api/v1/notifications/unread-count", async () => {
      const countResponse = { count: 5 };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(countResponse));
      vi.stubGlobal("fetch", fetchMock);

      const result = await fetchUnreadCount();
      expect(result).toEqual(countResponse);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe(
        "/api/v1/notifications/unread-count",
      );
    });
  });

  // ==================== POST endpoints ====================

  describe("markNotificationRead", () => {
    it("posts to /api/v1/notifications/{id}/read", async () => {
      const fetchMock = mockFetchWithCsrf(emptyResponse());

      await markNotificationRead("notif-1");

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/notifications/notif-1/read");
      expect(businessCall[1].method).toBe("POST");
      expect(JSON.parse(businessCall[1].body)).toEqual({});
    });
  });

  describe("markAllNotificationsRead", () => {
    it("posts to /api/v1/notifications/read-all", async () => {
      const fetchMock = mockFetchWithCsrf(emptyResponse());

      await markAllNotificationsRead();

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/notifications/read-all");
      expect(businessCall[1].method).toBe("POST");
      expect(JSON.parse(businessCall[1].body)).toEqual({});
    });
  });
});
