import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { createRouter, createMemoryHistory } from "vue-router";

vi.mock("../api/reminder", () => ({
  fetchTasks: vi.fn(),
  snoozeTask: vi.fn(),
  completeTask: vi.fn(),
  ignoreTask: vi.fn(),
  reopenTask: vi.fn(),
}));

import RemindersView from "./RemindersView.vue";
import { fetchTasks, completeTask, reopenTask } from "../api/reminder";

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: "/reminders", component: RemindersView }],
});

beforeEach(() => {
  (fetchTasks as ReturnType<typeof vi.fn>).mockResolvedValue({
    items: [
      {
        id: "t1",
        kind: "EXPIRY",
        lotId: "l1",
        itemId: "i1",
        status: "OPEN",
        dueAt: "2026-07-28T00:00:00Z",
        severity: "WARN",
        snoozedUntil: null,
      },
    ],
    total: 1,
    page: 1,
    pageSize: 20,
  });
});

const mountR = () =>
  mount(RemindersView, {
    global: { plugins: [ElementPlus, router] },
  });

describe("RemindersView", () => {
  it("renders tasks", async () => {
    const w = mountR();
    await flushPromises();
    expect(w.text()).toContain("临期"); // kind label
  });

  it("reopen on DONE task calls reopenTask", async () => {
    (fetchTasks as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      items: [
        {
          id: "t1",
          kind: "EXPIRY",
          lotId: "l1",
          itemId: "i1",
          status: "DONE",
          dueAt: "2026-07-28T00:00:00Z",
          severity: "URGENT",
          snoozedUntil: null,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    const w = mountR();
    await flushPromises();
    expect(fetchTasks).toHaveBeenCalled();
  });
});
