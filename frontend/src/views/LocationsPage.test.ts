import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchLocationTree, createLocation, renameLocation, deleteLocation, moveLocation } from "../api/location";
import { fetchStockPositions } from "../api/inventory";
import LocationsPage from "./LocationsPage.vue";
import type { LocationNode } from "../types/location";

vi.mock("../api/location", () => ({
  fetchLocationTree: vi.fn(),
  createLocation: vi.fn(),
  renameLocation: vi.fn(),
  deleteLocation: vi.fn(),
  moveLocation: vi.fn(),
}));

vi.mock("../api/inventory", () => ({
  fetchStockPositions: vi.fn(),
}));

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

const pushMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
}));

const fetchTreeMock = vi.mocked(fetchLocationTree);
const createMock = vi.mocked(createLocation);
const renameMock = vi.mocked(renameLocation);
const deleteMock = vi.mocked(deleteLocation);
const moveMock = vi.mocked(moveLocation);
const fetchStockPositionsMock = vi.mocked(fetchStockPositions);

const childNode: LocationNode = {
  id: "loc-child",
  parentId: "loc-root",
  name: "卧室",
  sortOrder: 1,
  everReferenced: false,
  version: 1,
  children: [],
};

const rootNode: LocationNode = {
  id: "loc-root",
  parentId: null,
  name: "家",
  sortOrder: 0,
  everReferenced: true,
  version: 2,
  children: [childNode],
};

const treeResponse = { roots: [rootNode] };

