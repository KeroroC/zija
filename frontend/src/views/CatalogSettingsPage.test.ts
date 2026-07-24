import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchCategories, createCategory, renameCategory, moveCategory, archiveCategory, restoreCategory,
  fetchBrands, createBrand, renameBrand, archiveBrand, restoreBrand,
  fetchUnits, createUnit, renameUnit, archiveUnit, restoreUnit,
  fetchTags, createTag, renameTag, archiveTag, restoreTag,
} from "../api/catalog";
import type { Category, Brand, Unit, Tag } from "../types/catalog";
import CatalogSettingsPage from "./CatalogSettingsPage.vue";

// --------------- Mocks ---------------

vi.mock("../api/catalog", () => ({
  fetchCategories: vi.fn(),
  createCategory: vi.fn(),
  renameCategory: vi.fn(),
  moveCategory: vi.fn(),
  archiveCategory: vi.fn(),
  restoreCategory: vi.fn(),
  fetchBrands: vi.fn(),
  createBrand: vi.fn(),
  renameBrand: vi.fn(),
  archiveBrand: vi.fn(),
  restoreBrand: vi.fn(),
  fetchUnits: vi.fn(),
  createUnit: vi.fn(),
  renameUnit: vi.fn(),
  archiveUnit: vi.fn(),
  restoreUnit: vi.fn(),
  fetchTags: vi.fn(),
  createTag: vi.fn(),
  renameTag: vi.fn(),
  archiveTag: vi.fn(),
  restoreTag: vi.fn(),
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => sessionState,
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
}));

// Mock ElMessageBox.confirm to auto-resolve by default
vi.mock("element-plus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("element-plus")>();
  return {
    ...actual,
    ElMessageBox: {
      ...actual.ElMessageBox,
      confirm: vi.fn().mockResolvedValue("confirm"),
    },
  };
});

// --------------- Typed mocks ---------------

const fetchCategoriesMock = vi.mocked(fetchCategories);
const createCategoryMock = vi.mocked(createCategory);
const renameCategoryMock = vi.mocked(renameCategory);
const moveCategoryMock = vi.mocked(moveCategory);
const archiveCategoryMock = vi.mocked(archiveCategory);
const restoreCategoryMock = vi.mocked(restoreCategory);

const fetchBrandsMock = vi.mocked(fetchBrands);
const createBrandMock = vi.mocked(createBrand);
const renameBrandMock = vi.mocked(renameBrand);
const archiveBrandMock = vi.mocked(archiveBrand);
const restoreBrandMock = vi.mocked(restoreBrand);

const fetchUnitsMock = vi.mocked(fetchUnits);
const createUnitMock = vi.mocked(createUnit);
const renameUnitMock = vi.mocked(renameUnit);
const archiveUnitMock = vi.mocked(archiveUnit);
const restoreUnitMock = vi.mocked(restoreUnit);

const fetchTagsMock = vi.mocked(fetchTags);
const createTagMock = vi.mocked(createTag);
const renameTagMock = vi.mocked(renameTag);
const archiveTagMock = vi.mocked(archiveTag);
const restoreTagMock = vi.mocked(restoreTag);

// --------------- Mock data ---------------

const rootCategory: Category = {
  id: "cat-1",
  householdId: "h1",
  parentId: null,
  name: "电子产品",
  status: "ACTIVE",
  sortOrder: 0,
  version: 1,
};

const childCategory: Category = {
  id: "cat-2",
  householdId: "h1",
  parentId: "cat-1",
  name: "手机",
  status: "ACTIVE",
  sortOrder: 0,
  version: 1,
};

const archivedCategory: Category = {
  id: "cat-3",
  householdId: "h1",
  parentId: null,
  name: "已归档分类",
  status: "ARCHIVED",
  sortOrder: 0,
  version: 2,
};

const mockCategories: Category[] = [rootCategory, childCategory, archivedCategory];

const activeBrand: Brand = {
  id: "brand-1",
  householdId: "h1",
  name: "苹果",
  status: "ACTIVE",
  version: 1,
};

const archivedBrand: Brand = {
  id: "brand-2",
  householdId: "h1",
  name: "诺基亚",
  status: "ARCHIVED",
  version: 2,
};

