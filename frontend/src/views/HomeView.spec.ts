import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { createRouter, createMemoryHistory } from "vue-router";

vi.mock("../api/reminder", () => ({
  fetchDashboard: vi.fn(),
  snoozeTask: vi.fn(),
  completeTask: vi.fn(),
  ignoreTask: vi.fn(),
}));
vi.mock("../api/inventory", () => ({
  fetchStocktakes: vi.fn(),
  fetchMovements: vi.fn(),
}));

import HomeView from "./HomeView.vue";
import { fetchDashboard } from "../api/reminder";
import { fetchStocktakes, fetchMovements } from "../api/inventory";

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: "/", component: HomeView }],
});

beforeEach(() => {
  (fetchDashboard as ReturnType<typeof vi.fn>).mockResolvedValue({
    expiryWithin7Days: { count: 12, items: [] },
    lowStockItems: { count: 5, items: [] },
    priorityTasks: {
      count: 23,
      items: [
        {
          taskId: "t1",
          kind: "EXPIRY",
          severity: "URGENT",
          title: "「牛奶」还有 3 天到期",
          dueAt: "2026-07-28T00:00:00Z",
          itemId: "i1",
          lotId: "l1",
        },
      ],
    },
    generatedAt: "2026-07-27T00:00:00Z",
  });
  (fetchStocktakes as ReturnType<typeof vi.fn>).mockResolvedValue({
    items: [],
    total: 2,
    page: 1,
    pageSize: 1,
  });
  (fetchMovements as ReturnType<typeof vi.fn>).mockResolvedValue({
    items: [
      {
        id: "m1",
        lotId: "l1",
        itemId: "i1",
        itemName: "牛奶",
        type: "INBOUND",
        quantity: "1",
        unitName: "盒",
        fromLocationId: null,
        fromLocationName: null,
        toLocationId: "loc1",
        toLocationName: "冰箱",
        reason: null,
        memo: null,
        operatorUsername: "admin",
        businessTime: "2026-07-27T10:00:00Z",
        createdAt: "2026-07-27T10:00:00Z",
        idempotencyKey: "k1",
        reversalOf: null,
        reversedBy: null,
      },
    ],
    total: 1,
    page: 1,
    pageSize: 10,
  });
});

const mountHome = () =>
  mount(HomeView, {
    global: {
      plugins: [ElementPlus, router],
    },
  });

describe("HomeView", () => {
  it("shows risk counts", async () => {
    const w = mountHome();
    await flushPromises();
    expect(w.text()).toContain("12");
    expect(w.text()).toContain("5");
    expect(w.text()).toContain("2");
  });

  it("renders priority task row", async () => {
    const w = mountHome();
    await flushPromises();
    expect(w.text()).toContain("「牛奶」还有 3 天到期");
  });

  it("renders recent movements", async () => {
    const w = mountHome();
    await flushPromises();
    expect(w.text()).toContain("入库");
  });
});
