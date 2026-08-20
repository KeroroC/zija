import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import ElementPlus from "element-plus";

vi.mock("../../../api/reporting", () => ({
  getReport: vi.fn(),
  buildExportUrl: vi.fn(),
}));

vi.mock("../../../api/catalog", () => ({
  fetchItems: vi.fn(),
}));

vi.mock("../../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}));

vi.mock("../../../api/member", () => ({
  memberApi: {
    list: vi.fn(),
  },
}));

vi.mock("../../../stores/session", () => ({
  useSessionStore: () => ({
    role: "OWNER",
    currentMember: { householdId: "h1" },
  }),
}));

import MovementsView from "../MovementsView.vue";
import { getReport, buildExportUrl } from "../../../api/reporting";
import { fetchItems } from "../../../api/catalog";
import { fetchLocationTree } from "../../../api/location";
import { memberApi } from "../../../api/member";

const mockGetReport = vi.mocked(getReport);
const mockBuildExportUrl = vi.mocked(buildExportUrl);
const mockFetchItems = vi.mocked(fetchItems);
const mockFetchLocationTree = vi.mocked(fetchLocationTree);
const mockMemberList = vi.mocked(memberApi.list);

function row(overrides: Record<string, unknown> = {}) {
  return {
    movement_id: "m1",
    item_id: "i1",
    item_name: "面粉",
    type: "CONSUME",
    quantity_delta: 50,
    from_location_path: "家 > 厨房",
    to_location_path: null,
    operator_display_name: "所有者",
    reason: "做饭",
    reversal_of: null,
    business_time: "2026-01-01T10:00:00Z",
    created_at: "2026-01-01T10:00:00Z",
    ...overrides,
  };
}

function defaultMocks() {
  mockFetchItems.mockResolvedValue({
    items: [{ id: "i1", name: "面粉" } as any],
    total: 1,
    page: 1,
    pageSize: 1000,
  });
  mockFetchLocationTree.mockResolvedValue({
    roots: [{ id: "loc1", name: "厨房" } as any],
  });
  mockMemberList.mockResolvedValue([
    { accountId: "a1", displayName: "所有者" } as any,
  ] as any);
  mockGetReport.mockResolvedValue({
    items: [row()],
    total: 1,
    page: 1,
    pageSize: 20,
  });
  mockBuildExportUrl.mockReturnValue("/api/v1/reporting/exports/movements");
}

function mountV() {
  return mount(MovementsView, { global: { plugins: [ElementPlus] } });
}

describe("MovementsView 数量符号", () => {
  let wrapper: VueWrapper | null = null;
  let openSpy: any;

  beforeEach(() => {
    vi.clearAllMocks();
    defaultMocks();
    openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    openSpy.mockRestore();
  });

  it("CONSUME(领用) 数量显示为负号而非加号", async () => {
    mockGetReport.mockResolvedValue({
      items: [
        row({ type: "CONSUME", quantity_delta: 50, item_name: "面粉" }),
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("-50");
    expect(wrapper.text()).not.toContain("+50");
  });

  it("LOSS(报损) 数量显示为负号", async () => {
    mockGetReport.mockResolvedValue({
      items: [row({ type: "LOSS", quantity_delta: 5 })],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("-5");
  });

  it("INBOUND(入库) 数量显示为加号", async () => {
    mockGetReport.mockResolvedValue({
      items: [row({ type: "INBOUND", quantity_delta: 30 })],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("+30");
  });

  it("TRANSFER(移位) 数量显示原始数量不带强制符号", async () => {
    mockGetReport.mockResolvedValue({
      items: [
        row({
          type: "TRANSFER",
          quantity_delta: 8,
          from_location_path: "家 > 厨房",
          to_location_path: "家 > 储物间",
        }),
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    wrapper = mountV();
    await flushPromises();

    expect(wrapper.text()).toContain("8");
  });

  it("位置筛选以层级树的形式展示位置", async () => {
    mockFetchLocationTree.mockResolvedValue({
      roots: [
        {
          id: "root1",
          parentId: null,
          name: "家",
          sortOrder: 1,
          everReferenced: true,
          version: 0,
          children: [
            {
              id: "loc1",
              parentId: "root1",
              name: "厨房",
              sortOrder: 1,
              everReferenced: true,
              version: 0,
              children: [],
            },
          ],
        },
      ],
    } as any);
    wrapper = mountV();
    await flushPromises();

    const treeSelect = wrapper.findComponent({ name: "ElTreeSelect" });
    expect(treeSelect.exists()).toBe(true);
    // 树数据应保留层级关系：位置是父节点，叶子是子位置
    const data = treeSelect.props("data") as any[];
    expect(data[0].name).toBe("家");
    expect(data[0].children[0].name).toBe("厨房");
  });
});
