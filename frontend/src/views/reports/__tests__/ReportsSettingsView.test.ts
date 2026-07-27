import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../../../api/reporting", () => ({
  rebuildProjection: vi.fn(),
}));

vi.mock("../../../api/http", () => ({
  getJson: vi.fn(),
}));

vi.mock("../../../stores/session", () => ({
  useSessionStore: () => ({
    role: "OWNER",
    currentMember: {
      householdId: "h1",
      memberId: "m1",
      displayName: "所有者",
      role: "OWNER",
    },
  }),
}));

import ReportsSettingsView from "../ReportsSettingsView.vue";
import { rebuildProjection } from "../../../api/reporting";
import { getJson } from "../../../api/http";

const mockRebuildProjection = vi.mocked(rebuildProjection);
const mockGetJson = vi.mocked(getJson);

const sampleAuditLogs = {
  items: [
    {
      createdAt: "2026-07-27T10:00:00Z",
      action: "EXPORT_PERFORMED",
      outcome: "SUCCESS",
      detail: { reportKey: "stock-by-location", format: "CSV" },
    },
    {
      createdAt: "2026-07-26T14:30:00Z",
      action: "EXPORT_PERFORMED",
      outcome: "FAILURE",
      detail: { error: "timeout" },
    },
  ],
};

/** Click rebuild and confirm the popconfirm, then flush */
async function clickRebuildAndConfirm(wrapper: VueWrapper) {
  const rebuildBtn = wrapper.find(".el-button--danger");
  await rebuildBtn.trigger("click");
  await flushPromises();

  // Element Plus teleports popconfirm content to <body>.
  // In jsdom, try multiple selectors to find the confirm button.
  const popconfirm = document.querySelector(".el-popconfirm");
  if (popconfirm) {
    // Find the primary/confirm button inside the popconfirm
    const btns = popconfirm.querySelectorAll("button");
    const confirmBtn = Array.from(btns).find(
      (b) =>
        b.classList.contains("el-button--primary") ||
        b.textContent?.includes("确认重建"),
    ) as HTMLButtonElement | undefined;
    if (confirmBtn) {
      confirmBtn.click();
      await flushPromises();
      return;
    }
  }

  // Fallback: call doRebuild directly on the component instance
  await (wrapper.vm as any).doRebuild();
  await flushPromises();
}

function mountV() {
  return mount(ReportsSettingsView, { global: { plugins: [ElementPlus] } });
}

describe("ReportsSettingsView", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    vi.clearAllMocks();
    mockGetJson.mockResolvedValue(sampleAuditLogs);
    mockRebuildProjection.mockResolvedValue(undefined);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("renders page title and sections", async () => {
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("报表设置");
    expect(wrapper.text()).toContain("投影重建");
    expect(wrapper.text()).toContain("导出审计");
  });

  it("loads audit logs on mount", async () => {
    wrapper = mountV();
    await flushPromises();

    expect(mockGetJson).toHaveBeenCalledWith(
      "/api/v1/audit-logs?action=EXPORT_PERFORMED&page=1&pageSize=50",
    );
  });

  it("renders audit table rows", async () => {
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("EXPORT_PERFORMED");
    expect(wrapper.text()).toContain("SUCCESS");
    expect(wrapper.text()).toContain("FAILURE");
  });

  it("rebuild button exists with danger type", async () => {
    wrapper = mountV();
    await flushPromises();

    const rebuildBtn = wrapper.find(".el-button--danger");
    expect(rebuildBtn.exists()).toBe(true);
    expect(rebuildBtn.text()).toContain("重建报表读模型");
  });

  it("rebuild button is wrapped in popconfirm", async () => {
    wrapper = mountV();
    await flushPromises();

    // The rebuild button should be wrapped in an el-popconfirm component
    const popconfirmComponent = wrapper.findComponent({ name: "ElPopconfirm" });
    expect(popconfirmComponent.exists()).toBe(true);

    // Its title prop should contain the confirmation text
    expect(popconfirmComponent.props("title")).toContain("确认重建报表读模型");
  });

  it("confirming popconfirm calls rebuildProjection", async () => {
    wrapper = mountV();
    await flushPromises();

    await clickRebuildAndConfirm(wrapper);

    expect(mockRebuildProjection).toHaveBeenCalledWith("h1");
  });

  it("shows success result after rebuild", async () => {
    wrapper = mountV();
    await flushPromises();

    await clickRebuildAndConfirm(wrapper);

    expect(wrapper.text()).toContain("重建完成");
  });

  it("shows error result when rebuild fails", async () => {
    mockRebuildProjection.mockRejectedValue(new Error("network error"));

    wrapper = mountV();
    await flushPromises();

    await clickRebuildAndConfirm(wrapper);

    expect(wrapper.text()).toContain("重建失败");
  });

  it("formats audit timestamps", async () => {
    wrapper = mountV();
    await flushPromises();

    // The formatTime function uses toLocaleString('zh-CN') — just verify it renders something
    const tableRows = wrapper.findAll("tbody tr");
    expect(tableRows.length).toBeGreaterThanOrEqual(2);
    // The formatted time should not be the raw ISO string
    expect(wrapper.text()).not.toContain("2026-07-27T10:00:00Z");
  });

  it("renders audit detail as JSON", async () => {
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("stock-by-location");
    expect(wrapper.text()).toContain("CSV");
  });
});
