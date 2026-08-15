import ElementPlus, { ElMessage, ElMessageBox } from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AttachmentsPage from "./AttachmentsPage.vue";
import { listAttachments, renameAttachment, uploadHouseholdAttachment } from "../api/file";
import { ApiError } from "../api/http";

vi.mock("../api/file", () => ({
  listAttachments: vi.fn(),
  uploadHouseholdAttachment: vi.fn(),
  renameAttachment: vi.fn()
}));

const listMock = vi.mocked(listAttachments);
const uploadMock = vi.mocked(uploadHouseholdAttachment);
const renameMock = vi.mocked(renameAttachment);

describe("AttachmentsPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    listMock.mockResolvedValue({
      items: [
        {
          id: "f1",
          name: "户口本.jpg",
          mediaType: "image/jpeg",
          byteSize: 2048,
          mountType: "HOUSEHOLD",
          mountId: "h1",
          createdAt: "2026-08-15T10:00:00Z",
          url: "/api/v1/files/f1/content"
        }
      ],
      total: 1,
      page: 1,
      pageSize: 50
    });
    uploadMock.mockResolvedValue({
      id: "f2",
      storageKey: "k",
      originalFilename: "说明书.pdf",
      detectedMediaType: "application/pdf",
      byteSize: 100,
      sha256: "abc",
      url: "/api/v1/files/f2/content",
      version: 0
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.clearAllMocks();
  });

  it("renders household attachments returned by the API", async () => {
    wrapper = mount(AttachmentsPage, {
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(listMock).toHaveBeenCalled();
    expect(wrapper.text()).toContain("附件");
    expect(wrapper.text()).toContain("户口本.jpg");
    expect(wrapper.text()).toContain("image/jpeg");
  });

  it("renames an attachment from the row action", async () => {
    renameMock.mockResolvedValue({
      id: "f1",
      name: "房产证.jpg",
      mediaType: "image/jpeg",
      byteSize: 2048,
      mountType: "HOUSEHOLD",
      mountId: "h1",
      createdAt: "2026-08-15T10:00:00Z",
      url: "/api/v1/files/f1/content"
    });
    vi.spyOn(ElMessageBox, "prompt").mockResolvedValue({ value: "房产证.jpg" } as never);
    listMock.mockResolvedValueOnce({
      items: [
        {
          id: "f1",
          name: "户口本.jpg",
          mediaType: "image/jpeg",
          byteSize: 2048,
          mountType: "HOUSEHOLD",
          mountId: "h1",
          createdAt: "2026-08-15T10:00:00Z",
          url: "/api/v1/files/f1/content"
        }
      ],
      total: 1,
      page: 1,
      pageSize: 50
    }).mockResolvedValueOnce({
      items: [
        {
          id: "f1",
          name: "房产证.jpg",
          mediaType: "image/jpeg",
          byteSize: 2048,
          mountType: "HOUSEHOLD",
          mountId: "h1",
          createdAt: "2026-08-15T10:00:00Z",
          url: "/api/v1/files/f1/content"
        }
      ],
      total: 1,
      page: 1,
      pageSize: 50
    });

    wrapper = mount(AttachmentsPage, {
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    await wrapper.get('[data-testid="attachment-rename"]').trigger("click");
    await flushPromises();

    expect(renameMock).toHaveBeenCalledWith("f1", "房产证.jpg");
    expect(wrapper.text()).toContain("房产证.jpg");
  });

  it("shows the duplicate-name error when rename conflicts", async () => {
    renameMock.mockRejectedValue(
      new ApiError("同一挂载点下附件名字不可重复", "FILE_NAME_DUPLICATE", 409)
    );
    vi.spyOn(ElMessageBox, "prompt").mockResolvedValue({ value: "说明书.pdf" } as never);
    const errorSpy = vi.spyOn(ElMessage, "error").mockReturnValue({} as never);

    wrapper = mount(AttachmentsPage, {
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    await wrapper.get('[data-testid="attachment-rename"]').trigger("click");
    await flushPromises();

    expect(errorSpy).toHaveBeenCalledWith("同一挂载点下附件名字不可重复");
  });
});
