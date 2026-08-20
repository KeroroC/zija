import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import ElementPlus, { ElMessage } from "element-plus";

vi.mock("../../../api/reporting", () => ({
  searchReporting: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

import SearchView from "../SearchView.vue";
import { searchReporting } from "../../../api/reporting";
import { ApiError } from "../../../api/http";
import { REPORTING_INVALID_REQUEST } from "../../../types/errorCodes";

const mockSearchReporting = vi.mocked(searchReporting);

const sampleResults = {
  items: [
    {
      itemId: "i1",
      name: "纸巾",
      brand: "维达",
      tags: "",
      category: "日用品",
      unit: "包",
      matchedFields: ["name"],
    },
  ],
  lots: [
    {
      lotId: "l1",
      itemName: "纸巾",
      lotNumber: "LOT-001",
      serialNumber: "",
      matchedFields: ["lotNumber"],
    },
  ],
  locations: [
    {
      locationId: "loc1",
      name: "储物间",
      path: "家 > 储物间",
      matchedFields: ["name"],
    },
  ],
};

const emptyResults = { items: [], lots: [], locations: [] };

function mountV() {
  return mount(SearchView, { global: { plugins: [ElementPlus] } });
}

describe("SearchView", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    vi.useFakeTimers();
    mockSearchReporting.mockReset();
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.useRealTimers();
  });

  it("renders search input and button", () => {
    wrapper = mountV();
    expect(wrapper.find("input").exists()).toBe(true);
    expect(wrapper.find(".el-input-group__append").text()).toContain("搜索");
  });

  it("debounces live search on input by 300ms", async () => {
    mockSearchReporting.mockResolvedValue(sampleResults);
    wrapper = mountV();

    const input = wrapper.find("input");
    await input.setValue("纸巾");
    await flushPromises();

    // Not called yet (within debounce window)
    expect(mockSearchReporting).not.toHaveBeenCalled();

    // Advance past debounce
    vi.advanceTimersByTime(300);
    await flushPromises();

    expect(mockSearchReporting).toHaveBeenCalledWith("纸巾");
  });

  it("search button triggers doSearch immediately", async () => {
    mockSearchReporting.mockResolvedValue(sampleResults);
    wrapper = mountV();

    await wrapper.find("input").setValue("纸巾");
    await wrapper.find(".el-input-group__append .el-button").trigger("click");
    await flushPromises();

    expect(mockSearchReporting).toHaveBeenCalledWith("纸巾");
  });

  it("does not search when query is empty or whitespace", async () => {
    wrapper = mountV();

    await wrapper.find("input").setValue("  ");
    await wrapper.find("input").trigger("keyup.enter");
    await flushPromises();
    vi.advanceTimersByTime(300);
    await flushPromises();

    expect(mockSearchReporting).not.toHaveBeenCalled();
  });

  it("renders results in three collapse groups", async () => {
    mockSearchReporting.mockResolvedValue(sampleResults);
    wrapper = mountV();

    await wrapper.find("input").setValue("纸巾");
    await wrapper.find("input").trigger("keyup.enter");
    await flushPromises();

    // All three groups should be present
    expect(wrapper.text()).toContain("物品");
    expect(wrapper.text()).toContain("批次");
    expect(wrapper.text()).toContain("位置");

    // Results should be rendered
    expect(wrapper.text()).toContain("维达");
    expect(wrapper.text()).toContain("LOT-001");
    expect(wrapper.text()).toContain("储物间");
  });

  it("shows composed empty state when nothing matches", async () => {
    mockSearchReporting.mockResolvedValue(emptyResults);
    wrapper = mountV();

    await wrapper.find("input").setValue("不存在");
    await wrapper.find("input").trigger("keyup.enter");
    await flushPromises();

    const none = wrapper.find(".search-none");
    expect(none.exists()).toBe(true);
    expect(none.text()).toContain("没有找到相关内容");
  });

  it("clear resets results and searched flag", async () => {
    mockSearchReporting.mockResolvedValue(sampleResults);
    wrapper = mountV();

    await wrapper.find("input").setValue("纸巾");
    await wrapper.find("input").trigger("keyup.enter");
    await flushPromises();

    // Results are shown
    expect(wrapper.text()).toContain("维达");

    // Invoke clearResults directly via component instance
    (wrapper.vm as any).clearResults();
    await flushPromises();

    // Results should be hidden (searched=false)
    expect(wrapper.find(".search-results").exists()).toBe(false);
  });

  it("shows matched field tags", async () => {
    mockSearchReporting.mockResolvedValue(sampleResults);
    wrapper = mountV();

    await wrapper.find("input").setValue("纸巾");
    await wrapper.find("input").trigger("keyup.enter");
    await flushPromises();

    // Matched field tags should appear (mapped to Chinese labels)
    const tags = wrapper.findAll(".el-tag");
    const tagTexts = tags.map((t) => t.text());
    expect(tagTexts).toContain("名称");
    expect(tagTexts).toContain("批次号");
  });

  it("surfaces search API failure without an unhandled rejection (#31)", async () => {
    const errorSpy = vi.spyOn(ElMessage, "error").mockReturnValue({} as never);
    const unhandled: unknown[] = [];
    const onUnhandled = (reason: unknown) => {
      unhandled.push(reason);
    };
    process.on("unhandledRejection", onUnhandled);

    mockSearchReporting.mockRejectedValue(
      new ApiError("搜索服务暂时不可用", REPORTING_INVALID_REQUEST, 500),
    );
    wrapper = mountV();

    await wrapper.find("input").setValue("纸巾");
    await wrapper.find("input").trigger("keyup.enter");
    await flushPromises();
    await flushPromises();

    // User-visible error message from the backend problem title
    expect(errorSpy).toHaveBeenCalledWith("搜索服务暂时不可用");
    // loading must be reset even on failure
    expect((wrapper.vm as any).loading).toBe(false);
    // no unhandled rejection may escape doSearch()
    expect(unhandled).toHaveLength(0);

    process.off("unhandledRejection", onUnhandled);
    errorSpy.mockRestore();
  });
});
