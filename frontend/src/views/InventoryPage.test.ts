import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import InventoryPage from "./InventoryPage.vue";

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({
    role: "OWNER",
    currentMember: {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "owner",
      displayName: "所有者",
      role: "OWNER",
      status: "ACTIVE",
    },
    logout: vi.fn(),
    clearLocalSession: vi.fn(),
  }),
}));

vi.mock("../api/inventory", () => ({
  fetchStockPositions: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 }),
  fetchLots: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 }),
  fetchMovements: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 }),
  fetchStocktakes: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 }),
  fetchStocktake: vi.fn().mockResolvedValue({ id: "s1", version: 1, status: "DRAFT", items: [] }),
  fetchLot: vi.fn().mockResolvedValue({}),
  inboundNewLot: vi.fn().mockResolvedValue({ lotId: "l1", serialDuplicated: false }),
  inboundExistingLot: vi.fn().mockResolvedValue({ lotId: "l1", serialDuplicated: false }),
  consumeStock: vi.fn().mockResolvedValue({}),
  lossStock: vi.fn().mockResolvedValue({}),
  transferStock: vi.fn().mockResolvedValue({}),
  createStocktake: vi.fn().mockResolvedValue({ id: "s1" }),
  updateStocktakeDraft: vi.fn().mockResolvedValue({ status: "DRAFT" }),
  refreshStocktakeDraft: vi.fn().mockResolvedValue({ status: "DRAFT" }),
  confirmStocktake: vi.fn().mockResolvedValue({ stocktakeId: "s1", adjustedCount: 0 }),
  cancelStocktake: vi.fn().mockResolvedValue({ status: "CANCELLED" }),
  reverseMovement: vi.fn().mockResolvedValue({ reversalMovementId: "m2", lotId: "l1" }),
  updateLotMeta: vi.fn().mockResolvedValue({}),
  fetchConsistencyReport: vi.fn().mockResolvedValue({ discrepancies: [], total: 0 }),
}));

vi.mock("../api/catalog", () => ({
  fetchItems: vi.fn().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 }),
  fetchUnits: vi.fn().mockResolvedValue([]),
}));

vi.mock("../api/location", () => ({
  fetchLocationTree: vi.fn().mockResolvedValue({ roots: [] }),
}));

vi.mock("../api/member", () => ({
  memberApi: {
    list: vi.fn().mockResolvedValue([]),
  },
}));

const routeState: { query: Record<string, string> } = { query: {} };
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useRoute: () => routeState,
}));

describe("InventoryPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    routeState.query = {};
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("renders four tabs", async () => {
    wrapper = mount(InventoryPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const tabs = wrapper.findAll(".el-tabs__item");
    expect(tabs).toHaveLength(4);
    expect(tabs[0].text()).toBe("当前库存");
    expect(tabs[1].text()).toBe("批次");
    expect(tabs[2].text()).toBe("流水");
    expect(tabs[3].text()).toBe("盘点");
  });

  it("renders action buttons", async () => {
    wrapper = mount(InventoryPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(wrapper.text()).toContain("入库");
    expect(wrapper.text()).toContain("领用");
    expect(wrapper.text()).toContain("报损");
    expect(wrapper.text()).toContain("移位");
    expect(wrapper.text()).toContain("发起盘点");
  });

  it("clicking 入库 button opens inbound dialog", async () => {
    wrapper = mount(InventoryPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const inboundBtn = wrapper.find('[data-testid="btn-inbound"]');
    expect(inboundBtn.exists()).toBe(true);
    await inboundBtn.trigger("click");
    await flushPromises();

    // Dialog should appear with title "入库"
    expect(wrapper.text()).toContain("入库");
  });

  it("shows default tab content (当前库存)", async () => {
    wrapper = mount(InventoryPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // The active tab pane should contain the CurrentStockTab content (filter bar)
    expect(wrapper.text()).toContain("筛选物品");
  });

  it("opens the consistency dialog when action=consistency", async () => {
    routeState.query = { action: "consistency" };
    wrapper = mount(InventoryPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(wrapper.text()).toContain("一致性检查");
  });

  it("clears the action query after opening a dialog", async () => {
    routeState.query = { action: "consistency" };
    wrapper = mount(InventoryPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(replaceMock).toHaveBeenCalledTimes(1);
    expect(replaceMock.mock.calls[0][0].query.action).toBeUndefined();
  });
});
