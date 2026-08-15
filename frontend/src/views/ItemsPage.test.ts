import ElementPlus, { ElMessage, ElMessageBox } from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchItems,
  fetchItem,
  archiveItem,
  restoreItem,
  fetchCategories,
  fetchBrands,
  fetchUnits,
  fetchTags,
} from "../api/catalog";
import { fetchStockPositions, fetchLots } from "../api/inventory";
import {
  listItemAttachments,
  deleteAttachment,
  designateItemCover,
  remountAttachmentToHousehold,
  type Attachment,
} from "../api/file";
import { useSessionStore } from "../stores/session";
import ItemsPage from "./ItemsPage.vue";
import { ApiError } from "../api/http";
import type { CatalogItem, Category, Brand, Unit, Tag } from "../types/catalog";

vi.mock("../api/catalog", () => ({
  fetchItems: vi.fn(),
  fetchItem: vi.fn(),
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

vi.mock("../api/file", () => ({
  COVER_IMAGE_TYPES: ["image/jpeg", "image/png", "image/webp"],
  listItemAttachments: vi.fn(),
  uploadItemAttachment: vi.fn(),
  renameAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
  designateItemCover: vi.fn(),
  remountAttachmentToHousehold: vi.fn(),
}));

vi.mock("../stores/session", () => ({
  useSessionStore: vi.fn(),
}));

const pushMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: {} }),
}));

