import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus, { ElMessage } from "element-plus";

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
const routeQuery: Record<string, string> = {};
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: routeQuery }),
}));

import QaView from "../QaView.vue";
import { askHouseholdQuestion } from "../../api/ai";
import { fetchItems } from "../../api/catalog";
import { fetchLots } from "../../api/inventory";
import { ApiError } from "../../api/http";

const mockAsk = vi.mocked(askHouseholdQuestion);
const mockFetchItems = vi.mocked(fetchItems);
const mockFetchLots = vi.mocked(fetchLots);

const answerFixture = {
  question: "牛奶还有多少、放在哪里？",
  modelAvailable: true,
  reasonCode: "ANSWERED",
  summary: "牛奶当前库存 5 瓶，放在厨房。",
  dataTime: "2025-01-01T10:00:00Z",
  recommendedAnswerScope: "HOUSEHOLD_FACT" as const,
  usedAnswerScope: "HOUSEHOLD_FACT" as const,
  scopeReason: "根据问题内容推荐回答范围",
  candidates: [],
  answerParts: [],
  conflicts: [],
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
  recommendedAnswerScope: "HOUSEHOLD_FACT" as const,
  usedAnswerScope: "HOUSEHOLD_FACT" as const,
  scopeReason: "根据问题内容推荐回答范围",
  candidates: [],
  answerParts: [],
  conflicts: [],
  sources: [],
  structuredResults: [],
  jumps: [],
};

const structuredFallbackFixture = {
  ...answerFixture,
  modelAvailable: false,
  reasonCode: "STRUCTURED_FACTS_FALLBACK",
  summary: "AI 模型当前不可用，已返回可直接核对的家庭事实。",
};

