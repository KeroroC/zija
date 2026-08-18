import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/location", () => ({
  createLocation: vi.fn(),
}));

import LocationCreateDialog from "./LocationCreateDialog.vue";
import { createLocation } from "../api/location";
import type { LocationInfo } from "../types/location";

const createMock = vi.mocked(createLocation);

const createdLocation: LocationInfo = {
  id: "loc-new",
  householdId: "h1",
  parentId: null,
  name: "冰箱",
  sortOrder: 0,
  everReferenced: false,
  version: 0,
};

// el-dialog 使用 append-to-body 传送到 body，DOM 需经 testid 从 document 定位
function dialogEl(): HTMLElement | null {
  return document.querySelector('[data-testid="location-create-dialog"]');
}

describe("LocationCreateDialog", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    createMock.mockReset();
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    // 清除传送的弹窗与 ElMessage 残留，避免跨测试误判
    document.querySelectorAll("[data-testid='location-create-dialog'], .el-message").forEach((el) => el.remove());
  });

  async function mountDialog(props: Partial<{
    parentId: string | null;
    parentName: string | null;
    existingNames: string[];
  }> = {}) {
    wrapper = mount(LocationCreateDialog, {
      props: {
        modelValue: false,
        parentId: null,
        parentName: null,
        existingNames: [],
        ...props,
      },
      global: { plugins: [ElementPlus] },
    });
    await wrapper.setProps({ modelValue: true });
    await flushPromises();
    return wrapper;
  }

  async function submitName(value: string) {
    const input = wrapper!.findComponent({ name: "ElInput" });
    await input.vm.$emit("update:modelValue", value);
    const dlg = dialogEl()!;
    const confirm = Array.from(dlg.querySelectorAll("button")).find(
      (b) => b.textContent?.trim() === "确定",
    );
    confirm!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    await flushPromises();
  }

  // el-form-item 的 error 渲染经过 100ms 防抖（validateStateDebounced），断言前需等待
  async function waitForInlineError() {
    await new Promise((r) => setTimeout(r, 150));
  }

  it("shows root-mode title when no parent is given", async () => {
    await mountDialog();
    const dlg = dialogEl()!;
    expect(dlg.querySelector(".el-dialog__title")?.textContent).toContain("新增根位置");
    expect(dlg.textContent).not.toContain("父位置");
  });

  it("shows child-mode title and parent hint when a parent is given", async () => {
    await mountDialog({ parentId: "p1", parentName: "客厅" });
    const dlg = dialogEl()!;
    expect(dlg.querySelector(".el-dialog__title")?.textContent).toContain("新增子位置");
    expect(dlg.textContent).toContain("父位置：客厅");
  });

  it("creates a root location, emits created, and closes", async () => {
    createMock.mockResolvedValue(createdLocation);
    await mountDialog();
    await submitName("冰箱");
    expect(createMock).toHaveBeenCalledWith({ name: "冰箱", parentId: null, sortOrder: 0 });
    expect(wrapper!.emitted("created")).toEqual([[createdLocation]]);
    expect(wrapper!.emitted("update:modelValue")).toEqual([[false]]);
  });

  it("creates a child location under the given parent", async () => {
    createMock.mockResolvedValue({ ...createdLocation, parentId: "p1" });
    await mountDialog({ parentId: "p1", parentName: "客厅" });
    await submitName("储物间");
    expect(createMock).toHaveBeenCalledWith({ name: "储物间", parentId: "p1", sortOrder: 0 });
    expect(wrapper!.emitted("created")).toHaveLength(1);
  });

  it("warns and does not submit when the name is blank", async () => {
    await mountDialog();
    const dlg = dialogEl()!;
    const confirm = Array.from(dlg.querySelectorAll("button")).find(
      (b) => b.textContent?.trim() === "确定",
    );
    confirm!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    await flushPromises();
    expect(createMock).not.toHaveBeenCalled();
    expect(wrapper!.emitted("created")).toBeUndefined();
    expect(document.querySelector(".el-message--warning")?.textContent).toContain("请输入名称");
  });

  it("blocks a sibling name collision with an inline error", async () => {
    await mountDialog({ existingNames: ["厨房", " 储藏间 "] });
    await submitName(" 厨房 ");
    await waitForInlineError();
    expect(createMock).not.toHaveBeenCalled();
    expect(wrapper!.emitted("created")).toBeUndefined();
    expect(dialogEl()!.querySelector(".el-form-item__error")?.textContent).toContain("该名称已存在");
  });

  it("normalizes names before the collision check", async () => {
    await mountDialog({ existingNames: ["Kitchen"] });
    await submitName("ｋｉｔｃｈｅｎ");
    await waitForInlineError();
    expect(createMock).not.toHaveBeenCalled();
    expect(dialogEl()!.querySelector(".el-form-item__error")?.textContent).toContain("该名称已存在");
  });

  it("stays open and surfaces the error when the API rejects", async () => {
    createMock.mockRejectedValue(new Error("网络错误"));
    await mountDialog();
    await submitName("车库");
    expect(wrapper!.emitted("created")).toBeUndefined();
    expect(wrapper!.emitted("update:modelValue")).toBeUndefined();
    expect(document.querySelector(".el-message--error")?.textContent).toContain("网络错误");
  });
});
