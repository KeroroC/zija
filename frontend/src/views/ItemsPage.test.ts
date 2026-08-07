import ElementPlus, { ElMessage } from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchItems,
  archiveItem,
  restoreItem,
  fetchCategories,
  fetchBrands,
  fetchUnits,
  fetchTags,
} from "../api/catalog";
import { fetchStockPositions, fetchLots } from "../api/inventory";
import { useSessionStore } from "../stores/session";
import ItemsPage from "./ItemsPage.vue";
import { ApiError } from "../api/http";
import type { CatalogItem, Category, Brand, Unit, Tag } from "../types/catalog";

vi.mock("../api/catalog", () => ({
  fetchItems: vi.fn(),
  archiveItem: vi.fn(),
  restoreItem: vi.fn(),
  fetchCategories: vi.fn(),
  fetchBrands: vi.fn(),
  fetchUnits: vi.fn(),
  fetchTags: vi.fn(),
}));

vi.mock("../api/inventory", () => ({
  fetchStockPositions: vi.fn(),
  fetchLots: vi.fn(),
}));

vi.mock("../stores/session", () => ({
  useSessionStore: vi.fn(),
}));

const pushMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
}));

const fetchItemsMock = vi.mocked(fetchItems);
const archiveItemMock = vi.mocked(archiveItem);
const restoreItemMock = vi.mocked(restoreItem);
const fetchCategoriesMock = vi.mocked(fetchCategories);
const fetchBrandsMock = vi.mocked(fetchBrands);
const fetchUnitsMock = vi.mocked(fetchUnits);
const fetchTagsMock = vi.mocked(fetchTags);
const fetchStockPositionsMock = vi.mocked(fetchStockPositions);
const fetchLotsMock = vi.mocked(fetchLots);

const category: Category = {
  id: "cat-1",
  householdId: "h1",
  parentId: null,
  name: "电子设备",
  status: "ACTIVE",
  sortOrder: 1,
  version: 1,
};

const brand: Brand = {
  id: "brand-1",
  householdId: "h1",
  name: "索尼",
  status: "ACTIVE",
  version: 1,
};

const unit: Unit = {
  id: "unit-1",
  householdId: "h1",
  name: "个",
  decimalScale: 0,
  status: "ACTIVE",
  version: 1,
};

const tag: Tag = {
  id: "tag-1",
  householdId: "h1",
  name: "重要",
  status: "ACTIVE",
  version: 1,
};

const tag2: Tag = {
  id: "tag-2",
  householdId: "h1",
  name: "常用",
  status: "ACTIVE",
  version: 1,
};

