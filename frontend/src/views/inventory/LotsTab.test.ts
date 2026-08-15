import ElementPlus, { ElMessage } from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { fetchLots, fetchLot, fetchMovements, updateLotMeta } from "../../api/inventory"
import { fetchItems } from "../../api/catalog"
import LotsTab from "./LotsTab.vue"
import LotDetailDrawer from "./LotDetailDrawer.vue"
import type {
  LotSummary,
  LotListResponse,
  Movement,
  MovementListResponse,
} from "../../types/inventory"
import type { ItemListResponse } from "../../types/catalog"

vi.mock("../../api/inventory", () => ({
  fetchLots: vi.fn(),
  fetchLot: vi.fn(),
  fetchMovements: vi.fn(),
  updateLotMeta: vi.fn(),
}))

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
}))

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
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

const fetchLotsMock = vi.mocked(fetchLots)
const fetchLotMock = vi.mocked(fetchLot)
const fetchMovementsMock = vi.mocked(fetchMovements)
const updateLotMetaMock = vi.mocked(updateLotMeta)
const fetchItemsMock = vi.mocked(fetchItems)

const lotSummary: LotSummary = {
  lotId: "lot-1",
  itemId: "item-1",
  itemName: "洗衣液",
  unitName: "瓶",
  totalQuantity: "5",
  purchaseDate: "2026-06-01",
  productionDate: "2026-05-15",
  expiryDate: "2026-12-31",
  lotNumber: "LOT-001",
  serialNumber: null,
  memo: "首批采购",
  positions: [
    { locationId: "loc-1", locationName: "家", quantity: "3", revision: 1 },
    { locationId: "loc-2", locationName: "卧室", quantity: "2", revision: 1 },
  ],
  version: 1,
}

const lotSummary2: LotSummary = {
  lotId: "lot-2",
  itemId: "item-2",
  itemName: "毛巾",
  unitName: "条",
  totalQuantity: "10",
  purchaseDate: null,
  productionDate: null,
  expiryDate: null,
  lotNumber: null,
  serialNumber: "SN-001",
  memo: null,
  positions: [
    { locationId: "loc-1", locationName: "家", quantity: "10", revision: 1 },
  ],
  version: 1,
}

const lotListResponse: LotListResponse = {
  items: [lotSummary, lotSummary2],
  total: 2,
  page: 1,
  pageSize: 20,
}

const itemsResponse: ItemListResponse = {
  items: [
    {
      id: "item-1",
      householdId: "h1",
      name: "洗衣液",
      managementType: "CONSUMABLE",
      categoryId: null,
      brandId: null,
      unitId: "unit-1",
      coverFileId: null,
      memo: null,
      expiryReminderMode: "INHERIT",
      expiryReminderDays: null,
      lowStockMode: "INHERIT",
      lowStockThreshold: null,
      status: "ACTIVE",
      tagIds: [],
      version: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    {
      id: "item-2",
      householdId: "h1",
      name: "毛巾",
      managementType: "DURABLE",
      categoryId: null,
      brandId: null,
      unitId: "unit-2",
      coverFileId: null,
      memo: null,
      expiryReminderMode: "INHERIT",
      expiryReminderDays: null,
      lowStockMode: "INHERIT",
      lowStockThreshold: null,
      status: "ACTIVE",
      tagIds: [],
      version: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ],
  total: 2,
  page: 1,
  pageSize: 20,
}

const movement: Movement = {
  id: "mov-1",
  lotId: "lot-1",
  itemId: "item-1",
  itemName: "洗衣液",
  type: "INBOUND",
  quantity: "5",
  unitName: "瓶",
  fromLocationId: null,
  fromLocationName: null,
  toLocationId: "loc-1",
  toLocationName: "家",
  reason: null,
  memo: "首批入库",
  operatorUsername: "owner",
  businessTime: "2026-07-20T10:00:00Z",
  createdAt: "2026-07-20T10:00:00Z",
  idempotencyKey: "key-1",
  reversalOf: null,
  reversedBy: null,
}

const movementResponse: MovementListResponse = {
  items: [movement],
  total: 1,
  page: 1,
  pageSize: 20,
}

describe("LotsTab", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    fetchItemsMock.mockReset().mockResolvedValue(itemsResponse)
    fetchLotsMock.mockReset().mockResolvedValue(lotListResponse)
    fetchLotMock.mockReset().mockResolvedValue(lotSummary)
    fetchMovementsMock.mockReset().mockResolvedValue(movementResponse)
    updateLotMetaMock.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountAndWait() {
    wrapper = mount(LotsTab, { global: { plugins: [ElementPlus] } })
    await flushPromises()
  }

  it("renders table with lot data on mount", async () => {
    await mountAndWait()

    expect(fetchLotsMock).toHaveBeenCalledOnce()
    expect(wrapper!.text()).toContain("洗衣液")
    expect(wrapper!.text()).toContain("LOT-001")
    expect(wrapper!.text()).toContain("5 瓶")
    expect(wrapper!.text()).toContain("2026-12-31")
    expect(wrapper!.text()).toContain("毛巾")
    expect(wrapper!.text()).toContain("SN-001")
  })

  it("filters by item when item select changes", async () => {
    await mountAndWait()

    fetchLotsMock.mockResolvedValueOnce({
      items: [lotSummary],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    expect(selects.length).toBeGreaterThanOrEqual(1)
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    expect(fetchLotsMock).toHaveBeenCalledTimes(2)
    const lastCall =
      fetchLotsMock.mock.calls[fetchLotsMock.mock.calls.length - 1][0]
    expect(lastCall?.itemId).toBe("item-1")
  })

  it("opens LotDetailDrawer when row is clicked", async () => {
    await mountAndWait()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows.length).toBeGreaterThan(0)
    await rows[0].trigger("click")
    await flushPromises()

    expect(fetchLotMock).toHaveBeenCalledWith("lot-1")
    expect(fetchMovementsMock).toHaveBeenCalledWith({
      lotId: "lot-1",
      page: 1,
      pageSize: 50,
    })
  })

  it("shows empty state when no lots", async () => {
    fetchLotsMock.mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })

    await mountAndWait()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows).toHaveLength(0)
  })

  it("renders pagination", async () => {
    fetchLotsMock.mockResolvedValueOnce({
      items: [lotSummary],
      total: 50,
      page: 1,
      pageSize: 20,
    })

    await mountAndWait()

    expect(wrapper!.find(".el-pagination").exists()).toBe(true)
    expect(wrapper!.text()).toContain("50")
  })
})

