import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../../api/ai", () => ({
  askHouseholdQuestion: vi.fn(),
}));

const pushMock = vi.fn();
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
}));

import QaView from "../QaView.vue";
import { askHouseholdQuestion } from "../../api/ai";

const mockAsk = vi.mocked(askHouseholdQuestion);

const answerFixture = {
  question: "牛奶还有多少、放在哪里？",
  modelAvailable: true,
  reasonCode: "ANSWERED",
  summary: "牛奶当前库存 5 瓶，放在厨房。",
  dataTime: "2025-01-01T10:00:00Z",
  sources: [{ category: "HOUSEHOLD_FACT", label: "家庭事实", dataTime: "2025-01-01T10:00:00Z", available: true }],
  structuredResults: [
    {
      kind: "ITEM_STOCK",
      title: "「牛奶」库存分布",
      rows: [
        { 位置: "厨房", 批次号: "LOT-001", 数量: "5", 到期日: "2025-02-01" },
      ],
    },
  ],
  jumps: [
    { type: "ITEM", label: "牛奶", itemId: "item-1" },
    { type: "LOT", label: "LOT-001", itemId: "item-1", lotId: "lot-1" },
    { type: "LOCATION", label: "厨房", itemId: "item-1", lotId: "lot-1", locationId: "loc-1" },
  ],
};

const unavailableFixture = {
  question: "有牛奶吗？",
  modelAvailable: false,
  reasonCode: "AI_DISABLED",
  summary: "AI 模型当前不可用（AI_DISABLED），暂时无法确认家庭事实。",
  dataTime: "2025-01-01T10:00:00Z",
  sources: [],
  structuredResults: [],
  jumps: [],
};

function mountV() {
  return mount(QaView, { global: { plugins: [ElementPlus] } });
}

describe("QaView", () => {
  beforeEach(() => {
    pushMock.mockReset();
    mockAsk.mockReset();
  });

  it("renders empty state with composer", () => {
    const wrapper = mountV();
    expect(wrapper.text()).toContain("家庭问答");
    expect(wrapper.find(".qa-empty").exists()).toBe(true);
    expect(wrapper.find("textarea").exists()).toBe(true);
  });

  it("asks a question and renders summary, source, structured result and jumps", async () => {
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少、放在哪里？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    // 摘要
    expect(wrapper.text()).toContain("牛奶当前库存 5 瓶，放在厨房。");
    // 来源类别
    expect(wrapper.find(".qa-sources .zj-badge-pine").text()).toContain("家庭事实");
    // 数据时间
    expect(wrapper.text()).toContain("数据时间");
    // 结构化结果表格
    expect(wrapper.text()).toContain("「牛奶」库存分布");
    expect(wrapper.find(".qa-result-table").exists()).toBe(true);
    expect(wrapper.text()).toContain("厨房");
    expect(wrapper.text()).toContain("LOT-001");
    // 跳转
    expect(wrapper.findAll(".qa-jump").length).toBe(3);
    expect(mockAsk).toHaveBeenCalledWith("牛奶还有多少、放在哪里？");
  });

  it("jump buttons navigate to authoritative pages", async () => {
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶放在哪里？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    const jumps = wrapper.findAll(".qa-jump");
    await jumps[0].trigger("click");
    expect(pushMock).toHaveBeenCalledWith({ path: "/items", query: { highlight: "item-1" } });

    await jumps[1].trigger("click");
    expect(pushMock).toHaveBeenCalledWith({ name: "inventory", query: { lotId: "lot-1" } });

    await jumps[2].trigger("click");
    expect(pushMock).toHaveBeenCalledWith({ path: "/locations", query: { highlight: "loc-1" } });
  });

  it("renders unavailable answer with reason code and no fabricated results", async () => {
    mockAsk.mockResolvedValue(unavailableFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("有牛奶吗？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find(".qa-unavailable").exists()).toBe(true);
    expect(wrapper.text()).toContain("AI_DISABLED");
    expect(wrapper.text()).toContain("暂时无法确认");
    expect(wrapper.find(".qa-result").exists()).toBe(false);
    expect(wrapper.find(".qa-jump").exists()).toBe(false);
  });

  it("clears input after a successful question and keeps it in the thread", async () => {
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("哪些批次快到期了？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("");
    expect(wrapper.findAll(".qa-question-text")).toHaveLength(1);
  });

  it("shows error message on API failure and keeps input", async () => {
    mockAsk.mockRejectedValue(new Error("boom"));
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("查询会失败吗？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    // 失败不产生对话记录
    expect(wrapper.findAll(".qa-question-text")).toHaveLength(0);
  });
});
