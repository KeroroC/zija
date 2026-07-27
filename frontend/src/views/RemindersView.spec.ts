import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { createRouter, createMemoryHistory } from "vue-router";
import { nextTick } from "vue";

vi.mock("../api/reminder", () => ({
  fetchTasks: vi.fn(),
  snoozeTask: vi.fn(),
  completeTask: vi.fn(),
  ignoreTask: vi.fn(),
  reopenTask: vi.fn(),
}));

import RemindersView from "./RemindersView.vue";
import { fetchTasks, reopenTask } from "../api/reminder";

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: "/reminders", component: RemindersView }],
});

beforeEach(() => {
  vi.clearAllMocks();
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

    // Click the "操作" dropdown trigger button
    const actionBtn = w.findAll("button").find((b) => b.text().includes("操作"));
    expect(actionBtn).toBeDefined();
    await actionBtn!.trigger("click");
    await nextTick();

    // Click the "重新打开" dropdown item
    const reopenItem = document.querySelector(
      ".el-dropdown-menu__item"
    );
    // Find the specific reopen item among all dropdown items
    const allItems = document.querySelectorAll(".el-dropdown-menu__item");
    const reopenEl = Array.from(allItems).find((el) =>
      el.textContent?.includes("重新打开")
    );
    expect(reopenEl).toBeDefined();
    (reopenEl as HTMLElement).click();
    await flushPromises();

    expect(reopenTask).toHaveBeenCalledWith("t1");
  });
});
