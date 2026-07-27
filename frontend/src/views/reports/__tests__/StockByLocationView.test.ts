import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../../../api/reporting", () => ({
  getReport: vi.fn(),
  buildExportUrl: vi.fn(),
}));

vi.mock("../../../api/catalog", () => ({
  fetchItems: vi.fn(),
  fetchCategories: vi.fn(),
}));

vi.mock("../../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}));

vi.mock("../../../stores/session", () => ({
  useSessionStore: () => ({
    role: "OWNER",
    currentMember: { householdId: "h1" },
  }),
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}));

import StockByLocationView from "../StockByLocationView.vue";
import { getReport, buildExportUrl } from "../../../api/reporting";
import { fetchItems, fetchCategories } from "../../../api/catalog";
import { fetchLocationTree } from "../../../api/location";

const mockGetReport = vi.mocked(getReport);
const mockBuildExportUrl = vi.mocked(buildExportUrl);
const mockFetchItems = vi.mocked(fetchItems);
const mockFetchCategories = vi.mocked(fetchCategories);
const mockFetchLocationTree = vi.mocked(fetchLocationTree);

const sampleRows = [
  {
    location_path: "家 > 储物间",
    item_name: "纸巾",
    lot_number: "LOT-001",
    serial_number: "",
    unit_name: "包",
    quantity: 5,
    expiry_date: null,
  },
  {
    location_path: "家 > 厨房",
    item_name: "洗洁精",
    lot_number: null,
    serial_number: "",
    unit_name: "瓶",
    quantity: 2,
    expiry_date: "2026-12-31",
  },
];

function defaultMocks() {
  mockFetchItems.mockResolvedValue({
    items: [
      { id: "i1", name: "纸巾" } as any,
      { id: "i2", name: "洗洁精" } as any,
    ],
    total: 2,
    page: 1,
    pageSize: 1000,
  });
  mockFetchLocationTree.mockResolvedValue({
    roots: [
      {
        id: "loc1",
        name: "储物间",
        parentId: null,
        sortOrder: 0,
        everReferenced: true,
        version: 1,
        children: [],
      },
    ],
  });
  mockFetchCategories.mockResolvedValue([
    {
      id: "c1",
      householdId: "h1",
      parentId: null,
      name: "日用品",
      status: "ACTIVE",
      sortOrder: 0,
      version: 1,
    },
  ]);
  mockGetReport.mockResolvedValue({
    items: sampleRows,
    total: 2,
    page: 1,
    pageSize: 20,
  });
  mockBuildExportUrl.mockReturnValue("/api/v1/reporting/exports/stock-by-location");
}

function mountV() {
  return mount(StockByLocationView, { global: { plugins: [ElementPlus] } });
}

describe("StockByLocationView", () => {
  let wrapper: VueWrapper | null = null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let openSpy: any;

  beforeEach(() => {
    vi.clearAllMocks();
    defaultMocks();
    openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    openSpy.mockRestore();
  });

  it("loads data on mount", async () => {
    wrapper = mountV();
    await flushPromises();

    expect(mockGetReport).toHaveBeenCalledWith("stock-by-location", {
      page: 1,
      pageSize: 20,
      itemId: undefined,
      categoryId: undefined,
      locationId: undefined,
    });
  });

  it("renders table rows", async () => {
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("纸巾");
    expect(wrapper.text()).toContain("洗洁精");
    expect(wrapper.text()).toContain("储物间");
  });

  it("filter change resets page to 1 and re-queries", async () => {
    wrapper = mountV();
    await flushPromises();

    // Clear initial call
    mockGetReport.mockClear();

    // Simulate filter change by calling the component's onFilter via the select change
    // The el-select @change="onFilter" sets page=1 then calls loadData
    const vm = wrapper.vm as any;
    vm.filters.itemId = "i1";
    vm.onFilter();
    await flushPromises();

    expect(mockGetReport).toHaveBeenCalledWith("stock-by-location", {
      page: 1,
      pageSize: 20,
      itemId: "i1",
      categoryId: undefined,
      locationId: undefined,
    });
  });

  it("pagination page change triggers re-query", async () => {
    wrapper = mountV();
    await flushPromises();
    mockGetReport.mockClear();

    const vm = wrapper.vm as any;
    vm.page = 2;
    vm.loadData();
    await flushPromises();

    expect(mockGetReport).toHaveBeenCalledWith("stock-by-location", {
      page: 2,
      pageSize: 20,
      itemId: undefined,
      categoryId: undefined,
      locationId: undefined,
    });
  });

  it("export button calls window.open for OWNER role", async () => {
    wrapper = mountV();
    await flushPromises();

    const exportBtn = wrapper.find(".page-header .el-button");
    expect(exportBtn.exists()).toBe(true);
    expect(exportBtn.text()).toContain("导出 CSV");

    await exportBtn.trigger("click");

    expect(mockBuildExportUrl).toHaveBeenCalledWith("stock-by-location", {
      itemId: undefined,
      categoryId: undefined,
      locationId: undefined,
      scope: "current-filter",
    });
    expect(openSpy).toHaveBeenCalledWith(
      "/api/v1/reporting/exports/stock-by-location",
      "_blank",
    );
  });

  it("renders pagination with total", async () => {
    mockGetReport.mockResolvedValue({
      items: sampleRows,
      total: 50,
      page: 1,
      pageSize: 20,
    });

    wrapper = mountV();
    await flushPromises();

    expect(wrapper.find(".el-pagination").exists()).toBe(true);
    expect(wrapper.text()).toContain("50");
  });

  it("loading flag is set during data fetch", async () => {
    let resolveGetReport: (v: any) => void;
    mockGetReport.mockImplementation(
      () => new Promise((resolve) => { resolveGetReport = resolve; }),
    );

    wrapper = mountV();
    await flushPromises();

    // The component's loading ref should be true while promise is pending
    expect((wrapper.vm as any).loading).toBe(true);

    resolveGetReport!({
      items: sampleRows,
      total: 2,
      page: 1,
      pageSize: 20,
    });
    await flushPromises();

    expect((wrapper.vm as any).loading).toBe(false);
  });
});
