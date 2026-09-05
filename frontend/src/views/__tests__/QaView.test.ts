import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import ElementPlus, { ElMessage } from "element-plus";

vi.mock("../../api/ai", () => ({
  askHouseholdQuestion: vi.fn(),
  fetchAiStatus: vi.fn(),
  fetchKnowledgeSources: vi.fn(),
}));

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
}));

vi.mock("../../api/inventory", () => ({
  fetchLots: vi.fn(),
}));

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}));

const pushMock = vi.fn();
const routeQuery: Record<string, string> = {};
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: routeQuery }),
}));

import QaView from "../QaView.vue";
import { askHouseholdQuestion, fetchAiStatus, fetchKnowledgeSources } from "../../api/ai";
import { fetchItems } from "../../api/catalog";
import { fetchLots } from "../../api/inventory";
import { fetchLocationTree } from "../../api/location";
import { ApiError } from "../../api/http";
import type { AiStatus, KnowledgeSourceInfo } from "../../types/ai";

const mockAsk = vi.mocked(askHouseholdQuestion);
const mockFetchAiStatus = vi.mocked(fetchAiStatus);
const mockFetchKnowledgeSources = vi.mocked(fetchKnowledgeSources);
const mockFetchItems = vi.mocked(fetchItems);
const mockFetchLots = vi.mocked(fetchLots);
const mockFetchLocationTree = vi.mocked(fetchLocationTree);

const availableStatusFixture: AiStatus = {
  available: true,
  reasonCode: "AVAILABLE",
  detail: "ready",
  providerId: "ollama",
  chatModel: "qwen",
  embeddingModel: "nomic",
  outboundEnabled: false,
  requestsPerMinute: 20,
  memberRequestsPerMinute: 10,
  maxContextTokens: 8192,
  maxConcurrentRequests: 2,
  requestTimeoutSeconds: 30,
};

function knowledgeSource(
  fileId: string,
  status: KnowledgeSourceInfo["status"],
  extra: Partial<KnowledgeSourceInfo> = {},
): KnowledgeSourceInfo {
  return {
    fileId,
    status,
    processingVersion: status === "AVAILABLE" ? 1 : 0,
    selectedAt: "2026-09-05T10:00:00Z",
    updatedAt: "2026-09-05T10:00:00Z",
    ...extra,
  };
}

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

const movementsFixture = {
  ...answerFixture,
  question: "牛奶最近流水？",
  structuredResults: [
    {
      kind: "MOVEMENTS",
      title: "「牛奶」最近流水",
      rows: [
        { 类型: "INBOUND", 数量: "12", 原因: "采购", 操作人: "家长", 时间: "2025-01-01T10:00:00Z", 从: "-", 到: "厨房" },
        { 类型: "CONSUME", 数量: "2", 原因: "早餐", 操作人: "家长", 时间: "2025-01-02T08:00:00Z", 从: "厨房", 到: "-" },
        { 类型: "LOSS", 数量: "1", 原因: "过期", 操作人: "家长", 时间: "2025-01-03T08:00:00Z", 从: "厨房", 到: "-" },
        { 类型: "ADJUSTMENT", 数量: "1", 原因: "盘点", 操作人: "家长", 时间: "2025-01-04T08:00:00Z", 从: "-", 到: "厨房" },
        { 类型: "TRANSFER", 数量: "3", 原因: "-", 操作人: "家长", 时间: "2025-01-05T08:00:00Z", 从: "厨房", 到: "阳台" },
        { 类型: "REVERSAL", 数量: "2", 原因: "冲正", 操作人: "家长", 时间: "2025-01-06T08:00:00Z", 从: "-", 到: "厨房" },
      ],
    },
  ],
};