const fetchItemsMock = vi.mocked(fetchItems);
const fetchItemMock = vi.mocked(fetchItem);
const archiveItemMock = vi.mocked(archiveItem);
const restoreItemMock = vi.mocked(restoreItem);
const fetchCategoriesMock = vi.mocked(fetchCategories);
const fetchBrandsMock = vi.mocked(fetchBrands);
const fetchUnitsMock = vi.mocked(fetchUnits);
const fetchTagsMock = vi.mocked(fetchTags);
const fetchStockPositionsMock = vi.mocked(fetchStockPositions);
const fetchLotsMock = vi.mocked(fetchLots);
const listItemAttachmentsMock = vi.mocked(listItemAttachments);
const deleteAttachmentMock = vi.mocked(deleteAttachment);
const designateItemCoverMock = vi.mocked(designateItemCover);
const remountAttachmentMock = vi.mocked(remountAttachmentToHousehold);

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
    listItemAttachmentsMock.mockReset().mockResolvedValue([
      {
        id: "f1",
        name: "铭牌.jpg",
        mediaType: "image/jpeg",
        byteSize: 2048,
        mountType: "ITEM",
        mountId: "item-1",
        createdAt: "2026-08-15T10:00:00Z",
        url: "/api/v1/files/f1/content",
      },
      {
        id: "f2",
        name: "说明书.pdf",
        mediaType: "application/pdf",
        byteSize: 100,
        mountType: "ITEM",
        mountId: "item-1",
        createdAt: "2026-08-15T10:00:00Z",
        url: "/api/v1/files/f2/content",
      },
    ] as Attachment[]);
    deleteAttachmentMock.mockReset().mockResolvedValue({} as Attachment);
    remountAttachmentMock.mockReset().mockResolvedValue({} as Attachment);
    fetchItemMock.mockReset().mockResolvedValue(activeItem as never);
    designateItemCoverMock.mockReset().mockResolvedValue({
      id: "f1",
      name: "铭牌.jpg",
      mediaType: "image/jpeg",
      byteSize: 2048,
      mountType: "ITEM",
      mountId: "item-1",
      createdAt: "2026-08-15T10:00:00Z",
      url: "/api/v1/files/f1/content",
      version: 2,
    } as never);
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
    expect(drawer.text()).toContain("自定义");
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

  // ==================== 附件与封面指定 ====================

  async function openItemDetail() {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const firstRow = wrapper.findAll("tbody tr")[0];
    await firstRow.trigger("click");
    await flushPromises();
    return wrapper.findComponent({ name: "ElDrawer" });
  }

  it("shows item attachments in the detail drawer", async () => {
    const drawer = await openItemDetail();
    expect(listItemAttachmentsMock).toHaveBeenCalledWith("item-1");
    expect(drawer.text()).toContain("铭牌.jpg");
    expect(drawer.text()).toContain("说明书.pdf");
  });

  it("designates an eligible image attachment as cover", async () => {
    const successSpy = vi.spyOn(ElMessage, "success").mockReturnValue({} as never);
    const drawer = await openItemDetail();
    const designButtons = drawer.findAll('[data-testid="designate-cover"]');
    // 图片可以指定，PDF 不可以
    expect(designButtons.length).toBe(1);
    await designButtons[0].trigger("click");
    await flushPromises();

    expect(designateItemCoverMock).toHaveBeenCalledWith("item-1", "f1", 1, undefined);
    expect(successSpy).toHaveBeenCalledWith("已设为封面");
    successSpy.mockRestore();
  });

  it("asks how to dispose the old cover when replacing it", async () => {
    fetchItemsMock.mockResolvedValue({
      items: [
        { ...activeItem, version: 1, coverFileId: "f1", coverUrl: "/api/v1/files/f1/content" }
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    } as never);
    listItemAttachmentsMock.mockResolvedValue([
      {
        id: "f1",
        name: "旧封面.jpg",
        mediaType: "image/jpeg",
        byteSize: 2048,
        mountType: "ITEM",
        mountId: "item-1",
        createdAt: "2026-08-15T10:00:00Z",
        url: "/api/v1/files/f1/content",
      },
      {
        id: "f3",
        name: "新图.jpg",
        mediaType: "image/jpeg",
        byteSize: 2048,
        mountType: "ITEM",
        mountId: "item-1",
        createdAt: "2026-08-15T10:00:00Z",
        url: "/api/v1/files/f3/content",
      },
    ] as Attachment[]);
    // ElMessageBox.confirm 的 reject 分支返回 'cancel'（送回收站）
    vi.spyOn(ElMessageBox, "confirm").mockRejectedValue("cancel");

    const drawer = await openItemDetail();
    const designButtons = drawer.findAll('[data-testid="designate-cover"]');
    // 旧封面显示「当前封面」，只有 f3 是「设为封面」
    expect(designButtons.length).toBe(2);
    const replaceBtn = designButtons.find((b) => b.text().includes("设为封面"));
    await replaceBtn!.trigger("click");
    await flushPromises();

    expect(designateItemCoverMock).toHaveBeenCalledWith("item-1", "f3", 1, "RECYCLE");
  });

  it("deletes an attachment from the item detail drawer", async () => {
    const drawer = await openItemDetail();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const buttons = drawer.findAll(".el-button");
    const deleteButtons = buttons.filter((b) => b.text().includes("删除"));
    // 第二行是 PDF 附件
    await deleteButtons[1].trigger("click");
    await flushPromises();

    expect(deleteAttachmentMock).toHaveBeenCalledWith("f2");
  });

  // ==================== 封面状态与版本刷新（stale cover state after delete） ====================

  /** 带当前封面的物品与附件列表：f1 是封面图，f2 是普通 PDF。 */
  function mockItemWithCover() {
    fetchItemsMock.mockResolvedValue({
      items: [
        {
          ...activeItem,
          coverFileId: "f1",
          coverUrl: "/api/v1/files/f1/content",
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    } as never);
    listItemAttachmentsMock.mockResolvedValue([
      {
        id: "f1",
        name: "封面.jpg",
        mediaType: "image/jpeg",
        byteSize: 2048,
        mountType: "ITEM",
        mountId: "item-1",
        createdAt: "2026-08-15T10:00:00Z",
        url: "/api/v1/files/f1/content",
      },
      {
        id: "f2",
        name: "说明书.pdf",
        mediaType: "application/pdf",
        byteSize: 100,
        mountType: "ITEM",
        mountId: "item-1",
        createdAt: "2026-08-15T10:00:00Z",
        url: "/api/v1/files/f2/content",
      },
    ] as Attachment[]);
  }

  it("refreshes the item after deleting the current cover so the drawer drops it and later actions use the new version", async () => {
    mockItemWithCover();
    // 服务器：删除封面附件后清除封面指定并递增版本
    fetchItemMock.mockResolvedValue({
      ...activeItem,
      coverFileId: null,
      coverUrl: undefined,
      version: 2,
    } as never);
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);

    const drawer = await openItemDetail();
    expect(drawer.find(".detail-cover").exists()).toBe(true);

    // 删除当前封面附件
    const deleteButtons = drawer.findAll(".el-button").filter((b) => b.text().includes("删除"));
    await deleteButtons[0].trigger("click");
    await flushPromises();

    expect(deleteAttachmentMock).toHaveBeenCalledWith("f1");
    // 抽屉刷新了物品：封面消失、版本已是最新
    expect(fetchItemMock).toHaveBeenCalledWith("item-1");
    expect(drawer.find(".detail-cover").exists()).toBe(false);

    // 后续归档使用刷新后的版本，不再携带过期版本（否则服务器 409）
    const archiveBtn = drawer.findAll(".el-button").find((b) => b.text().includes("归档"))!;
    await archiveBtn.trigger("click");
    await flushPromises();

    expect(archiveItemMock).toHaveBeenCalledWith("item-1", 2);
  });

  it("shows the new tag name (not its id) in the table after an item is saved with a tag created during editing", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // 用户编辑物品时新建了一个标签 tag-3，保存后物品带上了新标签 id
    const freshItem: CatalogItem = { ...activeItem, tagIds: ["tag-1", "tag-3"] };
    fetchItemsMock.mockResolvedValueOnce({
      items: [freshItem],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    // 服务器已持久化新标签：刷新字典时 fetchTags 返回它
    fetchTagsMock.mockResolvedValueOnce([
      tag,
      tag2,
      { ...tag, id: "tag-3", name: "常用紧急" },
    ]);

    const formDrawer = wrapper.findComponent({ name: "ItemFormDrawer" });
    formDrawer.vm.$emit("saved", freshItem);
    await flushPromises();

    const firstRow = wrapper.findAll("tbody tr")[0];
    expect(firstRow.text()).toContain("重要");
    // 新标签应显示名称而非 id；字典里还没有 tag-3，说明刷新兜底生效
    expect(firstRow.text()).toContain("常用紧急");
    expect(firstRow.text()).not.toContain("tag-3");
  });

  it("refreshes dictionaries before refetching items after save (no bare id race)", async () => {
    wrapper = mount(ItemsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // 编辑物品时新建了一个标签 tag-3，保存后物品带上新标签 id
    const freshItem: CatalogItem = { ...activeItem, tagIds: ["tag-1", "tag-3"] };
    fetchItemsMock.mockReset().mockResolvedValue({
      items: [freshItem],
      total: 1,
      page: 1,
      pageSize: 20,
    });

    // 让字典刷新挂起：若列表拉取与字典刷新并行，列表先返回时 tag-3 无处解析为裸 id
    let resolveTags!: (v: Tag[]) => void;
    fetchTagsMock.mockReset().mockReturnValue(
      new Promise<Tag[]>((resolve) => { resolveTags = resolve; }),
    );
    fetchCategoriesMock.mockReset().mockResolvedValue([category]);
    fetchBrandsMock.mockReset().mockResolvedValue([brand]);
    fetchUnitsMock.mockReset().mockResolvedValue([unit]);

    const formDrawer = wrapper.findComponent({ name: "ItemFormDrawer" });
    formDrawer.vm.$emit("saved", freshItem);
    await flushPromises();

    // 字典尚未返回时不应重拉列表（否则 tag-3 以裸 id 渲染）
    expect(fetchItemsMock).not.toHaveBeenCalled();

    // 字典返回后才重拉列表
    resolveTags([tag, tag2, { ...tag, id: "tag-3", name: "常用紧急" }]);
    await flushPromises();

    expect(fetchItemsMock).toHaveBeenCalled();
    const firstRow = wrapper.findAll("tbody tr")[0];
    expect(firstRow.text()).toContain("常用紧急");
    expect(firstRow.text()).not.toContain("tag-3");
  });

  it("refreshes the item after remounting the current cover so the drawer stops showing the stale cover", async () => {
    mockItemWithCover();
    // 服务器：封面附件改挂到家庭后清除封面指定并递增版本
    fetchItemMock.mockResolvedValue({
      ...activeItem,
      coverFileId: null,
      coverUrl: undefined,
      version: 2,
    } as never);

    const drawer = await openItemDetail();
    expect(drawer.find(".detail-cover").exists()).toBe(true);

    const remountBtn = drawer.findAll(".el-button").find((b) => b.text().includes("移走"))!;
    await remountBtn.trigger("click");
    await flushPromises();

    expect(remountAttachmentMock).toHaveBeenCalledWith("f1");
    expect(fetchItemMock).toHaveBeenCalledWith("item-1");
    expect(drawer.find(".detail-cover").exists()).toBe(false);
  });

  it("does not refetch the item when deleting a non-cover attachment", async () => {
    const drawer = await openItemDetail();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const deleteButtons = drawer.findAll(".el-button").filter((b) => b.text().includes("删除"));
    await deleteButtons[1].trigger("click");
    await flushPromises();

    expect(deleteAttachmentMock).toHaveBeenCalledWith("f2");
    // 非封面附件不影响物品版本/封面，无需额外请求
    expect(fetchItemMock).not.toHaveBeenCalled();
  });
});