describe("LotDetailDrawer", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    fetchLotMock.mockReset().mockResolvedValue(lotSummary)
    fetchMovementsMock.mockReset().mockResolvedValue(movementResponse)
    updateLotMetaMock.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountDrawer(lotId = "lot-1") {
    wrapper = mount(LotDetailDrawer, {
      props: { modelValue: true, lotId },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
  }

  it("shows position distribution", async () => {
    await mountDrawer()

    expect(wrapper!.text()).toContain("库存分布")
    expect(wrapper!.text()).toContain("家")
    expect(wrapper!.text()).toContain("3 瓶")
    expect(wrapper!.text()).toContain("卧室")
    expect(wrapper!.text()).toContain("2 瓶")
  })

  it("shows lot metadata", async () => {
    await mountDrawer()

    expect(wrapper!.text()).toContain("批次信息")
    expect(wrapper!.text()).toContain("LOT-001")
    expect(wrapper!.text()).toContain("首批采购")
    expect(wrapper!.text()).toContain("2026-12-31")
    expect(wrapper!.text()).toContain("洗衣液")
    expect(wrapper!.text()).toContain("5 瓶")
  })

  it("shows related movements", async () => {
    await mountDrawer()

    expect(wrapper!.text()).toContain("相关流水")
    expect(wrapper!.text()).toContain("入库")
    expect(wrapper!.text()).toContain("首批入库")
  })

  it("opens edit form when edit button is clicked", async () => {
    await mountDrawer()

    const editBtn = wrapper!.find("button.el-button--primary.is-link")
    expect(editBtn.exists()).toBe(true)
    await editBtn.trigger("click")
    await flushPromises()

    // Should show form inputs
    expect(wrapper!.find(".el-form").exists()).toBe(true)
  })

  it("updates lot metadata on save", async () => {
    const updatedLot: LotSummary = {
      ...lotSummary,
      lotNumber: "LOT-001-NEW",
      memo: "已更新",
      version: 2,
    }
    updateLotMetaMock.mockResolvedValueOnce(updatedLot)

    await mountDrawer()

    // Click edit
    const editBtn = wrapper!.find("button.el-button--primary.is-link")
    await editBtn.trigger("click")
    await flushPromises()

    // Find and click save button
    const buttons = wrapper!.findAll(".el-form button")
    const saveBtn = buttons.find((b) => b.text().includes("保存"))
    expect(saveBtn).toBeDefined()
    await saveBtn!.trigger("click")
    await flushPromises()

    expect(updateLotMetaMock).toHaveBeenCalledWith("lot-1", {
      version: 1,
      purchaseDate: "2026-06-01",
      productionDate: "2026-05-15",
      expiryDate: "2026-12-31",
      serialNumber: null,
      memo: "首批采购",
    })
  })

  it("handles version conflict error on save", async () => {
    const { ApiError } = await import("../../api/http")
    const messageSpy = vi.spyOn(ElMessage, "error")
    updateLotMetaMock.mockRejectedValueOnce(
      new ApiError("Version conflict", "lot_version_conflict", 409),
    )

    await mountDrawer()

    // Click edit
    const editBtn = wrapper!.find("button.el-button--primary.is-link")
    await editBtn.trigger("click")
    await flushPromises()

    // Click save
    const buttons = wrapper!.findAll(".el-form button")
    const saveBtn = buttons.find((b) => b.text().includes("保存"))
    await saveBtn!.trigger("click")
    await flushPromises()

    // Should show error message via ElMessage
    expect(messageSpy).toHaveBeenCalledWith("数据已被其他人修改，请关闭后重新打开")
    messageSpy.mockRestore()
  })

  it("emits updated event on successful save", async () => {
    updateLotMetaMock.mockResolvedValueOnce({ ...lotSummary, version: 2 })

    await mountDrawer()

    const editBtn = wrapper!.find("button.el-button--primary.is-link")
    await editBtn.trigger("click")
    await flushPromises()

    const buttons = wrapper!.findAll(".el-form button")
    const saveBtn = buttons.find((b) => b.text().includes("保存"))
    await saveBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.emitted("updated")).toBeTruthy()
  })
})
