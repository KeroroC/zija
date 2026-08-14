import ElementPlus, { ElMessage, ElMessageBox } from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  fetchStocktakes,
  createStocktake,
  fetchStocktake,
  updateStocktakeDraft,
  refreshStocktakeDraft,
  confirmStocktake,
  cancelStocktake,
  fetchLots,
} from "../../api/inventory"
import { fetchLocationTree } from "../../api/location"
import { memberApi } from "../../api/member"
import StocktakesTab from "./StocktakesTab.vue"
import StocktakeDialog from "./StocktakeDialog.vue"
import type {
  StocktakeSummary,
  StocktakeListResponse,
  StocktakeDetail,
  LotListResponse,
} from "../../types/inventory"
import type { LocationTree } from "../../types/location"
import type { MemberInfo } from "../../types/identity"

vi.mock("../../api/inventory", () => ({
  fetchStocktakes: vi.fn(),
  createStocktake: vi.fn(),
  fetchStocktake: vi.fn(),
  updateStocktakeDraft: vi.fn(),
  refreshStocktakeDraft: vi.fn(),
  confirmStocktake: vi.fn(),
  cancelStocktake: vi.fn(),
  fetchLots: vi.fn(),
}))

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}))

vi.mock("../../api/member", () => ({
  memberApi: {
    list: vi.fn(),
  },
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock("../../stores/session", () => ({
  useSessionStore: () => ({
    role: "OWNER",
    currentMember: {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "owner",
      displayName: "所有者",
      role: "OWNER",
      status: "ACTIVE",
    },
    logout: vi.fn(),
    clearLocalSession: vi.fn(),
  }),
}))

const fetchStocktakesMock = vi.mocked(fetchStocktakes)
const createStocktakeMock = vi.mocked(createStocktake)
const fetchStocktakeMock = vi.mocked(fetchStocktake)
const updateStocktakeDraftMock = vi.mocked(updateStocktakeDraft)
const refreshStocktakeDraftMock = vi.mocked(refreshStocktakeDraft)
const confirmStocktakeMock = vi.mocked(confirmStocktake)
const cancelStocktakeMock = vi.mocked(cancelStocktake)
const fetchLotsMock = vi.mocked(fetchLots)
const fetchLocationTreeMock = vi.mocked(fetchLocationTree)
const memberListMock = vi.mocked(memberApi.list)

const members: MemberInfo[] = [
  {
    id: "m1",
    accountId: "a1",
    username: "owner",
    displayName: "所有者",
    role: "OWNER",
    status: "ACTIVE",
  },
]

const locationTree: LocationTree = {
  roots: [
    {
      id: "loc-1",
      parentId: null,
      name: "家",
      sortOrder: 0,
      everReferenced: true,
      version: 1,
      children: [
        {
          id: "loc-2",
          parentId: "loc-1",
          name: "卧室",
          sortOrder: 1,
          everReferenced: false,
          version: 1,
          children: [],
        },
      ],
    },
  ],
}

const draftStocktake: StocktakeSummary = {
  id: "st-1",
  status: "DRAFT",
  createdBy: "a1",
  createdAt: "2026-07-25T10:00:00Z",
  completedAt: null,
  version: 1,
}

const completedStocktake: StocktakeSummary = {
  id: "st-2",
  status: "COMPLETED",
  createdBy: "a1",
  createdAt: "2026-07-24T10:00:00Z",
  completedAt: "2026-07-24T11:00:00Z",
  version: 2,
}

const cancelledStocktake: StocktakeSummary = {
  id: "st-3",
  status: "CANCELLED",
  createdBy: "a1",
  createdAt: "2026-07-23T10:00:00Z",
  completedAt: null,
  version: 1,
}

const stocktakeListResponse: StocktakeListResponse = {
  items: [draftStocktake, completedStocktake, cancelledStocktake],
  total: 3,
  page: 1,
  pageSize: 20,
}

const stocktakeDetail: StocktakeDetail = {
  id: "st-1",
  status: "DRAFT",
  createdBy: "a1",
  createdAt: "2026-07-25T10:00:00Z",
  completedAt: null,
  version: 1,
  items: [
    {
      lotId: "lot-1",
      locationId: "loc-1",
      bookQuantity: "5",
      actualQuantity: "5",
      reason: null,
      itemName: "大米",
      lotNumber: "LOT-001",
      unitName: "袋",
    },
    {
      lotId: "lot-2",
      locationId: "loc-1",
      bookQuantity: "10",
      actualQuantity: "10",
      reason: null,
      itemName: "酱油",
      lotNumber: null,
      unitName: "瓶",
    },
  ],
}

const lotsResponse: LotListResponse = {
  items: [
    {
      lotId: "lot-3",
      itemId: "item-3",
      itemName: "牙膏",
      unitName: "支",
      totalQuantity: "0",
      purchaseDate: null,
      productionDate: null,
      expiryDate: null,
      lotNumber: "LOT-003",
      serialNumber: null,
      memo: null,
      positions: [{ locationId: "loc-1", locationName: "家", quantity: "0", revision: 0 }],
      version: 1,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 1000,
}

describe("StocktakesTab", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    fetchStocktakesMock.mockReset().mockResolvedValue(stocktakeListResponse)
    fetchLocationTreeMock.mockReset().mockResolvedValue(locationTree)
    memberListMock.mockReset().mockResolvedValue(members)
    createStocktakeMock.mockReset()
    fetchStocktakeMock.mockReset()
    updateStocktakeDraftMock.mockReset()
    refreshStocktakeDraftMock.mockReset()
    confirmStocktakeMock.mockReset()
    cancelStocktakeMock.mockReset()
    fetchLotsMock.mockReset().mockResolvedValue(lotsResponse)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountAndWait() {
    wrapper = mount(StocktakesTab, { global: { plugins: [ElementPlus] } })
    await flushPromises()
  }

  it("renders table with stocktake data on mount", async () => {
    await mountAndWait()

    expect(fetchStocktakesMock).toHaveBeenCalledOnce()
    expect(wrapper!.text()).toContain("草稿")
    expect(wrapper!.text()).toContain("已完成")
    expect(wrapper!.text()).toContain("已取消")
  })

  it("loads operator name map on mount", async () => {
    await mountAndWait()

    expect(memberListMock).toHaveBeenCalledOnce()
  })

  it("displays status tags with correct labels", async () => {
    await mountAndWait()

    const tags = wrapper!.findAll(".el-tag")
    const tagTexts = tags.map((t) => t.text())
    expect(tagTexts).toContain("草稿")
    expect(tagTexts).toContain("已完成")
    expect(tagTexts).toContain("已取消")
  })

  it("displays formatted timestamps", async () => {
    await mountAndWait()

    expect(wrapper!.text()).toContain("2026-07-25 10:00:00")
    expect(wrapper!.text()).toContain("2026-07-24 11:00:00")
  })

  it("displays operator names", async () => {
    await mountAndWait()

    expect(wrapper!.text()).toContain("所有者")
  })

  it("shows pagination", async () => {
    fetchStocktakesMock.mockResolvedValueOnce({
      items: [draftStocktake],
      total: 50,
      page: 1,
      pageSize: 20,
    })

    await mountAndWait()

    expect(wrapper!.find(".el-pagination").exists()).toBe(true)
    expect(wrapper!.text()).toContain("50")
  })

  it("filters by status when status select changes", async () => {
    await mountAndWait()

    fetchStocktakesMock.mockResolvedValueOnce({
      items: [draftStocktake],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    expect(selects.length).toBeGreaterThanOrEqual(1)
    await selects[0].vm.$emit("update:modelValue", "DRAFT")
    await selects[0].vm.$emit("change", "DRAFT")
    await flushPromises()

    expect(fetchStocktakesMock).toHaveBeenCalledTimes(2)
    const lastCall =
      fetchStocktakesMock.mock.calls[fetchStocktakesMock.mock.calls.length - 1][0]
    expect(lastCall?.status).toBe("DRAFT")
  })

  it("resets to page 1 when filter changes", async () => {
    await mountAndWait()

    fetchStocktakesMock.mockResolvedValueOnce({
      items: [draftStocktake],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "COMPLETED")
    await selects[0].vm.$emit("change", "COMPLETED")
    await flushPromises()

    const lastCall =
      fetchStocktakesMock.mock.calls[fetchStocktakesMock.mock.calls.length - 1][0]
    expect(lastCall?.page).toBe(1)
  })

  it("opens create dialog when 发起盘点 button is clicked", async () => {
    await mountAndWait()

    const btn = wrapper!.findAll("button").find((b) => b.text().includes("发起盘点"))
    expect(btn).toBeDefined()
    await btn!.trigger("click")
    await flushPromises()

    // Dialog should be visible
    expect(wrapper!.findComponent(StocktakeDialog).exists()).toBe(true)
  })

  it("opens edit dialog when draft row is clicked", async () => {
    await mountAndWait()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows.length).toBeGreaterThan(0)
    await rows[0].trigger("click") // draftStocktake is first
    await flushPromises()

    expect(wrapper!.findComponent(StocktakeDialog).exists()).toBe(true)
  })

  it("does not open dialog when completed row is clicked", async () => {
    await mountAndWait()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows.length).toBeGreaterThan(0)
    await rows[1].trigger("click") // completedStocktake
    await flushPromises()

    // Dialog should not be visible for completed stocktakes
    const dialog = wrapper!.findComponent(StocktakeDialog)
    expect(dialog.props("modelValue")).toBe(false)
  })

  it("shows empty state when no stocktakes", async () => {
    fetchStocktakesMock.mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })

    await mountAndWait()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows).toHaveLength(0)
  })
})

describe("StocktakeDialog", () => {
  beforeEach(() => {
    fetchLocationTreeMock.mockReset().mockResolvedValue(locationTree)
    createStocktakeMock.mockReset()
    fetchStocktakeMock.mockReset()
    updateStocktakeDraftMock.mockReset()
    refreshStocktakeDraftMock.mockReset()
    confirmStocktakeMock.mockReset()
    cancelStocktakeMock.mockReset()
    fetchLotsMock.mockReset().mockResolvedValue(lotsResponse)
  })

  async function mountDialog(
    stocktakeId: string | null = null,
    startStep = 0,
  ) {
    const wrapper = mount(StocktakeDialog, {
      props: {
        modelValue: true,
        stocktakeId,
        startStep,
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    return wrapper
  }

  it("shows step 0 with location selector on create", async () => {
    const wrapper = await mountDialog(null, 0)
    await flushPromises()

    expect(wrapper.text()).toContain("选择位置")
    expect(wrapper.findComponent({ name: "ElTreeSelect" }).exists()).toBe(true)
    wrapper.unmount()
  })

  it("creates stocktake when create button is clicked", async () => {
    createStocktakeMock.mockResolvedValue({ id: "st-new" })
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const wrapper = await mountDialog(null, 0)
    await flushPromises()

    // Select location
    const treeSelect = wrapper.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await flushPromises()

    // Click create
    const createBtn = wrapper.findAll("button").find((b) => b.text().includes("创建盘点"))
    expect(createBtn).toBeDefined()
    await createBtn!.trigger("click")
    await flushPromises()

    expect(createStocktakeMock).toHaveBeenCalledWith({ locationId: "loc-1" })
    expect(fetchStocktakeMock).toHaveBeenCalledWith("st-new")
    wrapper.unmount()
  })

  it("shows items table in step 1", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const wrapper = await mountDialog("st-1", 1)
    await flushPromises()

    expect(wrapper.text()).toContain("物品名称")
    expect(wrapper.text()).toContain("批次号")
    expect(wrapper.text()).toContain("账面数量")
    expect(wrapper.text()).toContain("实际数量")
    expect(wrapper.text()).toContain("单位")
    expect(wrapper.text()).toContain("差异原因")
    // Display fields from enriched detail
    expect(wrapper.text()).toContain("大米")
    expect(wrapper.text()).toContain("LOT-001")
    expect(wrapper.text()).toContain("酱油")
    expect(wrapper.text()).toContain("瓶")
    // Batch null falls back to "—"
    expect(wrapper.text()).toContain("—")
    expect(wrapper.text()).toContain("5")
    expect(wrapper.text()).toContain("10")
    wrapper.unmount()
  })

  it("shows enriched detail columns on confirm preview (step 2)", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const wrapper = await mountDialog("st-1", 2)
    await flushPromises()

    expect(wrapper.text()).toContain("物品名称")
    expect(wrapper.text()).toContain("批次号")
    expect(wrapper.text()).toContain("单位")
    expect(wrapper.text()).toContain("账面")
    expect(wrapper.text()).toContain("实际")
    expect(wrapper.text()).toContain("差异")
    expect(wrapper.text()).toContain("大米")
    expect(wrapper.text()).toContain("LOT-001")
    expect(wrapper.text()).toContain("酱油")
    expect(wrapper.text()).toContain("瓶")
    wrapper.unmount()
  })

  it("saves draft with updated items", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)
    updateStocktakeDraftMock.mockResolvedValue({ status: "ok" })

    const wrapper = await mountDialog("st-1", 1)
    await flushPromises()

    // Click save
    const saveBtn = wrapper.findAll("button").find((b) => b.text().includes("保存"))
    expect(saveBtn).toBeDefined()
    await saveBtn!.trigger("click")
    await flushPromises()

    expect(updateStocktakeDraftMock).toHaveBeenCalledWith(
      "st-1",
      expect.objectContaining({
        version: 1,
        updates: expect.any(Array),
      }),
    )
    wrapper.unmount()
  })

  it("shows stale alert and refresh button when stocktake is stale", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)
    updateStocktakeDraftMock.mockRejectedValue(
      new (await import("../../api/http")).ApiError(
        "盘点数据已过期",
        "INVENTORY_STOCKTAKE_STALE",
        409,
      ),
    )

    const wrapper = await mountDialog("st-1", 1)
    await flushPromises()

    // Save to trigger stale
    const saveBtn = wrapper.findAll("button").find((b) => b.text().includes("保存"))
    await saveBtn!.trigger("click")
    await flushPromises()

    // Should now be on step 2 with stale alert
    expect(wrapper.text()).toContain("盘点数据已过期")
    expect(wrapper.text()).toContain("刷新快照")
    wrapper.unmount()
  })

  it("refreshes stocktake when refresh button is clicked", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)
    refreshStocktakeDraftMock.mockResolvedValue({ status: "ok" })

    const wrapper = await mountDialog("st-1", 2)
    await flushPromises()

    // Trigger stale state by mocking fetchStocktake to return different revision
    fetchStocktakeMock.mockResolvedValueOnce({
      ...stocktakeDetail,
      items: [
        { ...stocktakeDetail.items[0], bookQuantity: "3" },
        stocktakeDetail.items[1],
      ],
    })

    const refreshBtn = wrapper.findAll("button").find((b) => b.text().includes("刷新快照"))
    if (refreshBtn) {
      await refreshBtn.trigger("click")
      await flushPromises()

      expect(refreshStocktakeDraftMock).toHaveBeenCalledWith(
        "st-1",
        expect.objectContaining({
          version: 1,
          locationId: expect.any(String),
        }),
      )
    }
    wrapper.unmount()
  })

  it("confirms stocktake when confirm button is clicked", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)
    confirmStocktakeMock.mockResolvedValue({ stocktakeId: "st-1", adjustedCount: 2 })

    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)

    const wrapper = await mountDialog("st-1", 2)
    await flushPromises()

    const confirmBtn = wrapper.findAll("button").find((b) => b.text().includes("确认盘点"))
    expect(confirmBtn).toBeDefined()
    await confirmBtn!.trigger("click")
    await flushPromises()

    expect(confirmStocktakeMock).toHaveBeenCalledWith("st-1", 1)
    messageSpy.mockRestore()
    wrapper.unmount()
  })

  it("emits saved event on successful confirm", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)
    confirmStocktakeMock.mockResolvedValue({ stocktakeId: "st-1", adjustedCount: 0 })

    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)

    const wrapper = await mountDialog("st-1", 2)
    await flushPromises()

    const confirmBtn = wrapper.findAll("button").find((b) => b.text().includes("确认盘点"))
    await confirmBtn!.trigger("click")
    await flushPromises()

    expect(wrapper.emitted("saved")).toBeTruthy()
    expect(wrapper.emitted("update:modelValue")).toBeTruthy()

    messageSpy.mockRestore()
    wrapper.unmount()
  })

  it("cancels stocktake when cancel button is clicked", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)
    cancelStocktakeMock.mockResolvedValue({ status: "ok" })

    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)

    const wrapper = await mountDialog("st-1", 2)
    await flushPromises()

    const cancelBtn = wrapper.findAll("button").find((b) => b.text().includes("取消盘点"))
    expect(cancelBtn).toBeDefined()
    await cancelBtn!.trigger("click")
    await flushPromises()

    expect(cancelStocktakeMock).toHaveBeenCalledWith("st-1", 1)
    expect(wrapper.emitted("saved")).toBeTruthy()

    messageSpy.mockRestore()
    wrapper.unmount()
  })

  it("does not confirm when user cancels confirmation dialog", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockRejectedValue("cancel")

    const wrapper = await mountDialog("st-1", 2)
    await flushPromises()

    const confirmBtn = wrapper.findAll("button").find((b) => b.text().includes("确认盘点"))
    await confirmBtn!.trigger("click")
    await flushPromises()

    expect(confirmStocktakeMock).not.toHaveBeenCalled()

    messageSpy.mockRestore()
    wrapper.unmount()
  })

  it("navigates between steps", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const wrapper = await mountDialog("st-1", 1)
    await flushPromises()

    // Should be on step 1
    expect(wrapper.text()).toContain("账面数量")

    // Go to step 2
    const nextBtn = wrapper.findAll("button").find((b) => b.text().includes("保存"))
    expect(nextBtn).toBeDefined()
    await nextBtn!.trigger("click")
    await flushPromises()

    // Should be on step 2
    expect(wrapper.text()).toContain("差异")

    // Go back to step 1
    const backBtn = wrapper.findAll("button").find((b) => b.text().includes("返回编辑"))
    expect(backBtn).toBeDefined()
    await backBtn!.trigger("click")
    await flushPromises()

    expect(wrapper.text()).toContain("账面数量")
    wrapper.unmount()
  })

  it("shows backfill section in step 1", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const wrapper = await mountDialog("st-1", 1)
    await flushPromises()

    expect(wrapper.text()).toContain("补录零库存批次")
    wrapper.unmount()
  })

  it("adds backfill item", async () => {
    fetchStocktakeMock.mockResolvedValue(stocktakeDetail)

    const wrapper = await mountDialog("st-1", 1)
    await flushPromises()

    // Expand backfill
    const backfillBtn = wrapper.findAll("button").find((b) => b.text().includes("补录零库存批次"))
    await backfillBtn!.trigger("click")
    await flushPromises()

    // Select lot, set quantity, add
    const selects = wrapper.findAllComponents({ name: "ElSelect" })
    const lotSelect = selects.find((s) => s.props("placeholder") === "选择批次")
    if (lotSelect) {
      await lotSelect.vm.$emit("update:modelValue", "lot-3")
      await lotSelect.vm.$emit("change", "lot-3")
    }

    const addBtn = wrapper.findAll("button").find((b) => b.text() === "添加")
    if (addBtn) {
      await addBtn.trigger("click")
      await flushPromises()
    }

    // Should have added item to the table
    expect(wrapper.text()).toContain("LOT-003")
    wrapper.unmount()
  })
})