const mockBrands: Brand[] = [activeBrand, archivedBrand];

const activeUnit: Unit = {
  id: "unit-1",
  householdId: "h1",
  name: "个",
  decimalScale: 0,
  status: "ACTIVE",
  version: 1,
};

const archivedUnit: Unit = {
  id: "unit-2",
  householdId: "h1",
  name: "箱",
  decimalScale: 0,
  status: "ARCHIVED",
  version: 3,
};

const mockUnits: Unit[] = [activeUnit, archivedUnit];

const activeTag: Tag = {
  id: "tag-1",
  householdId: "h1",
  name: "常用",
  status: "ACTIVE",
  version: 1,
};

const archivedTag: Tag = {
  id: "tag-2",
  householdId: "h1",
  name: "旧标签",
  status: "ARCHIVED",
  version: 2,
};

const mockTags: Tag[] = [activeTag, archivedTag];

// --------------- Session & router ---------------

const pushMock = vi.fn();

const sessionState: {
  role: "OWNER" | "ADMIN" | "MEMBER" | null;
  currentMember: {
    householdId: string;
    memberId: string;
    accountId: string;
    username: string;
    displayName: string;
    role: "OWNER" | "ADMIN" | "MEMBER";
    status: "ACTIVE" | "DEACTIVATED";
  } | null;
} = {
  role: "ADMIN",
  currentMember: {
    householdId: "h1",
    memberId: "m-admin",
    accountId: "a-admin",
    username: "admin",
    displayName: "管理员",
    role: "ADMIN",
    status: "ACTIVE",
  },
};

// --------------- Tests ---------------

