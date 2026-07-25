import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchStockPositions, fetchMovements } from "../../api/inventory";
import { fetchItems } from "../../api/catalog";
import { fetchLocationTree } from "../../api/location";
import CurrentStockTab from "./CurrentStockTab.vue";
import type {
  StockPosition,
  StockPositionListResponse,
  Movement,
  MovementListResponse,
} from "../../types/inventory";
import type { ItemListResponse } from "../../types/catalog";
import type { LocationTree } from "../../types/location";

vi.mock("../../api/inventory", () => ({
  fetchStockPositions: vi.fn(),
  fetchMovements: vi.fn(),
}));

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
}));

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("../../stores/session", () => ({
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

const fetchStockPositionsMock = vi.mocked(fetchStockPositions);
const fetchMovementsMock = vi.mocked(fetchMovements);
const fetchItemsMock = vi.mocked(fetchItems);
const fetchLocationTreeMock = vi.mocked(fetchLocationTree);

const stockRow: StockPosition = {
  lotId: "lot-1",
  locationId: "loc-1",
  itemName: "洗衣液",
  itemManagementType: "CONSUMABLE",
  unitName: "瓶",
  quantity: "5",
  revision: 1,
  expiryDate: "2026-12-31",
  lotNumber: "LOT-001",
  serialNumber: null,
  updatedAt: "2026-07-20T10:00:00Z",
};

const stockRow2: StockPosition = {
  lotId: "lot-2",
  locationId: "loc-2",
  itemName: "毛巾",
  itemManagementType: "DURABLE",
  unitName: "条",
  quantity: "10",
  revision: 1,
  expiryDate: null,
  lotNumber: null,
  serialNumber: null,
  updatedAt: "2026-07-21T12:00:00Z",
};

const stockResponse: StockPositionListResponse = {
  items: [stockRow, stockRow2],
  total: 2,
  page: 1,
  pageSize: 20,
};

const emptyStockResponse: StockPositionListResponse = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 20,
};

const itemsResponse: ItemListResponse = {
  items: [
    {
      id: "item-1",
      householdId: "h1",
      name: "洗衣液",
      managementType: "CONSUMABLE",
      categoryId: null,
      brandId: null,
      unitId: "unit-1",
      coverFileId: null,
      memo: null,
      expiryReminderMode: "INHERIT",
      expiryReminderDays: null,
      lowStockMode: "INHERIT",
      lowStockThreshold: null,
      status: "ACTIVE",
      tagIds: [],
      version: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    {
      id: "item-2",
      householdId: "h1",
      name: "毛巾",
      managementType: "DURABLE",
      categoryId: null,
      brandId: null,
      unitId: "unit-2",
      coverFileId: null,
      memo: null,
      expiryReminderMode: "INHERIT",
      expiryReminderDays: null,
      lowStockMode: "INHERIT",
      lowStockThreshold: null,
      status: "ACTIVE",
      tagIds: [],
      version: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ],
  total: 2,
  page: 1,
  pageSize: 20,
};

const locationTree: LocationTree = {
  roots: [
    {
      id: "loc-1",
      parentId: null,
      name: "家",
      sortOrder: 0,
      everReferenced: true,
      version: 1,
      children: [
        {
          id: "loc-2",
          parentId: "loc-1",
          name: "卧室",
          sortOrder: 1,
          everReferenced: false,
          version: 1,
          children: [],
        },
      ],
    },
  ],
};

const movement: Movement = {
  id: "mov-1",
  lotId: "lot-1",
  itemId: "item-1",
  itemName: "洗衣液",
  type: "INBOUND",
  quantity: "5",
  unitName: "瓶",
  fromLocationId: null,
  fromLocationName: null,
  toLocationId: "loc-1",
  toLocationName: "家",
  reason: null,
  memo: "首批入库",
  operatorUsername: "owner",
  businessTime: "2026-07-20T10:00:00Z",
  createdAt: "2026-07-20T10:00:00Z",
  idempotencyKey: "key-1",
  reversalOf: null,
  reversedBy: null,
};

const movementResponse: MovementListResponse = {
  items: [movement],
  total: 1,
  page: 1,
  pageSize: 20,
};

describe("CurrentStockTab", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    fetchItemsMock.mockReset().mockResolvedValue(itemsResponse);
    fetchLocationTreeMock.mockReset().mockResolvedValue(locationTree);
    fetchStockPositionsMock.mockReset().mockResolvedValue(stockResponse);
    fetchMovementsMock.mockReset().mockResolvedValue(movementResponse);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  async function mountAndWait() {
    wrapper = mount(CurrentStockTab, { global: { plugins: [ElementPlus] } });
    await flushPromises();
  }

  it("renders table with stock data on mount", async () => {
    await mountAndWait();

    expect(fetchStockPositionsMock).toHaveBeenCalledOnce();
    expect(wrapper!.text()).toContain("洗衣液");
    expect(wrapper!.text()).toContain("LOT-001");
    expect(wrapper!.text()).toContain("5 瓶");
    expect(wrapper!.text()).toContain("毛巾");
  });

  it("loads item and location name maps on mount", async () => {
    await mountAndWait();

    expect(fetchItemsMock).toHaveBeenCalledOnce();
    expect(fetchLocationTreeMock).toHaveBeenCalledOnce();
  });

  it("displays location names from the tree", async () => {
    await mountAndWait();

    // loc-1 is "家", loc-2 is "卧室"
    expect(wrapper!.text()).toContain("家");
    expect(wrapper!.text()).toContain("卧室");
  });

  it("displays expiry date and dash for null", async () => {
    await mountAndWait();

    expect(wrapper!.text()).toContain("2026-12-31");
    // row2 has null expiryDate, should show dash
    const cells = wrapper!.findAll(".el-table__body td");
    const expiryCells = cells.filter(
      (c) => c.text() === "-" || c.text().includes("2026-"),
    );
    expect(expiryCells.length).toBeGreaterThanOrEqual(2);
  });

  it("filters by item when item select changes", async () => {
    await mountAndWait();

    fetchStockPositionsMock.mockResolvedValueOnce({
      items: [stockRow],
      total: 1,
      page: 1,
      pageSize: 20,
    });

    // Find the first el-select (item filter) and trigger change
    const selects = wrapper!.findAllComponents({ name: "ElSelect" });
    expect(selects.length).toBeGreaterThanOrEqual(2);
    await selects[0].vm.$emit("update:modelValue", "item-1");
    await selects[0].vm.$emit("change", "item-1");
    await flushPromises();

    expect(fetchStockPositionsMock).toHaveBeenCalledTimes(2);
    const lastCall =
      fetchStockPositionsMock.mock.calls[
        fetchStockPositionsMock.mock.calls.length - 1
      ][0];
    expect(lastCall?.itemId).toBe("item-1");
  });

  it("filters by location when location select changes", async () => {
    await mountAndWait();

    fetchStockPositionsMock.mockResolvedValueOnce({
      items: [stockRow2],
      total: 1,
      page: 1,
      pageSize: 20,
    });

    const selects = wrapper!.findAllComponents({ name: "ElSelect" });
    expect(selects.length).toBeGreaterThanOrEqual(2);
    await selects[1].vm.$emit("update:modelValue", "loc-2");
    await selects[1].vm.$emit("change", "loc-2");
    await flushPromises();

    expect(fetchStockPositionsMock).toHaveBeenCalledTimes(2);
    const lastCall =
      fetchStockPositionsMock.mock.calls[
        fetchStockPositionsMock.mock.calls.length - 1
      ][0];
    expect(lastCall?.locationId).toBe("loc-2");
  });

  it("opens drawer with movements when row is clicked", async () => {
    await mountAndWait();

    const rows = wrapper!.findAll(".el-table__body .el-table__row");
    expect(rows.length).toBeGreaterThan(0);
    await rows[0].trigger("click");
    await flushPromises();

    expect(fetchMovementsMock).toHaveBeenCalledWith({
      lotId: "lot-1",
      page: 1,
      pageSize: 20,
    });
    expect(wrapper!.text()).toContain("近期流水");
    expect(wrapper!.text()).toContain("入库");
    expect(wrapper!.text()).toContain("首批入库");
  });

  it("shows empty state when no stock positions", async () => {
    fetchStockPositionsMock.mockResolvedValueOnce(emptyStockResponse);

    await mountAndWait();

    const rows = wrapper!.findAll(".el-table__body .el-table__row");
    expect(rows).toHaveLength(0);
  });

  it("renders pagination", async () => {
    fetchStockPositionsMock.mockResolvedValueOnce({
      items: [stockRow],
      total: 50,
      page: 1,
      pageSize: 20,
    });

    await mountAndWait();

    expect(wrapper!.find(".el-pagination").exists()).toBe(true);
    expect(wrapper!.text()).toContain("50");
  });
});
