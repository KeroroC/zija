import { describe, it, expect, vi, beforeEach } from "vitest";
vi.mock("./http", () => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  putJson: vi.fn()
}));
import { getJson, postJson, putJson } from "./http";
import { fetchRules, updateRules, fetchTasks, snoozeTask, completeTask, ignoreTask, reopenTask, fetchDashboard } from "./reminder";

const getJsonMock = getJson as vi.Mock;
const postJsonMock = postJson as vi.Mock;
const putJsonMock = putJson as vi.Mock;

beforeEach(() => [getJsonMock, postJsonMock, putJsonMock].forEach(m => m.mockReset()));

describe("reminder api", () => {
  it("fetchRules GET rules", async () => {
    getJsonMock.mockResolvedValue({ expiryDisabled: false, expiryReminderDays: [30,7,1], lowStockDisabled: false, lowStockThreshold: "1", version: 0 });
    const r = await fetchRules();
    expect(getJsonMock).toHaveBeenCalledWith("/api/v1/reminder/rules");
    expect(r.expiryReminderDays).toEqual([30,7,1]);
  });

  it("updateRules PUT body", async () => {
    putJsonMock.mockResolvedValue({ version: 1 });
    await updateRules({ expiryDisabled: false, expiryReminderDays: [60], lowStockDisabled: false, lowStockThreshold: "2", version: 0 });
    expect(putJsonMock).toHaveBeenCalledWith("/api/v1/reminder/rules", expect.objectContaining({ version: 0 }));
  });

  it("fetchTasks passes query", async () => {
    getJsonMock.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 });
    const p = new URLSearchParams({ kind: "EXPIRY", status: "OPEN", page: "1", pageSize: "20" });
    await fetchTasks(p);
    expect(getJsonMock).toHaveBeenCalledWith(`/api/v1/reminder/tasks?${p.toString()}`);
  });

  it("snoozeTask posts ISO until", async () => {
    postJsonMock.mockResolvedValue(undefined);
    await snoozeTask("id1", "2026-12-31T00:00:00Z");
    expect(postJsonMock).toHaveBeenCalledWith("/api/v1/reminder/tasks/id1/snooze", { until: "2026-12-31T00:00:00Z" });
  });

  it("complete/ignore/reopen call right paths", async () => {
    postJsonMock.mockResolvedValue(undefined);
    await completeTask("t1"); expect(postJsonMock).toHaveBeenCalledWith("/api/v1/reminder/tasks/t1/complete", {});
    await ignoreTask("t1");   expect(postJsonMock).toHaveBeenCalledWith("/api/v1/reminder/tasks/t1/ignore", {});
    await reopenTask("t1");   expect(postJsonMock).toHaveBeenCalledWith("/api/v1/reminder/tasks/t1/reopen", {});
  });

  it("fetchDashboard days/topN params", async () => {
    getJsonMock.mockResolvedValue({ expiryWithin7Days: {count:0,items:[]}, lowStockItems: {count:0,items:[]}, priorityTasks: {count:0,items:[]}, generatedAt: "x" });
    await fetchDashboard(7, 8);
    expect(getJsonMock).toHaveBeenCalledWith("/api/v1/reminder/dashboard?days=7&topN=8");
  });
});