describe("CatalogSettingsPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    fetchCategoriesMock.mockReset().mockResolvedValue(mockCategories);
    fetchBrandsMock.mockReset().mockResolvedValue(mockBrands);
    fetchUnitsMock.mockReset().mockResolvedValue(mockUnits);
    fetchTagsMock.mockReset().mockResolvedValue(mockTags);

    createCategoryMock.mockReset().mockResolvedValue(rootCategory);
    renameCategoryMock.mockReset().mockResolvedValue(rootCategory);
    moveCategoryMock.mockReset().mockResolvedValue(undefined);
    archiveCategoryMock.mockReset().mockResolvedValue(undefined);
    restoreCategoryMock.mockReset().mockResolvedValue(undefined);

    createBrandMock.mockReset().mockResolvedValue(activeBrand);
    renameBrandMock.mockReset().mockResolvedValue(undefined);
    archiveBrandMock.mockReset().mockResolvedValue(undefined);
    restoreBrandMock.mockReset().mockResolvedValue(undefined);

    createUnitMock.mockReset().mockResolvedValue(activeUnit);
    renameUnitMock.mockReset().mockResolvedValue(activeUnit);
    archiveUnitMock.mockReset().mockResolvedValue(undefined);
    restoreUnitMock.mockReset().mockResolvedValue(undefined);

    createTagMock.mockReset().mockResolvedValue(activeTag);
    renameTagMock.mockReset().mockResolvedValue(activeTag);
    archiveTagMock.mockReset().mockResolvedValue(undefined);
    restoreTagMock.mockReset().mockResolvedValue(undefined);

    pushMock.mockReset();

    sessionState.role = "ADMIN";
    sessionState.currentMember = {
      householdId: "h1",
      memberId: "m-admin",
      accountId: "a-admin",
      username: "admin",
      displayName: "管理员",
      role: "ADMIN",
      status: "ACTIVE",
    };
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("renders four tabs and loads all catalog data on mount", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // All four fetch calls happen on mount
    expect(fetchCategoriesMock).toHaveBeenCalledOnce();
    expect(fetchBrandsMock).toHaveBeenCalledOnce();
    expect(fetchUnitsMock).toHaveBeenCalledOnce();
    expect(fetchTagsMock).toHaveBeenCalledOnce();

    // Verify tab labels are present
    const text = wrapper.text();
    expect(text).toContain("分类");
    expect(text).toContain("品牌");
    expect(text).toContain("单位");
    expect(text).toContain("标签");
  });

  it("displays category tree with root and child nodes", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Default active tab is categories — tree should show category names
    expect(wrapper.text()).toContain("电子产品");
    expect(wrapper.text()).toContain("已归档分类");
  });

  it("displays brands table with active and archived items", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Switch to brands tab
    const brandsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("品牌"));
    await brandsTab!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("苹果");
    expect(wrapper.text()).toContain("诺基亚");
  });

  it("displays units table with decimal scale column", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const unitsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("单位"));
    await unitsTab!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("个");
    expect(wrapper.text()).toContain("箱");
  });

  it("displays tags table", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const tagsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("标签"));
    await tagsTab!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("常用");
    expect(wrapper.text()).toContain("旧标签");
  });

  it("shows archive, restore, rename, and move action buttons for admin", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Categories tab (default) — check action buttons on root category
    const text = wrapper.text();
    // Active category should have rename, move, archive buttons
    expect(text).toContain("重命名");
    expect(text).toContain("移动");
    expect(text).toContain("归档");
    // Archived category should have restore button
    expect(text).toContain("恢复");

    // Switch to brands tab and check actions
    const brandsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("品牌"));
    await brandsTab!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("重命名");
    expect(wrapper.text()).toContain("归档");
    expect(wrapper.text()).toContain("恢复");

    // Switch to units tab
    const unitsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("单位"));
    await unitsTab!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("重命名");
    expect(wrapper.text()).toContain("归档");
    expect(wrapper.text()).toContain("恢复");

    // Switch to tags tab
    const tagsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("标签"));
    await tagsTab!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("重命名");
    expect(wrapper.text()).toContain("归档");
    expect(wrapper.text()).toContain("恢复");
  });

  it("renders correctly for OWNER role (route-level permission gate)", async () => {
    sessionState.role = "OWNER";
    sessionState.currentMember = {
      householdId: "h1",
      memberId: "m-owner",
      accountId: "a-owner",
      username: "owner",
      displayName: "所有者",
      role: "OWNER",
      status: "ACTIVE",
    };

    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Component should render fully for OWNER
    expect(wrapper.find(".catalog-settings-page").exists()).toBe(true);
    expect(wrapper.text()).toContain("目录设置");
    expect(fetchCategoriesMock).toHaveBeenCalledOnce();
    expect(fetchBrandsMock).toHaveBeenCalledOnce();
  });

  it("brand rename dialog submission calls renameBrand", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Switch to brands tab
    const brandsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("品牌"));
    await brandsTab!.trigger("click");
    await flushPromises();

    // Scope to the brands tab pane to avoid picking up category tree buttons
    const brandsPane = wrapper.find("#pane-brands");
    const renameButtons = brandsPane.findAll("button").filter((b) => b.text().includes("重命名"));
    await renameButtons[0].trigger("click");
    await flushPromises();

    // Rename dialog should be visible with title "重命名品牌"
    const dialogText = wrapper.find(".el-dialog").text();
    expect(dialogText).toContain("重命名品牌");

    // Find the dialog input and change the value
    const dialogInput = wrapper.find(".el-dialog .el-input__inner");
    await dialogInput.setValue("新品牌名称");

    // Click confirm button in the dialog
    const confirmBtn = wrapper.findAll(".el-dialog__footer button").find((b) => b.text().includes("确定"));
    await confirmBtn!.trigger("click");
    await flushPromises();

    expect(renameBrandMock).toHaveBeenCalledWith("brand-1", "新品牌名称", 1);
  });

  it("unit archive confirmation calls archiveUnit", async () => {
    wrapper = mount(CatalogSettingsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Switch to units tab
    const unitsTab = wrapper.findAll(".el-tabs__item").find((el) => el.text().includes("单位"));
    await unitsTab!.trigger("click");
    await flushPromises();

    // Scope to the units tab pane to avoid picking up category tree buttons
    const unitsPane = wrapper.find("#pane-units");
    const archiveButtons = unitsPane.findAll("button").filter((b) => b.text().includes("归档"));
    await archiveButtons[0].trigger("click");
    await flushPromises();

    expect(archiveUnitMock).toHaveBeenCalledWith("unit-1", 1);
    // After archive, units are reloaded
    expect(fetchUnitsMock).toHaveBeenCalledTimes(2);
  });
});