const activeItem: CatalogItem = {
  id: "item-1",
  householdId: "h1",
  name: "耳机",
  managementType: "DURABLE",
  categoryId: "cat-1",
  brandId: "brand-1",
  unitId: "unit-1",
  coverFileId: null,
  coverUrl: undefined,
  memo: null,
  expiryReminderMode: "INHERIT",
  expiryReminderDays: null,
  lowStockMode: "INHERIT",
  lowStockThreshold: null,
  status: "ACTIVE",
  tagIds: ["tag-1", "tag-2"],
  version: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const archivedItem: CatalogItem = {
  ...activeItem,
  id: "item-2",
  name: "旧手机",
  status: "ARCHIVED",
  tagIds: [],
};

const consumableItem: CatalogItem = {
  ...activeItem,
  id: "item-3",
  name: "电池",
  managementType: "CONSUMABLE",
  lowStockMode: "CUSTOM",
  lowStockThreshold: "5",
};

const itemsList = [activeItem, archivedItem, consumableItem];

describe("ItemsPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    fetchItemsMock.mockReset().mockResolvedValue({
      items: itemsList,
      total: 3,
      page: 1,
      pageSize: 20,
    });
    archiveItemMock.mockReset().mockResolvedValue(undefined);
    restoreItemMock.mockReset().mockResolvedValue(undefined);
    fetchCategoriesMock.mockReset().mockResolvedValue([category]);
    fetchBrandsMock.mockReset().mockResolvedValue([brand]);
    fetchUnitsMock.mockReset().mockResolvedValue([unit]);
    fetchTagsMock.mockReset().mockResolvedValue([tag, tag2]);
    fetchStockPositionsMock.mockReset().mockResolvedValue({
      items: [
        { lotId: "lot-1", locationId: "loc-1", itemName: "耳机", itemManagementType: "DURABLE", unitName: "个", quantity: "3", revision: 0, expiryDate: null, lotNumber: null, serialNumber: null, updatedAt: "2026-01-01T00:00:00Z" },
        { lotId: "lot-2", locationId: "loc-2", itemName: "耳机", itemManagementType: "DURABLE", unitName: "个", quantity: "2", revision: 0, expiryDate: null, lotNumber: null, serialNumber: null, updatedAt: "2026-01-01T00:00:00Z" },
      ],
      total: 2,
      page: 1,
      pageSize: 10000,
    });
    fetchLotsMock.mockReset().mockResolvedValue({
      items: [],
      total: 3,
      page: 1,
      pageSize: 1,
    });
    vi.mocked(useSessionStore).mockReturnValue({ role: "OWNER" } as any);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("loads items and renders the table", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalledOnce();
    const rows = wrapper.findAll("tbody tr");
    expect(rows).toHaveLength(3);
    expect(rows[0].text()).toContain("耳机");
    expect(rows[1].text()).toContain("旧手机");
    expect(rows[2].text()).toContain("电池");
  });

  it("shows the backend error message when loading items fails", async () => {
    const errorMessage = vi.spyOn(ElMessage, "error");
    fetchItemsMock.mockRejectedValueOnce(
      new ApiError("物品目录暂时不可用", "CATALOG_UNAVAILABLE", 503),
    );

    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(errorMessage).toHaveBeenCalledWith("物品目录暂时不可用");
  });

  it("loads dictionary data on mount", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Called by both ItemsPage and its child ItemFormDrawer
    expect(fetchCategoriesMock).toHaveBeenCalled();
    expect(fetchBrandsMock).toHaveBeenCalled();
    expect(fetchUnitsMock).toHaveBeenCalled();
    expect(fetchTagsMock).toHaveBeenCalled();
  });

  it("renders category, brand, unit, and tag names from dictionaries", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const firstRow = wrapper.findAll("tbody tr")[0];
    expect(firstRow.text()).toContain("电子设备");
    expect(firstRow.text()).toContain("索尼");
    expect(firstRow.text()).toContain("个");
    expect(firstRow.text()).toContain("重要");
    expect(firstRow.text()).toContain("常用");
  });

  it("displays consumption type tag for consumable items", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const rows = wrapper.findAll("tbody tr");
    expect(rows[2].text()).toContain("消耗品");
    expect(rows[2].text()).toContain("5");
  });

  it("re-fetches when category filter changes", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const treeSelect = wrapper.findComponent({ name: "ElTreeSelect" });
    await treeSelect.vm.$emit("change", "cat-1");
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches when brand filter changes", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    // Find the select that has brand options (value: brand-1)
    const brandSelect = wrapper.findAllComponents({ name: "ElSelect" })[2];
    await brandSelect.vm.$emit("change", "brand-1");
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches when tag filter changes", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const tagSelect = wrapper.findAllComponents({ name: "ElSelect" })[3];
    await tagSelect.vm.$emit("change", "tag-1");
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches when sort filter changes", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const sortSelect = wrapper.findAllComponents({ name: "ElSelect" })[4];
    await sortSelect.vm.$emit("change", "name,asc");
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches when managementType filter changes", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const managementSelect = wrapper.findAllComponents({ name: "ElSelect" })[0];
    await managementSelect.vm.$emit("change", "CONSUMABLE");
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches when status filter changes", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const statusSelect = wrapper.findAllComponents({ name: "ElSelect" })[1];
    await statusSelect.vm.$emit("change", "ARCHIVED");
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches on pagination page change", async () => {
    fetchItemsMock.mockResolvedValue({
      items: itemsList,
      total: 50,
      page: 1,
      pageSize: 20,
    });
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const pagination = wrapper.findComponent({ name: "ElPagination" });
    pagination.vm.$emit("current-change", 2);
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("re-fetches on pagination page-size change", async () => {
    fetchItemsMock.mockResolvedValue({
      items: itemsList,
      total: 50,
      page: 1,
      pageSize: 20,
    });
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    fetchItemsMock.mockClear();
    const pagination = wrapper.findComponent({ name: "ElPagination" });
    pagination.vm.$emit("size-change", 50);
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
  });

  it("opens detail drawer on row click", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const firstRow = wrapper.findAll("tbody tr")[0];
    await firstRow.trigger("click");
    await flushPromises();

    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    expect(drawer.exists()).toBe(true);
    expect(drawer.text()).toContain("耳机");
    expect(drawer.text()).toContain("耐用品");
    expect(drawer.text()).toContain("电子设备");
    expect(drawer.text()).toContain("索尼");
  });

  it("displays tags in the detail drawer", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const firstRow = wrapper.findAll("tbody tr")[0];
    await firstRow.trigger("click");
    await flushPromises();

    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    expect(drawer.text()).toContain("重要");
    expect(drawer.text()).toContain("常用");
  });

  it("shows archive button for active items and restore button for archived items in drawer", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Open active item
    const firstRow = wrapper.findAll("tbody tr")[0];
    await firstRow.trigger("click");
    await flushPromises();

    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    const buttons = drawer.findAll(".el-button");
    const archiveBtn = buttons.find((b) => b.text().includes("归档"));
    const restoreBtn = buttons.find((b) => b.text().includes("恢复"));
    expect(archiveBtn).toBeTruthy();
    expect(restoreBtn).toBeFalsy();

    // Open archived item
    const secondRow = wrapper.findAll("tbody tr")[1];
    await secondRow.trigger("click");
    await flushPromises();

    const drawer2 = wrapper.findComponent({ name: "ElDrawer" });
    const buttons2 = drawer2.findAll(".el-button");
    const archiveBtn2 = buttons2.find((b) => b.text().includes("归档"));
    const restoreBtn2 = buttons2.find((b) => b.text().includes("恢复"));
    expect(archiveBtn2).toBeFalsy();
    expect(restoreBtn2).toBeTruthy();
  });

  it("calls archiveItem after confirmation dialog", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Open active item detail drawer
    const firstRow = wrapper.findAll("tbody tr")[0];
    await firstRow.trigger("click");
    await flushPromises();

    // Click archive button
    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    const archiveBtn = drawer.findAll(".el-button").find((b) => b.text().includes("归档"))!;
    await archiveBtn.trigger("click");
    await flushPromises();

    // ElMessageBox.confirm pops up — click the confirm button
    const messageBox = document.querySelector(".el-message-box");
    expect(messageBox).toBeTruthy();
    const confirmBtn = messageBox!.querySelector(".el-button--primary") as HTMLButtonElement;
    confirmBtn.click();
    await flushPromises();

    expect(archiveItemMock).toHaveBeenCalledWith("item-1", 1);
    expect(fetchItemsMock).toHaveBeenCalledTimes(2); // initial + after archive
  });

  it("calls restoreItem when restore button is clicked", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Open archived item detail drawer
    const secondRow = wrapper.findAll("tbody tr")[1];
    await secondRow.trigger("click");
    await flushPromises();

    // Click restore button
    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    const restoreBtn = drawer.findAll(".el-button").find((b) => b.text().includes("恢复"))!;
    await restoreBtn.trigger("click");
    await flushPromises();

    expect(restoreItemMock).toHaveBeenCalledWith("item-2", 1);
    expect(fetchItemsMock).toHaveBeenCalledTimes(2); // initial + after restore
  });

  it("MEMBER can see the create button", async () => {
    vi.mocked(useSessionStore).mockReturnValue({ role: "MEMBER" } as any);

    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const createBtn = wrapper.findAll(".el-button").find((b) => b.text().includes("新建物品"));
    expect(createBtn).toBeTruthy();
  });

  it("displays cover image when coverUrl is present", async () => {
    const itemWithCover: CatalogItem = {
      ...activeItem,
      id: "item-cover",
      coverUrl: "https://example.com/cover.jpg",
    };
    fetchItemsMock.mockResolvedValue({
      items: [itemWithCover],
      total: 1,
      page: 1,
      pageSize: 20,
    });

    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const img = wrapper.find("tbody .cover-thumb");
    expect(img.exists()).toBe(true);
    expect(img.attributes("src")).toBe("https://example.com/cover.jpg");
  });

  it("displays placeholder when coverUrl is absent", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const placeholder = wrapper.find("tbody .cover-placeholder");
    expect(placeholder.exists()).toBe(true);
    expect(placeholder.text()).toBe("—");
  });

  it("displays memo and expiry reminder info in detail drawer", async () => {
    const detailedItem: CatalogItem = {
      ...activeItem,
      id: "item-detail",
      memo: "测试备注",
      expiryReminderMode: "CUSTOM",
      expiryReminderDays: [30, 7],
      lowStockMode: "CUSTOM",
      lowStockThreshold: "10",
    };
    fetchItemsMock.mockResolvedValue({
      items: [detailedItem],
      total: 1,
      page: 1,
      pageSize: 20,
    });

    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const row = wrapper.findAll("tbody tr")[0];
    await row.trigger("click");
    await flushPromises();

    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    expect(drawer.text()).toContain("测试备注");
    expect(drawer.text()).toContain("CUSTOM");
    expect(drawer.text()).toContain("30");
    expect(drawer.text()).toContain("7");
    expect(drawer.text()).toContain("阈值");
    expect(drawer.text()).toContain("10");
  });

  it("shows inventory summary and 入库 button in detail drawer", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const firstRow = wrapper.findAll("tbody tr")[0];
    await firstRow.trigger("click");
    await flushPromises();

    const drawer = wrapper.findComponent({ name: "ElDrawer" });
    expect(drawer.text()).toContain("库存总量");
    expect(drawer.text()).toContain("5");
    expect(drawer.text()).toContain("批次数");
    expect(drawer.text()).toContain("3");

    const inboundBtn = drawer.findAll(".el-button").find((b) => b.text().includes("入库"));
    expect(inboundBtn).toBeTruthy();

    await inboundBtn!.trigger("click");
    await flushPromises();

    expect(pushMock).toHaveBeenCalledWith({ name: "inventory", query: { action: "inbound", itemId: "item-1" } });
  });
});