const knowledgeFixture = {
  question: "咖啡机滤网怎么清洁？",
  modelAvailable: true,
  reasonCode: "ANSWERED",
  summary: "先取下滤网，用温水冲洗，晾干后装回。",
  dataTime: "2025-01-01T10:00:00Z",
  recommendedAnswerScope: "KNOWLEDGE_SOURCE" as const,
  usedAnswerScope: "KNOWLEDGE_SOURCE" as const,
  scopeReason: "已使用你确认的回答目标和来源范围",
  targetScope: { type: "ITEM" as const, id: "item-1", label: "咖啡机" },
  candidates: [],
  answerParts: [],
  conflicts: [],
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
    for (const key of Object.keys(routeQuery)) delete routeQuery[key];
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
    expect(mockAsk).toHaveBeenCalledWith("牛奶还有多少、放在哪里？", {
      answerScope: "AUTO",
    });
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

  it("renders structured facts when the model is unavailable", async () => {
    mockAsk.mockResolvedValue(structuredFallbackFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find("[data-testid='qa-fallback']").exists()).toBe(true);
    expect(wrapper.find(".qa-result-table").exists()).toBe(true);
    expect(wrapper.text()).toContain("AI 模型当前不可用");
    expect(wrapper.findAll(".qa-summary").filter(
      (summary) => summary.text().includes("AI 模型当前不可用")
    )).toHaveLength(1);
  });

  it("keeps the question out of the thread when the server rate-limits it", async () => {
    const errorSpy = vi.spyOn(ElMessage, "error");
    mockAsk.mockRejectedValue(new ApiError(
      "AI 请求受限",
      "AI_REQUEST_LIMITED",
      429,
      "request-44",
      undefined,
      "AI_MEMBER_RATE_LIMITED",
    ));
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.findAll(".qa-question-text")).toHaveLength(0);
    expect(errorSpy).toHaveBeenCalledWith("你的提问过于频繁，请稍后再试");
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

    wrapper.get('[data-testid="qa-answer-scope"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "KNOWLEDGE_SOURCE");
    wrapper.get('[data-testid="qa-target-type"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "ITEM");
    await flushPromises();
    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", "item-1");
    await wrapper.find("textarea").setValue("咖啡机滤网怎么清洁？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("咖啡机滤网怎么清洁？", {
      answerScope: "KNOWLEDGE_SOURCE",
      scope: { type: "ITEM", id: "item-1" },
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

    wrapper.get('[data-testid="qa-target-type"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "ITEM");
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

    wrapper.get('[data-testid="qa-target-type"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "LOT");
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

  it("recommends a mixed range and lets the user override the actual answer scope", async () => {
    mockAsk.mockResolvedValue({
      ...answerFixture,
      question: "咖啡机当前库存和说明书记录一致吗？",
      recommendedAnswerScope: "BOTH",
      usedAnswerScope: "HOUSEHOLD_FACT",
      scopeReason: "已使用你调整后的来源范围",
    });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("咖啡机当前库存和说明书记录一致吗？");
    expect(wrapper.get('[data-testid="qa-scope-recommendation"]').text()).toContain("两者");

    wrapper.get('[data-testid="qa-answer-scope"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "HOUSEHOLD_FACT");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("咖啡机当前库存和说明书记录一致吗？", {
      answerScope: "HOUSEHOLD_FACT",
    });
    expect(wrapper.get('[data-testid="qa-used-scope"]').text()).toContain("家庭事实");
    expect(wrapper.get('[data-testid="qa-used-scope"]').text()).toContain("推荐 两者");
  });

  it("uses the current business page context when auto scope is selected", async () => {
    routeQuery.contextType = "ITEM";
    routeQuery.contextId = "item-1";
    routeQuery.contextLabel = "咖啡机";
    mockAsk.mockResolvedValue(knowledgeFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("这个物品怎么清洁？");
    expect(wrapper.get('[data-testid="qa-scope-recommendation"]').text()).toContain("知识来源");
    expect(wrapper.text()).toContain("咖啡机");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("这个物品怎么清洁？", {
      answerScope: "AUTO",
      pageContext: { type: "ITEM", id: "item-1", label: "咖啡机" },
    });
  });

  it("uses item page context to recommend both sources for a neutral question", async () => {
    routeQuery.contextType = "ITEM";
    routeQuery.contextId = "item-1";
    mockAsk.mockResolvedValue({
      ...answerFixture,
      recommendedAnswerScope: "BOTH",
      usedAnswerScope: "BOTH",
    });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("这个呢？");
    expect(wrapper.get('[data-testid="qa-scope-recommendation"]').text()).toContain("两者");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("这个呢？", {
      answerScope: "AUTO",
      pageContext: { type: "ITEM", id: "item-1" },
    });
  });

  it("shows ambiguous candidates and retries only after the user confirms one", async () => {
    const ambiguous = {
      ...answerFixture,
      reasonCode: "AMBIGUOUS_TARGET",
      summary: "找到多个可能的对象，请先确认。",
      structuredResults: [],
      sources: [],
      jumps: [],
      recommendedAnswerScope: "HOUSEHOLD_FACT" as const,
      usedAnswerScope: "HOUSEHOLD_FACT" as const,
      candidates: [
        { type: "ITEM" as const, id: "item-1", label: "牛奶", detail: "物品 · 消耗品" },
        { type: "ITEM" as const, id: "item-2", label: "牛奶", detail: "物品 · 耐用品" },
      ],
    };
    mockAsk.mockResolvedValueOnce(ambiguous).mockResolvedValueOnce(answerFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.findAll('[data-testid="qa-candidate"]')).toHaveLength(2);
    expect(mockAsk).toHaveBeenCalledTimes(1);

    await wrapper.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenNthCalledWith(2, "牛奶还有多少？", {
      answerScope: "HOUSEHOLD_FACT",
      scope: { type: "ITEM", id: "item-1", label: "牛奶" },
    });
    expect(wrapper.find('[data-testid="qa-candidate"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("牛奶当前库存 5 瓶");
  });

  it("keeps prior confirmations while resolving multiple ambiguous groups", async () => {
    const itemAmbiguity = {
      ...answerFixture,
      reasonCode: "AMBIGUOUS_TARGET",
      usedAnswerScope: "BOTH" as const,
      candidates: [
        { type: "ITEM" as const, id: "item-1", label: "咖啡机", detail: "物品 · 耐用品" },
        { type: "ITEM" as const, id: "item-2", label: "咖啡机", detail: "物品 · 耐用品" },
      ],
    };
    const locationAmbiguity = {
      ...itemAmbiguity,
      candidates: [
        { type: "LOCATION" as const, id: "loc-1", label: "柜子", detail: "位置 · 厨房 / 柜子" },
        { type: "LOCATION" as const, id: "loc-2", label: "柜子", detail: "位置 · 客厅 / 柜子" },
      ],
    };
    mockAsk
      .mockResolvedValueOnce(itemAmbiguity)
      .mockResolvedValueOnce(locationAmbiguity)
      .mockResolvedValueOnce({ ...answerFixture, usedAnswerScope: "BOTH" });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("咖啡机在柜子里的库存和说明书要求是什么？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    await wrapper.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();
    await wrapper.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenNthCalledWith(3, "咖啡机在柜子里的库存和说明书要求是什么？", {
      answerScope: "BOTH",
      scope: { type: "LOCATION", id: "loc-1", label: "柜子" },
      confirmedScopes: [{ type: "ITEM", id: "item-1", label: "咖啡机" }],
    });
  });

  it("keeps item page context while confirming an ambiguous location", async () => {
    routeQuery.contextType = "ITEM";
    routeQuery.contextId = "item-1";
    routeQuery.contextLabel = "咖啡机";
    const locationAmbiguity = {
      ...answerFixture,
      reasonCode: "AMBIGUOUS_TARGET",
      usedAnswerScope: "BOTH" as const,
      candidates: [
        { type: "LOCATION" as const, id: "loc-1", label: "柜子", detail: "位置 · 厨房 / 柜子" },
        { type: "LOCATION" as const, id: "loc-2", label: "柜子", detail: "位置 · 客厅 / 柜子" },
      ],
    };
    mockAsk
      .mockResolvedValueOnce(locationAmbiguity)
      .mockResolvedValueOnce({ ...answerFixture, usedAnswerScope: "BOTH" });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("柜子里的库存和说明书要求是什么？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    await wrapper.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenNthCalledWith(2, "柜子里的库存和说明书要求是什么？", {
      answerScope: "BOTH",
      scope: { type: "LOCATION", id: "loc-1", label: "柜子" },
      confirmedScopes: [{ type: "ITEM", id: "item-1", label: "咖啡机" }],
    });
  });

  it("renders mixed source parts, an explicit conflict, and both authoritative jumps", async () => {
    mockAsk.mockResolvedValue({
      ...knowledgeFixture,
      summary: "家庭事实与知识来源分别如下。",
      recommendedAnswerScope: "BOTH",
      usedAnswerScope: "BOTH",
      answerParts: [
        { category: "HOUSEHOLD_FACT", label: "家庭事实", reasonCode: "ANSWERED",
          summary: "当前库存 0 台。", available: true },
        { category: "KNOWLEDGE_SOURCE", label: "知识来源", reasonCode: "ANSWERED",
          summary: "说明书记录库存 3 台。", available: true },
      ],
      sources: [
        { category: "HOUSEHOLD_FACT", label: "家庭事实", dataTime: "2025-01-01T10:00:00Z", available: true },
        { ...knowledgeFixture.sources[0], dataTime: "2025-01-02T10:00:00Z" },
      ],
      conflicts: [{ kind: "QUANTITY", factValue: "0", knowledgeValue: "3",
        note: "家庭事实与知识来源记录不一致" }],
    });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("咖啡机库存和说明书一致吗？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.findAll('[data-testid="qa-answer-part"]')).toHaveLength(2);
    expect(wrapper.text()).toContain("当前库存 0 台");
    expect(wrapper.text()).toContain("说明书记录库存 3 台");
    expect(wrapper.get('[data-testid="qa-conflict"]').text()).toContain("不一致");
    expect(wrapper.get('[data-testid="qa-conflict"]').text()).toContain("0");
    expect(wrapper.get('[data-testid="qa-conflict"]').text()).toContain("3");
    expect(wrapper.text()).toContain(new Date("2025-01-01T10:00:00Z")
      .toLocaleString("zh-CN", { hour12: false }));
    expect(wrapper.text()).toContain(new Date("2025-01-02T10:00:00Z")
      .toLocaleString("zh-CN", { hour12: false }));

    const jumps = wrapper.findAll(".qa-jump");
    await jumps[0].trigger("click");
    await jumps[1].trigger("click");
    expect(pushMock).toHaveBeenCalledWith({ path: "/items", query: { highlight: "item-1" } });
    expect(pushMock).toHaveBeenCalledWith({ path: "/files", query: { highlight: "file-1" } });
  });
});