describe("LocationsPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    fetchTreeMock.mockReset().mockResolvedValue(treeResponse);
    fetchStockPositionsMock.mockReset().mockResolvedValue({
      items: [
        { lotId: "lot-1", locationId: "loc-root", itemName: "耳机", itemManagementType: "DURABLE", unitName: "个", quantity: "5", revision: 0, expiryDate: null, lotNumber: null, serialNumber: null, updatedAt: "2026-01-01T00:00:00Z" },
      ],
      total: 1,
      page: 1,
      pageSize: 10000,
    });
    createMock.mockReset().mockResolvedValue({
      id: "loc-new",
      householdId: "h1",
      parentId: "loc-root",
      name: "新位置",
      sortOrder: 0,
      everReferenced: false,
      version: 1,
    });
    renameMock.mockReset().mockResolvedValue({
      id: "loc-child",
      householdId: "h1",
      parentId: "loc-root",
      name: "已重命名",
      sortOrder: 1,
      everReferenced: false,
      version: 2,
    });
    deleteMock.mockReset().mockResolvedValue(undefined);
    moveMock.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("loads and renders the location tree", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(fetchTreeMock).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("家");
    expect(wrapper.text()).toContain("卧室");
  });

  it("creates a child location via dialog submission", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Click the add button on the root node to open create-child dialog
    const createButtons = wrapper.findAll('[data-testid="loc-add"]');
    await createButtons[0].trigger("click");
    await flushPromises();

    // Fill in the name input
    const nameInput = wrapper.find(".el-dialog .el-input__inner");
    await nameInput.setValue("新位置");

    // Click confirm
    const confirmBtns = wrapper.findAll(".el-dialog button").filter((b) => b.text() === "确定");
    await confirmBtns[0].trigger("click");
    await flushPromises();

    expect(createMock).toHaveBeenCalledWith({
      name: "新位置",
      parentId: "loc-root",
      sortOrder: 0,
    });
    // Tree should be reloaded
    expect(fetchTreeMock).toHaveBeenCalledTimes(2);
  });

  it("renames a location via dialog submission", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Click the rename button on the root node (first in DOM order)
    const renameButtons = wrapper.findAll('[data-testid="loc-rename"]');
    await renameButtons[0].trigger("click");
    await flushPromises();

    // Change the name
    const nameInput = wrapper.find(".el-dialog .el-input__inner");
    await nameInput.setValue("已重命名");

    // Click confirm
    const confirmBtns = wrapper.findAll(".el-dialog button").filter((b) => b.text() === "确定");
    await confirmBtns[0].trigger("click");
    await flushPromises();

    expect(renameMock).toHaveBeenCalledWith("loc-root", {
      name: "已重命名",
      version: 2,
    });
    expect(fetchTreeMock).toHaveBeenCalledTimes(2);
  });

  it("moves a location via dialog submission", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Click the move button on the root node (first in DOM order)
    const moveButtons = wrapper.findAll('[data-testid="loc-move"]');
    await moveButtons[0].trigger("click");
    await flushPromises();

    // The move dialog should be visible; click on a node in the dialog tree to select target
    const dialogTrees = wrapper.findAll(".el-dialog .el-tree");
    expect(dialogTrees.length).toBeGreaterThan(0);
    const dialogTreeNodes = dialogTrees[0].findAll(".el-tree-node__content");
    // The first node is the root "家" — select it as the target
    await dialogTreeNodes[0].trigger("click");
    await flushPromises();

    // Click confirm
    const confirmBtns = wrapper.findAll(".el-dialog footer button, .el-dialog .el-dialog__footer button").filter(
      (b) => b.text() === "确定"
    );
    await confirmBtns[0].trigger("click");
    await flushPromises();

    expect(moveMock).toHaveBeenCalledWith("loc-root", {
      parentId: "loc-root",
      sortOrder: 0,
      version: 2,
    });
    expect(fetchTreeMock).toHaveBeenCalledTimes(2);
  });

  it("deletes a location with confirmation", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // The root node "家" is everReferenced so has no delete button.
    // The child node "卧室" (loc-child) has a delete button.
    const deleteButtons = wrapper.findAll('[data-testid="loc-delete"]');
    expect(deleteButtons).toHaveLength(1);
    await deleteButtons[0].trigger("click");
    await flushPromises();

    // Confirm the ElMessageBox
    const messageBoxBtn = document.querySelector(".el-message-box__btns .el-button--primary");
    expect(messageBoxBtn).toBeTruthy();
    await (messageBoxBtn as HTMLButtonElement).click();
    await flushPromises();

    expect(deleteMock).toHaveBeenCalledWith("loc-child", 1);
    expect(fetchTreeMock).toHaveBeenCalledTimes(2);
  });

  it("shows error message when API call fails", async () => {
    createMock.mockRejectedValue({ title: "创建失败" });

    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Open create dialog
    const createButtons = wrapper.findAll('[data-testid="loc-add"]');
    await createButtons[0].trigger("click");
    await flushPromises();

    const nameInput = wrapper.find(".el-dialog .el-input__inner");
    await nameInput.setValue("新位置");

    const confirmBtns = wrapper.findAll(".el-dialog button").filter((b) => b.text() === "确定");
    await confirmBtns[0].trigger("click");
    await flushPromises();

    // ElMessage.error should have been called — check for the message element in the DOM
    expect(document.querySelector(".el-message--error")).toBeTruthy();
  });

  it("shows warning and does not call API when deleting a node with children", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // The root node "家" has children and also has everReferenced=true so delete button is hidden.
    // Let's use a tree where a node has children but everReferenced=false so delete button shows.
    wrapper.unmount();

    const parentWithChildren: LocationNode = {
      id: "loc-parent",
      parentId: null,
      name: "有子节点",
      sortOrder: 0,
      everReferenced: false,
      version: 1,
      children: [
        {
          id: "loc-c1",
          parentId: "loc-parent",
          name: "子节点1",
          sortOrder: 0,
          everReferenced: false,
          version: 1,
          children: [],
        },
      ],
    };
    fetchTreeMock.mockResolvedValue({ roots: [parentWithChildren] });

    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Click the delete button on the parent node
    const deleteButtons = wrapper.findAll('[data-testid="loc-delete"]');
    expect(deleteButtons.length).toBeGreaterThan(0);
    await deleteButtons[0].trigger("click");
    await flushPromises();

    // Should show warning message, not the confirmation dialog
    expect(document.querySelector(".el-message--warning")).toBeTruthy();
    expect(deleteMock).not.toHaveBeenCalled();
  });

  it("shows inventory summary instead of placeholder, with navigation buttons", async () => {
    wrapper = mount(LocationsPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    // Click on the root node to select it
    const treeNodes = wrapper.findAll(".el-tree-node__content");
    await treeNodes[0].trigger("click");
    await flushPromises();

    // Inventory summary should be visible (not the old placeholder)
    expect(wrapper.text()).toContain("库存记录");
    expect(wrapper.text()).toContain("1 条");
    expect(wrapper.text()).toContain("库存总量");
    expect(wrapper.text()).toContain("5");
    expect(wrapper.text()).not.toContain("库存将在阶段四启用");

    // Buttons should exist
    const viewBtn = wrapper.findAll(".el-button").find((b) => b.text().includes("查看库存"));
    const stocktakeBtn = wrapper.findAll(".el-button").find((b) => b.text().includes("发起盘点"));
    expect(viewBtn).toBeTruthy();
    expect(stocktakeBtn).toBeTruthy();

    // Click 查看库存 button
    await viewBtn!.trigger("click");
    await flushPromises();
    expect(pushMock).toHaveBeenCalledWith({ name: "inventory", query: { locationId: "loc-root" } });

    // Click 发起盘点 button
    pushMock.mockClear();
    await stocktakeBtn!.trigger("click");
    await flushPromises();
    expect(pushMock).toHaveBeenCalledWith({ name: "inventory", query: { action: "stocktake", locationId: "loc-root" } });
  });
});
