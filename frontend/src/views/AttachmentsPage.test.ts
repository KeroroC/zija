import ElementPlus, { ElMessage, ElMessageBox, ElOption } from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AttachmentsPage from "./AttachmentsPage.vue";
import {
  listAttachments,
  renameAttachment,
  uploadHouseholdAttachment,
  deleteAttachment,
  restoreAttachment,
  remountAttachmentToHousehold,
  remountAttachmentToItem,
  remountAttachmentToLot,
  type Attachment
} from "../api/file";
import { fetchItems } from "../api/catalog";
import { fetchLots } from "../api/inventory";
import { ApiError } from "../api/http";

vi.mock("../api/file", () => ({
  listAttachments: vi.fn(),
  uploadHouseholdAttachment: vi.fn(),
  renameAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
  restoreAttachment: vi.fn(),
  remountAttachmentToHousehold: vi.fn(),
  remountAttachmentToItem: vi.fn(),
  remountAttachmentToLot: vi.fn()
}));

vi.mock("../api/catalog", () => ({
  fetchItems: vi.fn()
}));

vi.mock("../api/inventory", () => ({
  fetchLots: vi.fn()
}));

const listMock = vi.mocked(listAttachments);
const uploadMock = vi.mocked(uploadHouseholdAttachment);
const renameMock = vi.mocked(renameAttachment);
const deleteMock = vi.mocked(deleteAttachment);
const restoreMock = vi.mocked(restoreAttachment);
const remountMock = vi.mocked(remountAttachmentToHousehold);
const remountItemMock = vi.mocked(remountAttachmentToItem);
const remountLotMock = vi.mocked(remountAttachmentToLot);
const fetchItemsMock = vi.mocked(fetchItems);
const fetchLotsMock = vi.mocked(fetchLots);

const householdAttachment: Attachment = {
  id: "f1",
  name: "户口本.jpg",
  mediaType: "image/jpeg",
  byteSize: 2048,
  mountType: "HOUSEHOLD",
  mountId: "h1",
  createdAt: "2026-08-15T10:00:00Z",
  url: "/api/v1/files/f1/content"
};

const itemAttachment: Attachment = {
  id: "f2",
  name: "说明书.pdf",
  mediaType: "application/pdf",
  byteSize: 100,
  mountType: "ITEM",
  mountId: "i1",
  createdAt: "2026-08-15T10:00:00Z",
  url: "/api/v1/files/f2/content"
};

const lotAttachment: Attachment = {
  id: "f3",
  name: "小票.jpg",
  mediaType: "image/jpeg",
  byteSize: 500,
  mountType: "LOT",
  mountId: "l1",
  createdAt: "2026-08-15T10:00:00Z",
  url: "/api/v1/files/f3/content"
};

function pageResult(items: Attachment[]) {
  return { items, total: items.length, page: 1, pageSize: 50 };
}

function mountPage() {
  return mount(AttachmentsPage, {
    global: {
      plugins: [
        ElementPlus,
        createRouter({ history: createMemoryHistory(), routes: [] })
      ]
    }
  });
}

