import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearCsrf } from "./http";
import {
  fetchRules,
  updateRules,
  fetchTasks,
  snoozeTask,
  completeTask,
  ignoreTask,
  reopenTask,
  fetchDashboard,
} from "./reminder";

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

describe("reminder API", () => {
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

  describe("fetchRules", () => {
    it("fetches reminder rules from GET /api/v1/reminder/rules", async () => {
      const rules = {
        expiryDisabled: false,
        expiryReminderDays: [30, 7, 1],
        lowStockDisabled: false,
        lowStockThreshold: "1",
        version: 0,
      };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(rules));
      vi.stubGlobal("fetch", fetchMock);

      const result = await fetchRules();
      expect(result).toEqual(rules);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/reminder/rules");
    });
  });

  describe("fetchTasks", () => {
    it("fetches tasks with default params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body));
      vi.stubGlobal("fetch", fetchMock);

      const result = await fetchTasks();
      expect(result).toEqual(body);
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/reminder/tasks");
    });

    it("passes query parameters for kind, status, page, pageSize", async () => {
      const body = { items: [], total: 0, page: 2, pageSize: 10 };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body));
      vi.stubGlobal("fetch", fetchMock);

      await fetchTasks({
        kind: "EXPIRY",
        status: "OPEN",
        page: 2,
        pageSize: 10,
      });
      const url = fetchMock.mock.calls[0][0] as string;
      expect(url).toContain("kind=EXPIRY");
      expect(url).toContain("status=OPEN");
      expect(url).toContain("page=2");
      expect(url).toContain("pageSize=10");
    });
  });

  describe("fetchDashboard", () => {
    it("fetches dashboard with default days and topN", async () => {
      const dashboard = {
        expiryWithin7Days: { count: 0, items: [] },
        lowStockItems: { count: 0, items: [] },
        priorityTasks: { count: 0, items: [] },
        generatedAt: "2026-07-27T00:00:00Z",
      };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(dashboard));
      vi.stubGlobal("fetch", fetchMock);

      const result = await fetchDashboard();
      expect(result).toEqual(dashboard);
      expect(fetchMock.mock.calls[0][0]).toBe(
        "/api/v1/reminder/dashboard?days=7&topN=8",
      );
    });

    it("passes custom days and topN", async () => {
      const dashboard = {
        expiryWithin7Days: { count: 0, items: [] },
        lowStockItems: { count: 0, items: [] },
        priorityTasks: { count: 0, items: [] },
        generatedAt: "2026-07-27T00:00:00Z",
      };
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(dashboard));
      vi.stubGlobal("fetch", fetchMock);

      await fetchDashboard(14, 5);
      expect(fetchMock.mock.calls[0][0]).toBe(
        "/api/v1/reminder/dashboard?days=14&topN=5",
      );
    });
  });

  // ==================== PUT endpoints ====================

  describe("updateRules", () => {
    it("puts to /api/v1/reminder/rules with rule body", async () => {
      const updated = {
        expiryDisabled: true,
        expiryReminderDays: [60],
        lowStockDisabled: false,
        lowStockThreshold: "2",
        version: 1,
      };
      const fetchMock = mockFetchWithCsrf(jsonResponse(updated));

      const result = await updateRules({
        expiryDisabled: true,
        expiryReminderDays: [60],
        lowStockDisabled: false,
        lowStockThreshold: "2",
        version: 0,
      });
      expect(result).toEqual(updated);

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/reminder/rules");
      expect(businessCall[1].method).toBe("PUT");
      expect(JSON.parse(businessCall[1].body)).toEqual({
        expiryDisabled: true,
        expiryReminderDays: [60],
        lowStockDisabled: false,
        lowStockThreshold: "2",
        version: 0,
      });
    });
  });

  // ==================== POST endpoints ====================

  describe("snoozeTask", () => {
    it("posts to /api/v1/reminder/tasks/{id}/snooze with until", async () => {
      const fetchMock = mockFetchWithCsrf(emptyResponse());

      await snoozeTask("task-1", "2026-12-31T00:00:00Z");

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/reminder/tasks/task-1/snooze");
      expect(businessCall[1].method).toBe("POST");
      expect(JSON.parse(businessCall[1].body)).toEqual({
        until: "2026-12-31T00:00:00Z",
      });
    });
  });

  describe("completeTask", () => {
    it("posts to /api/v1/reminder/tasks/{id}/complete", async () => {
      const fetchMock = mockFetchWithCsrf(emptyResponse());

      await completeTask("task-1");

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/reminder/tasks/task-1/complete");
      expect(businessCall[1].method).toBe("POST");
      expect(JSON.parse(businessCall[1].body)).toEqual({});
    });
  });

  describe("ignoreTask", () => {
    it("posts to /api/v1/reminder/tasks/{id}/ignore", async () => {
      const fetchMock = mockFetchWithCsrf(emptyResponse());

      await ignoreTask("task-1");

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/reminder/tasks/task-1/ignore");
      expect(businessCall[1].method).toBe("POST");
      expect(JSON.parse(businessCall[1].body)).toEqual({});
    });
  });

  describe("reopenTask", () => {
    it("posts to /api/v1/reminder/tasks/{id}/reopen", async () => {
      const fetchMock = mockFetchWithCsrf(emptyResponse());

      await reopenTask("task-1");

      const [, businessCall] = fetchMock.mock.calls;
      expect(businessCall[0]).toBe("/api/v1/reminder/tasks/task-1/reopen");
      expect(businessCall[1].method).toBe("POST");
      expect(JSON.parse(businessCall[1].body)).toEqual({});
    });
  });
});