function mountV() {
  return mount(QaView, { global: { plugins: [ElementPlus] } });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("QaView", () => {
  beforeEach(() => {
    sessionStorage.clear();
    pushMock.mockReset();
    mockAsk.mockReset();
    mockFetchAiStatus.mockReset();
    mockFetchKnowledgeSources.mockReset();
    mockFetchItems.mockReset();
    mockFetchLots.mockReset();
    mockFetchLocationTree.mockReset();
    mockFetchAiStatus.mockResolvedValue(availableStatusFixture);
    mockFetchKnowledgeSources.mockResolvedValue([]);
    for (const key of Object.keys(routeQuery)) delete routeQuery[key];
    mockFetchItems.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 });
    mockFetchLots.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 });
    mockFetchLocationTree.mockResolvedValue({ roots: [] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows Chinese AI unavailability on enter without asking first", async () => {
    mockFetchAiStatus.mockResolvedValue({
      ...availableStatusFixture,
      available: false,
      reasonCode: "AI_DISABLED",
      detail: "AI is disabled",
    });
    const wrapper = mountV();
    await flushPromises();

    const status = wrapper.get('[data-testid="qa-ai-status"]');
    expect(status.text()).toContain("不可用");
    expect(status.text()).toContain("已停用");
    expect(status.text()).not.toContain("AI_DISABLED");
    expect(wrapper.text()).toContain("知识问答不可用");
    expect(wrapper.text()).toContain("家庭事实兜底");
    expect(mockAsk).not.toHaveBeenCalled();

    mockAsk.mockResolvedValue(structuredFallbackFixture);
    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    expect(mockAsk).toHaveBeenCalled();
  });

  it("summarizes in-range knowledge preparation when opened with item context", async () => {
    routeQuery.contextType = "ITEM";
    routeQuery.contextId = "item-1";
    mockFetchKnowledgeSources.mockResolvedValue([
      knowledgeSource("f1", "PROCESSING", { mountType: "ITEM", mountId: "item-1" }),
      knowledgeSource("f2", "AVAILABLE", { mountType: "ITEM", mountId: "item-1" }),
      knowledgeSource("f3", "FAILED", { mountType: "ITEM", mountId: "item-1" }),
      knowledgeSource("f4", "AVAILABLE", { mountType: "ITEM", mountId: "item-other" }),
      knowledgeSource("f5", "PROCESSING", { mountType: "HOUSEHOLD", mountId: "hh-1" }),
      knowledgeSource("f6", "DISABLED", { mountType: "ITEM", mountId: "item-1" }),
    ]);
    const wrapper = mountV();
    await flushPromises();

    const prep = wrapper.get('[data-testid="qa-knowledge-prep"]');
    expect(prep.text()).toContain("知识准备状态");
    expect(prep.text()).toContain("处理中 1");
    expect(prep.text()).toContain("可用 1");
    expect(prep.text()).toContain("失败 1");
    expect(prep.text()).not.toContain("已停用");
    expect(mockFetchKnowledgeSources).toHaveBeenCalledOnce();
  });

  it("summarizes household-mounted knowledge sources when no item is selected", async () => {
    mockFetchKnowledgeSources.mockResolvedValue([
      knowledgeSource("h1", "AVAILABLE", { mountType: "HOUSEHOLD", mountId: "hh-1" }),
      knowledgeSource("h2", "PROCESSING", { mountType: "HOUSEHOLD", mountId: "hh-1" }),
      knowledgeSource("i1", "FAILED", { mountType: "ITEM", mountId: "item-1" }),
    ]);
    const wrapper = mountV();
    await flushPromises();

    expect(mockFetchKnowledgeSources).toHaveBeenCalledOnce();
    const prep = wrapper.get('[data-testid="qa-knowledge-prep"]').text();
    expect(prep).toContain("知识准备状态");
    expect(prep).toContain("处理中 1");
    expect(prep).toContain("可用 1");
    expect(prep).toContain("失败 0");
  });

  it("still allows household-fact questions when status APIs fail", async () => {
    routeQuery.contextType = "ITEM";
    routeQuery.contextId = "item-1";
    mockFetchAiStatus.mockRejectedValue(new ApiError("status down", "INTERNAL", 500));
    mockFetchKnowledgeSources.mockRejectedValue(new ApiError("ks down", "INTERNAL", 500));
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();
    await flushPromises();

    expect(wrapper.find('[data-testid="qa-ai-status"]').exists()).toBe(false);
    await wrapper.find("textarea").setValue("牛奶还有多少、放在哪里？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("牛奶还有多少、放在哪里？", {
      answerScope: "AUTO",
      pageContext: { type: "ITEM", id: "item-1" },
    }, expect.any(AbortSignal));
    expect(wrapper.text()).toContain("牛奶当前库存 5 瓶，放在厨房。");
  });

  it("refreshes knowledge preparation when the selected target changes", async () => {
    mockFetchItems.mockResolvedValue({
      items: [
        { id: "item-1", name: "咖啡机" } as never,
        { id: "item-2", name: "牛奶" } as never,
      ],
      total: 2,
      page: 1,
      pageSize: 100,
    });
    mockFetchKnowledgeSources
      .mockResolvedValueOnce([
        knowledgeSource("f1", "PROCESSING", { mountType: "ITEM", mountId: "item-1" }),
      ])
      .mockResolvedValueOnce([
        knowledgeSource("f2", "AVAILABLE", { mountType: "ITEM", mountId: "item-2" }),
        knowledgeSource("f3", "FAILED", { mountType: "ITEM", mountId: "item-2" }),
      ]);
    const wrapper = mountV();
    wrapper.get('[data-testid="qa-target-type"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "ITEM");
    await flushPromises();
    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", "item-1");
    await flushPromises();

    expect(wrapper.get('[data-testid="qa-knowledge-prep"]').text()).toContain("处理中 1");

    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", "item-2");
    await flushPromises();

    expect(wrapper.get('[data-testid="qa-knowledge-prep"]').text()).toContain("可用 1");
    expect(wrapper.get('[data-testid="qa-knowledge-prep"]').text()).toContain("失败 1");
    expect(mockFetchKnowledgeSources).toHaveBeenCalledTimes(2);
  });

  it("renders empty state with composer", () => {
    const wrapper = mountV();
    expect(wrapper.text()).toContain("家庭问答");
    expect(wrapper.find(".qa-empty").exists()).toBe(true);
    expect(wrapper.find("textarea").exists()).toBe(true);
    expect(wrapper.find(".qa-page").classes()).toContain("page-container");
    expect(wrapper.find(".qa-shell > .qa-composer").exists()).toBe(true);
  });

  it("fills the composer from a clickable empty-state example so the user can ask it", async () => {
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();
    const examples = wrapper.findAll('[data-testid="qa-example"]');
    expect(examples.map((chip) => chip.text())).toEqual([
      "牛奶还有多少？",
      "哪些批次快到期了？",
      "看看低库存物品",
      "牛奶最近有没有入库？",
      "滤网怎么清洁？",
    ]);

    await examples[0].trigger("click");
    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("牛奶还有多少？");

    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    expect(mockAsk).toHaveBeenCalledWith("牛奶还有多少？", { answerScope: "AUTO" }, expect.any(AbortSignal));
  });

  it("shows a waiting card instead of a blank thread while the first question is in flight", async () => {
    vi.useFakeTimers();
    const ask = deferred<typeof answerFixture>();
    mockAsk.mockReturnValue(ask.promise);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find(".qa-empty").exists()).toBe(false);
    const pending = wrapper.get('[data-testid="qa-pending"]');
    expect(pending.text()).toContain("牛奶还有多少？");
    expect(pending.text()).toContain("正在查阅账册");
    expect(wrapper.text()).not.toContain("已等待");
    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("");

    await vi.advanceTimersByTimeAsync(3000);
    expect(wrapper.text()).toContain("已等待 3 秒");

    ask.resolve(answerFixture);
    await flushPromises();

    expect(wrapper.find('[data-testid="qa-pending"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("牛奶当前库存 5 瓶，放在厨房。");
  });

  it("cancels an in-flight question without writing a turn or treating it as failure", async () => {
    const errorSpy = vi.spyOn(ElMessage, "error");
    const infoSpy = vi.spyOn(ElMessage, "info");
    mockAsk.mockImplementation((_question, _options, signal?: AbortSignal) => {
      return new Promise((_resolve, reject) => {
        const abort = () => {
          reject(new DOMException("The operation was aborted.", "AbortError"));
        };
        if (signal?.aborted) abort();
        else signal?.addEventListener("abort", abort, { once: true });
      });
    });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.get('[data-testid="qa-pending"]').text()).toContain("正在查阅账册");
    await wrapper.get('[data-testid="qa-cancel"]').trigger("click");
    await flushPromises();

    expect(wrapper.find('[data-testid="qa-pending"]').exists()).toBe(false);
    expect(wrapper.findAll(".qa-question-text")).toHaveLength(0);
    expect(wrapper.find(".qa-empty").exists()).toBe(true);
    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("牛奶还有多少？");
    expect(mockAsk.mock.calls[0][2]?.aborted).toBe(true);
    expect(errorSpy).not.toHaveBeenCalled();
    expect(infoSpy).toHaveBeenCalledWith("已取消");
  });

  it("appends a waiting card after existing turns for a follow-up question", async () => {
    mockAsk.mockResolvedValueOnce(answerFixture);
    const wrapper = mountV();
    await wrapper.find("textarea").setValue("牛奶还有多少、放在哪里？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    const followUp = deferred<typeof answerFixture>();
    mockAsk.mockReturnValue(followUp.promise);
    await wrapper.find("textarea").setValue("哪些批次快到期了？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.findAll(".qa-turn")).toHaveLength(2);
    expect(wrapper.get('[data-testid="qa-pending"]').text()).toContain("哪些批次快到期了？");
    expect(wrapper.text()).toContain("牛奶当前库存 5 瓶，放在厨房。");

    followUp.resolve(answerFixture);
    await flushPromises();
    expect(wrapper.find('[data-testid="qa-pending"]').exists()).toBe(false);
    expect(wrapper.findAll(".qa-turn")).toHaveLength(2);
  });

  it("scrolls new turns inside the conversation thread instead of the document", async () => {
    mockAsk.mockResolvedValueOnce(answerFixture);
    const wrapper = mountV();
    await wrapper.find("textarea").setValue("牛奶还有多少、放在哪里？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    const threadEl = wrapper.get('[data-testid="qa-thread"]').element as HTMLElement;
    Object.defineProperty(threadEl, "scrollHeight", { configurable: true, get: () => 2400 });
    threadEl.scrollTop = 12;

    const followUp = deferred<typeof answerFixture>();
    mockAsk.mockReturnValue(followUp.promise);
    await wrapper.find("textarea").setValue("哪些批次快到期了？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find(".qa-shell > .qa-composer").exists()).toBe(true);
    expect(threadEl.scrollTop).toBe(2400);
  });

  it("replaces the current answer with a waiting card while confirming a candidate", async () => {
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
    mockAsk.mockResolvedValueOnce(ambiguous);
    const wrapper = mountV();
    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    const confirmed = deferred<typeof answerFixture>();
    mockAsk.mockReturnValue(confirmed.promise);
    await wrapper.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();

    expect(wrapper.find('[data-testid="qa-candidate"]').exists()).toBe(false);
    expect(wrapper.findAll(".qa-question-text")).toHaveLength(1);
    expect(wrapper.get('[data-testid="qa-pending"]').text()).toContain("正在查阅账册");

    confirmed.resolve(answerFixture);
    await flushPromises();
    expect(wrapper.find('[data-testid="qa-pending"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("牛奶当前库存 5 瓶");
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
    }, expect.any(AbortSignal));
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

  it("renders unavailable answer with summary fallback and no fabricated results", async () => {
    mockAsk.mockResolvedValue(unavailableFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("有牛奶吗？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find(".qa-unavailable").exists()).toBe(true);
    expect(wrapper.find(".qa-unavailable .zj-badge").exists()).toBe(false);
    expect(wrapper.find(".qa-unavailable .qa-summary").text()).toContain("暂时无法确认");
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
    expect(wrapper.find("[data-testid='qa-fallback'] .zj-badge").text())
      .toBe("模型不可用，已返回可核对的家庭事实");
    expect(wrapper.text()).not.toContain("STRUCTURED_FACTS_FALLBACK");
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

    // 失败不产生对话记录，输入保留以便重试
    expect(wrapper.findAll(".qa-question-text")).toHaveLength(0);
    expect(wrapper.find(".qa-empty").exists()).toBe(true);
    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("查询会失败吗？");
  });

  it("does not post an empty scope id when Enter is pressed without a selected target", async () => {
    mockAsk.mockResolvedValue(knowledgeFixture);
    const wrapper = mountV();

    wrapper.get('[data-testid="qa-answer-scope"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "KNOWLEDGE_SOURCE");
    wrapper.get('[data-testid="qa-target-type"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "ITEM");
    await flushPromises();
    await wrapper.find("textarea").setValue("滤网怎么清洁？");
    const textarea = wrapper.find("textarea").element as HTMLTextAreaElement;
    textarea.dispatchEvent(new KeyboardEvent("keydown", {
      key: "Enter",
      code: "Enter",
      bubbles: true,
      cancelable: true,
    }));
    await flushPromises();

    expect(mockAsk).toHaveBeenCalled();
    expect(mockAsk).toHaveBeenCalledWith("滤网怎么清洁？", {
      answerScope: "KNOWLEDGE_SOURCE",
    }, expect.any(AbortSignal));
    for (const [, options] of mockAsk.mock.calls) {
      expect(options?.scope?.id ?? "missing").not.toBe("");
    }
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
    }, expect.any(AbortSignal));
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
    expect(grounding.text()).not.toContain("字符");
    expect(grounding.text()).not.toContain("120-148");
  });

  it("renders movement structured types with the same Chinese labels as the report page", async () => {
    mockAsk.mockResolvedValue(movementsFixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶最近流水？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    const table = wrapper.find(".qa-result-table");
    expect(table.exists()).toBe(true);
    expect(table.text()).toContain("入库");
    expect(table.text()).toContain("领用");
    expect(table.text()).toContain("报损");
    expect(table.text()).toContain("调整");
    expect(table.text()).toContain("移位");
    expect(table.text()).toContain("冲正");
    expect(table.text()).not.toContain("INBOUND");
    expect(table.text()).not.toContain("CONSUME");
    expect(table.text()).not.toContain("LOSS");
    expect(table.text()).not.toContain("ADJUSTMENT");
    expect(table.text()).not.toContain("TRANSFER");
    expect(table.text()).not.toContain("REVERSAL");
  });

  it.each([
    ["no source", noKnowledgeFixture, "NO_AVAILABLE_KNOWLEDGE_SOURCE", "当前范围没有可用的知识来源"],
    ["preparation failure", knowledgePreparationFailureFixture, "KNOWLEDGE_SOURCE_PREPARATION_FAILED", "知识来源准备失败"],
    ["model failure", knowledgeModelFailureFixture, "KNOWLEDGE_MODEL_UNAVAILABLE", "模型暂不可用"],
    ["generic model unavailable", { ...knowledgeModelFailureFixture, reasonCode: "MODEL_UNAVAILABLE" }, "MODEL_UNAVAILABLE", "模型暂不可用"],
    ["timeout", { ...knowledgeModelFailureFixture, reasonCode: "AI_QA_TIMEOUT" }, "AI_QA_TIMEOUT", "模型暂不可用"],
  ])("renders %s as a Chinese failure without the English reason code", async (_name, fixture, reason, label) => {
    mockAsk.mockResolvedValue(fixture);
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("咖啡机怎么清洁？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(wrapper.find(".qa-unavailable").exists()).toBe(true);
    expect(wrapper.find(".qa-unavailable .zj-badge").text()).toBe(label);
    expect(wrapper.text()).not.toContain(reason);
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
    }, expect.any(AbortSignal));
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
    expect(wrapper.text()).toContain("当前页面 · 咖啡机");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("这个物品怎么清洁？", {
      answerScope: "AUTO",
      pageContext: { type: "ITEM", id: "item-1", label: "咖啡机" },
    }, expect.any(AbortSignal));
  });

  it("asks household facts about a composer-selected location", async () => {
    mockFetchLocationTree.mockResolvedValue({
      roots: [
        {
          id: "loc-kitchen",
          parentId: null,
          name: "厨房",
          sortOrder: 0,
          everReferenced: false,
          version: 1,
          children: [
            {
              id: "loc-1",
              parentId: "loc-kitchen",
              name: "柜子",
              sortOrder: 0,
              everReferenced: false,
              version: 1,
              children: [],
            },
          ],
        },
      ],
    });
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();

    wrapper.get('[data-testid="qa-answer-scope"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "HOUSEHOLD_FACT");
    wrapper.get('[data-testid="qa-target-type"]')
      .findComponent({ name: "ElSegmented" }).vm.$emit("update:modelValue", "LOCATION");
    await flushPromises();
    wrapper.findComponent({ name: "ElSelect" }).vm.$emit("update:modelValue", "loc-1");
    await wrapper.find("textarea").setValue("这个位置还有什么？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockFetchLocationTree).toHaveBeenCalledOnce();
    expect(mockAsk).toHaveBeenCalledWith("这个位置还有什么？", {
      answerScope: "HOUSEHOLD_FACT",
      scope: { type: "LOCATION", id: "loc-1", label: "厨房 / 柜子" },
    }, expect.any(AbortSignal));
  });

  it("uses a labeled location page context when asking from a location page", async () => {
    routeQuery.contextType = "LOCATION";
    routeQuery.contextId = "loc-1";
    routeQuery.contextLabel = "厨房 / 柜子";
    mockAsk.mockResolvedValue(answerFixture);
    const wrapper = mountV();

    expect(wrapper.text()).toContain("当前页面 · 厨房 / 柜子");
    await wrapper.find("textarea").setValue("这个位置还有什么？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenCalledWith("这个位置还有什么？", {
      answerScope: "AUTO",
      pageContext: { type: "LOCATION", id: "loc-1", label: "厨房 / 柜子" },
    }, expect.any(AbortSignal));
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
    }, expect.any(AbortSignal));
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
    }, expect.any(AbortSignal));
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
    }, expect.any(AbortSignal));
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
    }, expect.any(AbortSignal));
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

  it("sends the last confirmed scope on a follow-up without an explicit composer scope", async () => {
    const ambiguous = {
      ...answerFixture,
      reasonCode: "AMBIGUOUS_TARGET",
      summary: "找到多个可能的对象，请先确认。",
      structuredResults: [],
      sources: [],
      jumps: [],
      candidates: [
        { type: "ITEM" as const, id: "item-1", label: "牛奶", detail: "物品 · 消耗品" },
        { type: "ITEM" as const, id: "item-2", label: "牛奶", detail: "物品 · 耐用品" },
      ],
    };
    mockAsk
      .mockResolvedValueOnce(ambiguous)
      .mockResolvedValueOnce(answerFixture)
      .mockResolvedValueOnce({ ...answerFixture, question: "那放在哪？", summary: "放在厨房。" });
    const wrapper = mountV();

    await wrapper.find("textarea").setValue("牛奶还有多少？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    await wrapper.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();

    await wrapper.find("textarea").setValue("那放在哪？");
    await wrapper.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenNthCalledWith(3, "那放在哪？", {
      answerScope: "AUTO",
      confirmedScopes: [{ type: "ITEM", id: "item-1", label: "牛奶" }],
    }, expect.any(AbortSignal));
  });

  it("keeps confirmed scopes on a follow-up after the view is remounted", async () => {
    const ambiguous = {
      ...answerFixture,
      reasonCode: "AMBIGUOUS_TARGET",
      summary: "找到多个可能的对象，请先确认。",
      structuredResults: [],
      sources: [],
      jumps: [],
      candidates: [
        { type: "ITEM" as const, id: "item-1", label: "牛奶", detail: "物品 · 消耗品" },
        { type: "ITEM" as const, id: "item-2", label: "牛奶", detail: "物品 · 耐用品" },
      ],
    };
    mockAsk
      .mockResolvedValueOnce(ambiguous)
      .mockResolvedValueOnce(answerFixture);
    const first = mountV();
    await first.find("textarea").setValue("牛奶还有多少？");
    await first.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    await first.findAll('[data-testid="qa-candidate"]')[0].trigger("click");
    await flushPromises();
    first.unmount();

    mockAsk.mockResolvedValueOnce({ ...answerFixture, question: "那放在哪？", summary: "放在厨房。" });
    const second = mountV();
    await second.find("textarea").setValue("那放在哪？");
    await second.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();

    expect(mockAsk).toHaveBeenLastCalledWith("那放在哪？", {
      answerScope: "AUTO",
      confirmedScopes: [{ type: "ITEM", id: "item-1", label: "牛奶" }],
    }, expect.any(AbortSignal));
  });

  it("restores the conversation after leaving the view and coming back", async () => {
    mockAsk.mockResolvedValue(answerFixture);
    const first = mountV();
    await first.find("textarea").setValue("牛奶还有多少、放在哪里？");
    await first.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    first.unmount();

    const second = mountV();
    expect(second.text()).toContain("牛奶还有多少、放在哪里？");
    expect(second.text()).toContain("牛奶当前库存 5 瓶，放在厨房。");
    expect(second.find(".qa-empty").exists()).toBe(false);
  });

  it("restores an unsent question draft after remount", async () => {
    const first = mountV();
    await first.find("textarea").setValue("那放在哪？");
    first.unmount();

    const second = mountV();
    expect((second.find("textarea").element as HTMLTextAreaElement).value).toBe("那放在哪？");
  });

  it("keeps the restored thread when returning via a page context query", async () => {
    mockAsk.mockResolvedValue(answerFixture);
    const first = mountV();
    await first.find("textarea").setValue("牛奶还有多少、放在哪里？");
    await first.find(".qa-composer-footer .el-button").trigger("click");
    await flushPromises();
    first.unmount();

    routeQuery.contextType = "ITEM";
    routeQuery.contextId = "item-1";
    const second = mountV();
    expect(second.text()).toContain("牛奶当前库存 5 瓶，放在厨房。");
    expect(second.find(".qa-empty").exists()).toBe(false);
  });
});
