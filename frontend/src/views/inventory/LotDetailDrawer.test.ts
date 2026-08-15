import ElementPlus, { ElMessage, ElMessageBox } from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import LotDetailDrawer from "./LotDetailDrawer.vue";
import { fetchLot, fetchMovements } from "../../api/inventory";
import {
  listLotAttachments,
  renameAttachment,
  deleteAttachment,
  remountAttachmentToHousehold,
  type Attachment,
} from "../../api/file";
import { ApiError } from "../../api/http";

vi.mock("../../api/inventory", () => ({
  fetchLot: vi.fn(),
  fetchMovements: vi.fn(),
  updateLotMeta: vi.fn(),
}));

vi.mock("../../api/file", () => ({
  listLotAttachments: vi.fn(),
  uploadLotAttachment: vi.fn(),
  renameAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
  remountAttachmentToHousehold: vi.fn(),
}));

const fetchLotMock = vi.mocked(fetchLot);
const fetchMovementsMock = vi.mocked(fetchMovements);
const listLotAttachmentsMock = vi.mocked(listLotAttachments);
const renameAttachmentMock = vi.mocked(renameAttachment);
const deleteAttachmentMock = vi.mocked(deleteAttachment);
const remountMock = vi.mocked(remountAttachmentToHousehold);

const receipt: Attachment = {
  id: "f1",
  name: "小票.jpg",
  mediaType: "image/jpeg",
  byteSize: 500,
  mountType: "LOT",
  mountId: "lot-1",
  createdAt: "2026-08-15T10:00:00Z",
  url: "/api/v1/files/f1/content",
};

const lot = {
  lotId: "lot-1",
  householdId: "h1",
  itemId: "item-1",
  itemName: "吸尘器",
  unitName: "个",
  totalQuantity: "2",
  purchaseDate: null,
  productionDate: null,
  expiryDate: null,
  lotNumber: "B2026-01",
  serialNumber: null,
  memo: null,
  version: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
  positions: [],
};

describe("LotDetailDrawer", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    fetchLotMock.mockReset().mockResolvedValue(lot as never);
    fetchMovementsMock.mockReset().mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 50 });
    listLotAttachmentsMock.mockReset().mockResolvedValue([receipt]);
    renameAttachmentMock.mockReset().mockResolvedValue(receipt);
    deleteAttachmentMock.mockReset().mockResolvedValue(receipt);
    remountMock.mockReset().mockResolvedValue(receipt);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.clearAllMocks();
  });

  function mountDrawer() {
    wrapper = mount(LotDetailDrawer, {
      props: { modelValue: true, lotId: "lot-1" },
      global: { plugins: [ElementPlus] },
    });
    return wrapper;
  }

  it("lists lot attachments on open", async () => {
    const w = mountDrawer();
    await flushPromises();

    expect(listLotAttachmentsMock).toHaveBeenCalledWith("lot-1");
    expect(w.text()).toContain("小票.jpg");
  });

  it("renames a lot attachment", async () => {
    vi.spyOn(ElMessageBox, "prompt").mockResolvedValue({ value: "小票-2026.jpg" } as never);
    const w = mountDrawer();
    await flushPromises();

    const renameBtn = w.findAll(".el-button").find((b) => b.text().includes("改名"));
    await renameBtn!.trigger("click");
    await flushPromises();

    expect(renameAttachmentMock).toHaveBeenCalledWith("f1", "小票-2026.jpg");
  });

  it("deletes a lot attachment after confirmation", async () => {
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const w = mountDrawer();
    await flushPromises();

    const deleteBtn = w.findAll(".el-button").filter((b) => b.text().includes("删除"))[0];
    await deleteBtn.trigger("click");
    await flushPromises();

    expect(deleteAttachmentMock).toHaveBeenCalledWith("f1");
  });

  it("moves a lot attachment to household", async () => {
    const w = mountDrawer();
    await flushPromises();

    const moveBtn = w.findAll(".el-button").find((b) => b.text().includes("移走"));
    await moveBtn!.trigger("click");
    await flushPromises();

    expect(remountMock).toHaveBeenCalledWith("f1");
  });

  it("shows the duplicate-name error when renaming conflicts", async () => {
    renameAttachmentMock.mockRejectedValue(
      new ApiError("同一挂载点下附件名字不可重复", "FILE_NAME_DUPLICATE", 409)
    );
    vi.spyOn(ElMessageBox, "prompt").mockResolvedValue({ value: "小票.jpg" } as never);
    const errorSpy = vi.spyOn(ElMessage, "error").mockReturnValue({} as never);
    const w = mountDrawer();
    await flushPromises();

    const renameBtn = w.findAll(".el-button").find((b) => b.text().includes("改名"));
    await renameBtn!.trigger("click");
    await flushPromises();

    expect(errorSpy).toHaveBeenCalledWith("同一挂载点下附件名字不可重复");
  });
});