describe("AttachmentsPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    listMock.mockResolvedValue(pageResult([householdAttachment, itemAttachment, lotAttachment]));
    uploadMock.mockResolvedValue(householdAttachment);
    renameMock.mockResolvedValue(householdAttachment);
    deleteMock.mockResolvedValue({ ...householdAttachment, deletedAt: "2026-08-15T11:00:00Z" });
    restoreMock.mockResolvedValue(householdAttachment);
    remountMock.mockResolvedValue(householdAttachment);
    remountItemMock.mockResolvedValue({ ...householdAttachment, mountType: "ITEM", mountId: "i1" });
    remountLotMock.mockResolvedValue({ ...householdAttachment, mountType: "LOT", mountId: "l1" });
    fetchItemsMock.mockResolvedValue({
      items: [{ id: "i1", name: "吸尘器", status: "ACTIVE" }] as never,
      total: 1,
      page: 1,
      pageSize: 1000
    });
    fetchLotsMock.mockResolvedValue({
      items: [
        {
          lotId: "l1",
          itemName: "吸尘器",
          lotNumber: "B2026-01",
          serialNumber: null
        }
      ] as never,
      total: 1,
      page: 1,
      pageSize: 1000
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.clearAllMocks();
  });

  it("renders attachments with mount point labels", async () => {
    wrapper = mountPage();
    await flushPromises();

    expect(listMock).toHaveBeenCalled();
    expect(wrapper.text()).toContain("户口本.jpg");
    expect(wrapper.text()).toContain("家庭");
    expect(wrapper.text()).toContain("吸尘器");
  });

  it("filters by mount type and name query", async () => {
    wrapper = mountPage();
    await flushPromises();

    // 挂载点筛选
    const mountSelect = wrapper.findComponent({ name: "ElSelect" });
    await mountSelect.vm.$emit("update:modelValue", "ITEM");
    await mountSelect.vm.$emit("change", "ITEM");
    await flushPromises();
    expect(listMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ mountType: "ITEM", recycled: false })
    );

    // 名字搜索
    const search = wrapper.find("input[placeholder='按名字搜索']");
    await search.setValue("说明书");
    await new Promise((resolve) => setTimeout(resolve, 350));
    await flushPromises();
    expect(listMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "说明书", mountType: "ITEM" })
    );
  });

  it("switches to recycle bin view and lists recycled attachments", async () => {
    wrapper = mountPage();
    await flushPromises();

    const radioGroup = wrapper.findComponent({ name: "ElRadioGroup" });
    await radioGroup.vm.$emit("update:modelValue", "recycled");
    await radioGroup.vm.$emit("change", "recycled");
    await flushPromises();

    expect(listMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ recycled: true })
    );
    // 回收站视图下每行出现「恢复」而不是「删除」
    expect(wrapper.find('[data-testid="attachment-restore"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="attachment-delete"]').exists()).toBe(false);
  });

  it("deletes an attachment after confirmation", async () => {
    wrapper = mountPage();
    await flushPromises();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);

    const deleteButtons = wrapper.findAll('[data-testid="attachment-delete"]');
    await deleteButtons[0].trigger("click");
    await flushPromises();

    expect(deleteMock).toHaveBeenCalledWith("f1");
    expect(listMock).toHaveBeenCalled();
  });

  it("restores a recycled attachment", async () => {
    listMock.mockResolvedValue(pageResult([{ ...householdAttachment, deletedAt: "2026-08-15T11:00:00Z" }]));
    wrapper = mountPage();
    await flushPromises();

    const radioGroup = wrapper.findComponent({ name: "ElRadioGroup" });
    await radioGroup.vm.$emit("update:modelValue", "recycled");
    await radioGroup.vm.$emit("change", "recycled");
    await flushPromises();

    await wrapper.get('[data-testid="attachment-restore"]').trigger("click");
    await flushPromises();

    expect(restoreMock).toHaveBeenCalledWith("f1");
  });

  it("warns to rename when restore conflicts with an existing name", async () => {
    listMock.mockResolvedValue(pageResult([{ ...householdAttachment, deletedAt: "2026-08-15T11:00:00Z" }]));
    restoreMock.mockRejectedValue(
      new ApiError("同一挂载点下附件名字不可重复", "FILE_NAME_DUPLICATE", 409)
    );
    const warningSpy = vi.spyOn(ElMessage, "warning").mockReturnValue({} as never);

    wrapper = mountPage();
    await flushPromises();
    const radioGroup = wrapper.findComponent({ name: "ElRadioGroup" });
    await radioGroup.vm.$emit("update:modelValue", "recycled");
    await radioGroup.vm.$emit("change", "recycled");
    await flushPromises();

    await wrapper.get('[data-testid="attachment-restore"]').trigger("click");
    await flushPromises();

    expect(warningSpy).toHaveBeenCalledWith("原挂载点已有一份同名附件，请先改名再恢复");
  });

  it("renames an attachment from the row action", async () => {
    renameMock.mockResolvedValue({ ...householdAttachment, name: "房产证.jpg" });
    vi.spyOn(ElMessageBox, "prompt").mockResolvedValue({ value: "房产证.jpg" } as never);

    wrapper = mountPage();
    await flushPromises();
    await wrapper.get('[data-testid="attachment-rename"]').trigger("click");
    await flushPromises();

    expect(renameMock).toHaveBeenCalledWith("f1", "房产证.jpg");
  });

  it("shows the duplicate-name error when rename conflicts", async () => {
    renameMock.mockRejectedValue(
      new ApiError("同一挂载点下附件名字不可重复", "FILE_NAME_DUPLICATE", 409)
    );
    vi.spyOn(ElMessageBox, "prompt").mockResolvedValue({ value: "说明书.pdf" } as never);
    const errorSpy = vi.spyOn(ElMessage, "error").mockReturnValue({} as never);

    wrapper = mountPage();
    await flushPromises();
    await wrapper.get('[data-testid="attachment-rename"]').trigger("click");
    await flushPromises();

    expect(errorSpy).toHaveBeenCalledWith("同一挂载点下附件名字不可重复");
  });

  it("uploads a household attachment from the upload button", async () => {
    wrapper = mountPage();
    await flushPromises();

    const input = wrapper.find("input[type='file']");
    const file = new File(["x"], "户口本.jpg", { type: "image/jpeg" });
    Object.defineProperty(input.element, "files", { value: [file] });
    await input.trigger("change");
    await flushPromises();

    expect(uploadMock).toHaveBeenCalledWith(file);
  });

  it("moves an item attachment to household", async () => {
    wrapper = mountPage();
    await flushPromises();

    const moveButtons = wrapper.findAll("button").filter((b) => b.text().includes("移到家庭"));
    await moveButtons[0].trigger("click");
    await flushPromises();

    expect(remountMock).toHaveBeenCalledWith("f2");
  });

  it("moves a household attachment to an item via the target picker", async () => {
    const successSpy = vi.spyOn(ElMessage, "success").mockReturnValue({} as never);
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到物品");
    await pickMoveTarget("吸尘器", "i1");
    await wrapper.get('[data-testid="attachment-move-confirm"]').trigger("click");
    await flushPromises();

    expect(remountItemMock).toHaveBeenCalledWith("i1", "f1");
    expect(successSpy).toHaveBeenCalledWith("已移到物品");
    expect(listMock).toHaveBeenCalled();
  });

  it("moves a household attachment to a lot via the target picker", async () => {
    const successSpy = vi.spyOn(ElMessage, "success").mockReturnValue({} as never);
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到批次");
    await pickMoveTarget("吸尘器 · B2026-01", "l1");
    await wrapper.get('[data-testid="attachment-move-confirm"]').trigger("click");
    await flushPromises();

    expect(remountLotMock).toHaveBeenCalledWith("l1", "f1");
    expect(successSpy).toHaveBeenCalledWith("已移到批次");
    expect(listMock).toHaveBeenCalled();
  });

  it("warns to rename first when moving hits a duplicate name", async () => {
    remountItemMock.mockRejectedValue(
      new ApiError("同一挂载点下附件名字不可重复", "FILE_NAME_DUPLICATE", 409)
    );
    const warningSpy = vi.spyOn(ElMessage, "warning").mockReturnValue({} as never);
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到物品");
    await pickMoveTarget("吸尘器", "i1");
    await wrapper.get('[data-testid="attachment-move-confirm"]').trigger("click");
    await flushPromises();

    expect(warningSpy).toHaveBeenCalledWith("目标已有一份同名附件，请先改名再移动");
  });

  it("lists archived items with an archived badge in the item picker", async () => {
    fetchItemsMock.mockResolvedValue({
      items: [
        { id: "i1", name: "吸尘器", status: "ACTIVE" },
        { id: "i2", name: "旧冰箱", status: "ARCHIVED" }
      ] as never,
      total: 2,
      page: 1,
      pageSize: 1000
    });
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到物品");

    const options = wrapper.findAllComponents(ElOption);
    const archived = options.find((o) => o.props("value") === "i2");
    expect(archived).toBeDefined();
    expect(archived!.text()).toContain("旧冰箱");
    expect(archived!.text()).toContain("已归档");
  });

  it("shows an empty state when there are no items to move to", async () => {
    fetchItemsMock.mockResolvedValue({
      items: [] as never,
      total: 0,
      page: 1,
      pageSize: 1000
    });
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到物品");

    expect(wrapper.get('[data-testid="attachment-move-empty"]').text()).toContain("家庭还没有");
  });

  it("shows an empty state when there are no lots to move to", async () => {
    fetchLotsMock.mockResolvedValue({
      items: [] as never,
      total: 0,
      page: 1,
      pageSize: 1000
    });
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到批次");

    expect(wrapper.get('[data-testid="attachment-move-empty"]').text()).toContain("家庭还没有");
  });

  it("shows a load-failure hint instead of the empty state when target names fail to load", async () => {
    fetchItemsMock.mockRejectedValue(new Error("boom"));
    wrapper = mountPage();
    await flushPromises();

    await openMoveDialog("移到物品");

    expect(wrapper.get('[data-testid="attachment-move-empty"]').text()).toContain("加载失败");
  });

  function openMoveDialog(buttonText: string) {
    const button = wrapper!.findAll("button").find((b) => b.text().includes(buttonText));
    expect(button).toBeDefined();
    return button!.trigger("click").then(() => flushPromises());
  }

  function pickMoveTarget(optionLabel: string, value: string) {
    const moveSelect = wrapper!
      .findAllComponents({ name: "ElSelect" })
      .find((c) => c.attributes("data-testid") === "attachment-move-select");
    expect(moveSelect).toBeDefined();
    expect(moveSelect!.props("filterable")).toBe(true);
    const options = wrapper!.findAllComponents(ElOption);
    expect(options.find((o) => o.props("value") === value)?.text()).toContain(optionLabel);
    moveSelect!.vm.$emit("update:modelValue", value);
    return flushPromises();
  }
});
