import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../../api/ai", () => ({
  askHouseholdQuestion: vi.fn(),
}));

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
}));

vi.mock("../../api/inventory", () => ({
  fetchLots: vi.fn(),
}));

const pushMock = vi.fn();
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
}));

import QaView from "../QaView.vue";
import { askHouseholdQuestion } from "../../api/ai";
import { fetchItems } from "../../api/catalog";
import { fetchLots } from "../../api/inventory";

const mockAsk = vi.mocked(askHouseholdQuestion);
const mockFetchItems = vi.mocked(fetchItems);
const mockFetchLots = vi.mocked(fetchLots);

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

const knowledgeFixture = {
  question: "咖啡机滤网怎么清洁？",
  modelAvailable: true,
  reasonCode: "ANSWERED",
  summary: "先取下滤网，用温水冲洗，晾干后装回。",
  dataTime: "2025-01-01T10:00:00Z",
  sources: [
    {
      category: "KNOWLEDGE_SOURCE",
      label: "咖啡机说明书.pdf",
      dataTime: "2025-01-01T10:00:00Z",
      available: true,
      attachmentId: "file-1",
      attachmentName: "咖啡机说明书.pdf",
      attachmentUrl: "/api/v1/files/file-1/content",
      mountType: "ITEM" as const,
      mountId: "item-1",
      mountLabel: "咖啡机",
      pageNumber: 12,
      sectionPath: "维护/滤网清洁",
      excerpt: "清洁时先取下滤网，用温水冲洗并完全晾干后装回。",
      charStart: 120,
      charEnd: 148,
    },
  ],
  structuredResults: [],
  jumps: [
    { type: "ITEM", label: "咖啡机", itemId: "item-1" },
    { type: "ATTACHMENT", label: "咖啡机说明书.pdf", attachmentId: "file-1" },
  ],
};

const noKnowledgeFixture = {
  question: "咖啡机怎么清洁？",
  modelAvailable: true,
  reasonCode: "NO_AVAILABLE_KNOWLEDGE_SOURCE",
  summary: "当前范围没有可用的知识来源，请先到附件管理中选择或处理附件。",
  dataTime: "2025-01-01T10:00:00Z",
  sources: [],
  structuredResults: [],
  jumps: [{ type: "ATTACHMENT", label: "附件管理" }],
};

const knowledgeModelFailureFixture = {
  ...noKnowledgeFixture,
  modelAvailable: false,
  reasonCode: "KNOWLEDGE_MODEL_UNAVAILABLE",
  summary: "AI 模型暂时无法依据资料生成回答，请稍后重试或查看附件。",
  jumps: [{ type: "ATTACHMENT", label: "咖啡机说明书.pdf", attachmentId: "file-1" }],
};

const knowledgePreparationFailureFixture = {
  ...noKnowledgeFixture,
  reasonCode: "KNOWLEDGE_SOURCE_PREPARATION_FAILED",
  summary: "知识来源「咖啡机说明书.pdf」解析失败：扫描版 PDF 无法提取文字。请到附件管理中处理或重试。",
  jumps: [{ type: "ATTACHMENT", label: "咖啡机说明书.pdf", attachmentId: "file-1" }],
};

function mountV() {
  return mount(QaView, { global: { plugins: [ElementPlus] } });
}

describe("QaView", () => {
  beforeEach(() => {
    pushMock.mockReset();
    mockAsk.mockReset();
    mockFetchItems.mockReset();
    mockFetchLots.mockReset();
    mockFetchItems.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 });
    mockFetchLots.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 });
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

  it("asks knowledge questions with an explicit item scope", async () => {
    mockFetchItems.mockResolvedValue({
      items: [{ id: "item-1", name: "咖啡机" } as never],
      total: 1,
      page: 1,
      pageSize: 100,
    });
    mockAsk.mockResolvedValue(knowledgeFixture);
    const wrapper = mountV();

    wrapper.findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "ITEM");
    await flushPromises();
    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", "item-1");
    await wrapper.find("textarea").setValue("咖啡机滤网怎么清洁？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("咖啡机滤网怎么清洁？", {
      type: "ITEM",
      id: "item-1",
    });
  });

  it("loads every item page so any item can be selected", async () => {
    mockFetchItems
      .mockResolvedValueOnce({
        items: Array.from({ length: 100 }, (_, index) => ({
          id: `item-${index + 1}`,
          name: `物品 ${index + 1}`,
        } as never)),
        total: 101,
        page: 1,
        pageSize: 100,
      })
      .mockResolvedValueOnce({
        items: [{ id: "item-101", name: "物品 101" } as never],
        total: 101,
        page: 2,
        pageSize: 100,
      });
    const wrapper = mountV();

    wrapper.findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "ITEM");
    await flushPromises();

    expect(mockFetchItems).toHaveBeenNthCalledWith(1, { page: 1, pageSize: 100 });
    expect(mockFetchItems).toHaveBeenNthCalledWith(2, { page: 2, pageSize: 100 });
    expect(wrapper.findAllComponents({ name: "ElOption" })).toHaveLength(101);
  });

  it("loads every lot page so any lot can be selected", async () => {
    mockFetchLots
      .mockResolvedValueOnce({
        items: Array.from({ length: 100 }, (_, index) => ({
          lotId: `lot-${index + 1}`,
          itemName: "咖啡机",
          lotNumber: `LOT-${index + 1}`,
          serialNumber: null,
        } as never)),
        total: 101,
        page: 1,
        pageSize: 100,
      })
      .mockResolvedValueOnce({
        items: [{
          lotId: "lot-101",
          itemName: "咖啡机",
          lotNumber: "LOT-101",
          serialNumber: null,
        } as never],
        total: 101,
        page: 2,
        pageSize: 100,
      });
    const wrapper = mountV();

    wrapper.findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "LOT");
    await flushPromises();

    expect(mockFetchLots).toHaveBeenNthCalledWith(1, { page: 1, pageSize: 100 });
    expect(mockFetchLots).toHaveBeenNthCalledWith(2, { page: 2, pageSize: 100 });
    expect(wrapper.findAllComponents({ name: "ElOption" })).toHaveLength(101);
  });

  it("renders attachment, mount and locatable excerpt for knowledge evidence", async () => {
    mockAsk.mockResolvedValue(knowledgeFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("咖啡机滤网怎么清洁？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    const grounding = wrapper.find(".qa-grounding");
    expect(grounding.exists()).toBe(true);
    expect(grounding.text()).toContain("咖啡机说明书.pdf");
    expect(grounding.text()).toContain("咖啡机");
    expect(grounding.text()).toContain("第 12 页");
    expect(grounding.text()).toContain("维护/滤网清洁");
    expect(grounding.text()).toContain("清洁时先取下滤网");
  });

  it.each([
    ["no source", noKnowledgeFixture, "NO_AVAILABLE_KNOWLEDGE_SOURCE"],
    ["preparation failure", knowledgePreparationFailureFixture, "KNOWLEDGE_SOURCE_PREPARATION_FAILED"],
    ["model failure", knowledgeModelFailureFixture, "KNOWLEDGE_MODEL_UNAVAILABLE"],
  ])("renders %s as a safe failure with an attachment entry", async (_name, fixture, reason) => {
    mockAsk.mockResolvedValue(fixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("咖啡机怎么清洁？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find(".qa-unavailable").exists()).toBe(true);
    expect(wrapper.text()).toContain(reason);
    expect(wrapper.find("[data-testid='qa-attachment-entry']").exists()).toBe(true);
    expect(wrapper.find(".qa-grounding").exists()).toBe(false);
  });
});
