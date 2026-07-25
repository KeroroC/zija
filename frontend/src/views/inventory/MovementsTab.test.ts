import ElementPlus, { ElMessage, ElMessageBox } from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { fetchMovements, reverseMovement } from "../../api/inventory"
import { fetchItems, fetchUnits } from "../../api/catalog"
import { fetchLocationTree } from "../../api/location"
import { memberApi } from "../../api/member"
import MovementsTab from "./MovementsTab.vue"
import MovementDetailDrawer from "./MovementDetailDrawer.vue"
import type { Movement, MovementListResponse } from "../../types/inventory"
import type { ItemListResponse } from "../../types/catalog"
import type { LocationTree } from "../../types/location"
import type { MemberInfo } from "../../types/identity"
import type { Unit } from "../../types/catalog"

vi.mock("../../api/inventory", () => ({
  fetchMovements: vi.fn(),
  reverseMovement: vi.fn(),
}))

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
  fetchUnits: vi.fn(),
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

let mockRole = "OWNER"

vi.mock("../../stores/session", () => ({
  useSessionStore: () => ({
    get role() {
      return mockRole
    },
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

const fetchMovementsMock = vi.mocked(fetchMovements)
const reverseMovementMock = vi.mocked(reverseMovement)
const fetchItemsMock = vi.mocked(fetchItems)
const fetchUnitsMock = vi.mocked(fetchUnits)
const fetchLocationTreeMock = vi.mocked(fetchLocationTree)
const memberListMock = vi.mocked(memberApi.list)

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

const units: Unit[] = [
  { id: "unit-1", householdId: "h1", name: "瓶", decimalScale: 0, status: "ACTIVE", version: 1 },
  { id: "unit-2", householdId: "h1", name: "条", decimalScale: 0, status: "ACTIVE", version: 1 },
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

// The API returns operatorAccountId but the Movement TS type doesn't include it.
// We cast to include it for realistic mock data.
const movement1 = {
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
  operatorAccountId: "a1",
  operatorUsername: "owner",
  businessTime: "2026-07-20T10:00:00Z",
  createdAt: "2026-07-20T10:00:00Z",
  idempotencyKey: "key-1",
  reversalOf: null,
  reversedBy: null,
} as unknown as Movement

const movement2 = {
  id: "mov-2",
  lotId: "lot-1",
  itemId: "item-1",
  itemName: "洗衣液",
  type: "CONSUME",
  quantity: "2",
  unitName: "瓶",
  fromLocationId: "loc-1",
  fromLocationName: "家",
  toLocationId: null,
  toLocationName: null,
  reason: "日常使用",
  memo: null,
  operatorAccountId: "a1",
  operatorUsername: "owner",
  businessTime: "2026-07-21T14:00:00Z",
  createdAt: "2026-07-21T14:00:00Z",
  idempotencyKey: "key-2",
  reversalOf: null,
  reversedBy: null,
} as unknown as Movement

const reversedMovement = {
  id: "mov-3",
  lotId: "lot-1",
  itemId: "item-1",
  itemName: "洗衣液",
  type: "REVERSAL",
  quantity: "2",
  unitName: "瓶",
  fromLocationId: null,
  fromLocationName: null,
  toLocationId: "loc-1",
  toLocationName: "家",
  reason: null,
  memo: null,
  operatorAccountId: "a1",
  operatorUsername: "owner",
  businessTime: "2026-07-22T09:00:00Z",
  createdAt: "2026-07-22T09:00:00Z",
  idempotencyKey: "key-3",
  reversalOf: "mov-2",
  reversedBy: null,
} as unknown as Movement

const alreadyReversedMovement = {
  id: "mov-4",
  lotId: "lot-1",
  itemId: "item-2",
  itemName: "毛巾",
  type: "INBOUND",
  quantity: "10",
  unitName: "条",
  fromLocationId: null,
  fromLocationName: null,
  toLocationId: "loc-2",
  toLocationName: "卧室",
  reason: null,
  memo: null,
  operatorAccountId: "a1",
  operatorUsername: "owner",
  businessTime: "2026-07-23T10:00:00Z",
  createdAt: "2026-07-23T10:00:00Z",
  idempotencyKey: "key-4",
  reversalOf: null,
  reversedBy: "mov-5",
} as unknown as Movement

const movementResponse: MovementListResponse = {
  items: [movement1, movement2],
  total: 2,
  page: 1,
  pageSize: 20,
}

describe("MovementsTab", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    mockRole = "OWNER"
    fetchItemsMock.mockReset().mockResolvedValue(itemsResponse)
    fetchUnitsMock.mockReset().mockResolvedValue(units)
    fetchLocationTreeMock.mockReset().mockResolvedValue(locationTree)
    memberListMock.mockReset().mockResolvedValue(members)
    fetchMovementsMock.mockReset().mockResolvedValue(movementResponse)
    reverseMovementMock.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountAndWait() {
    wrapper = mount(MovementsTab, { global: { plugins: [ElementPlus] } })
    await flushPromises()
  }

  it("renders table with movement data on mount", async () => {
    await mountAndWait()

    expect(fetchMovementsMock).toHaveBeenCalledOnce()
    expect(wrapper!.text()).toContain("入库")
    expect(wrapper!.text()).toContain("洗衣液")
    expect(wrapper!.text()).toContain("5")
    expect(wrapper!.text()).toContain("瓶")
    expect(wrapper!.text()).toContain("家")
    expect(wrapper!.text()).toContain("日常使用")
  })

  it("loads item, location, unit, and member maps on mount", async () => {
    await mountAndWait()

    expect(fetchItemsMock).toHaveBeenCalledOnce()
    expect(fetchLocationTreeMock).toHaveBeenCalledOnce()
    expect(fetchUnitsMock).toHaveBeenCalledOnce()
    expect(memberListMock).toHaveBeenCalledOnce()
  })

  it("displays type badge with correct label", async () => {
    await mountAndWait()

    const tags = wrapper!.findAll(".el-tag")
    const tagTexts = tags.map((t) => t.text())
    expect(tagTexts).toContain("入库")
    expect(tagTexts).toContain("领用")
  })

  it("displays from→to location info", async () => {
    await mountAndWait()

    // movement1: null → 家, movement2: 家 → null
    expect(wrapper!.text()).toContain("家")
  })

  it("filters by type when type select changes", async () => {
    await mountAndWait()

    fetchMovementsMock.mockResolvedValueOnce({
      items: [movement1],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    expect(selects.length).toBeGreaterThanOrEqual(1)
    await selects[0].vm.$emit("update:modelValue", "INBOUND")
    await selects[0].vm.$emit("change", "INBOUND")
    await flushPromises()

    expect(fetchMovementsMock).toHaveBeenCalledTimes(2)
    const lastCall =
      fetchMovementsMock.mock.calls[fetchMovementsMock.mock.calls.length - 1][0]
    expect(lastCall?.type).toBe("INBOUND")
  })

  it("filters by item when item select changes", async () => {
    await mountAndWait()

    fetchMovementsMock.mockResolvedValueOnce({
      items: [movement1],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    // Selects: type(0), item(1), location(2)
    expect(selects.length).toBeGreaterThanOrEqual(3)
    await selects[1].vm.$emit("update:modelValue", "item-1")
    await selects[1].vm.$emit("change", "item-1")
    await flushPromises()

    expect(fetchMovementsMock).toHaveBeenCalledTimes(2)
    const lastCall =
      fetchMovementsMock.mock.calls[fetchMovementsMock.mock.calls.length - 1][0]
    expect(lastCall?.itemId).toBe("item-1")
  })

  it("filters by location when location select changes", async () => {
    await mountAndWait()

    fetchMovementsMock.mockResolvedValueOnce({
      items: [movement2],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    // Selects: type(0), item(1), location(2)
    expect(selects.length).toBeGreaterThanOrEqual(3)
    await selects[2].vm.$emit("update:modelValue", "loc-1")
    await selects[2].vm.$emit("change", "loc-1")
    await flushPromises()

    expect(fetchMovementsMock).toHaveBeenCalledTimes(2)
    const lastCall =
      fetchMovementsMock.mock.calls[fetchMovementsMock.mock.calls.length - 1][0]
    expect(lastCall?.locationId).toBe("loc-1")
  })

  it("resets to page 1 when filter changes", async () => {
    await mountAndWait()

    // Change filter - should call with page: 1
    fetchMovementsMock.mockResolvedValueOnce({
      items: [movement2],
      total: 1,
      page: 1,
      pageSize: 20,
    })

    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "CONSUME")
    await selects[0].vm.$emit("change", "CONSUME")
    await flushPromises()

    const lastCall =
      fetchMovementsMock.mock.calls[fetchMovementsMock.mock.calls.length - 1][0]
    expect(lastCall?.page).toBe(1)
  })

  it("opens MovementDetailDrawer when row is clicked", async () => {
    await mountAndWait()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows.length).toBeGreaterThan(0)
    await rows[0].trigger("click")
    await flushPromises()

    // Drawer should be visible and show movement details
    expect(wrapper!.text()).toContain("流水详情")
    expect(wrapper!.text()).toContain("首批入库")
  })

  it("shows empty state when no movements", async () => {
    fetchMovementsMock.mockResolvedValueOnce({
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
    fetchMovementsMock.mockResolvedValueOnce({
      items: [movement1],
      total: 50,
      page: 1,
      pageSize: 20,
    })

    await mountAndWait()

    expect(wrapper!.find(".el-pagination").exists()).toBe(true)
    expect(wrapper!.text()).toContain("50")
  })
})

describe("MovementDetailDrawer", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    mockRole = "OWNER"
    reverseMovementMock.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  const itemNameMap = new Map([["item-1", "洗衣液"], ["item-2", "毛巾"]])
  const locationNameMap = new Map([["loc-1", "家"], ["loc-2", "卧室"]])
  const itemUnitNameMap = new Map([["item-1", "瓶"], ["item-2", "条"]])
  const operatorNameMap = new Map([["a1", "所有者"]])

  async function mountDrawer(movement: Movement) {
    wrapper = mount(MovementDetailDrawer, {
      props: {
        modelValue: true,
        movement,
        itemNameMap,
        locationNameMap,
        itemUnitNameMap,
        operatorNameMap,
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
  }

  it("shows movement details", async () => {
    await mountDrawer(movement1)

    expect(wrapper!.text()).toContain("流水详情")
    expect(wrapper!.text()).toContain("入库")
    expect(wrapper!.text()).toContain("洗衣液")
    expect(wrapper!.text()).toContain("5")
    expect(wrapper!.text()).toContain("瓶")
    expect(wrapper!.text()).toContain("家")
    expect(wrapper!.text()).toContain("首批入库")
    expect(wrapper!.text()).toContain("所有者")
  })

  it("shows reversal relationship when movement is a reversal", async () => {
    await mountDrawer(reversedMovement)

    expect(wrapper!.text()).toContain("冲正关系")
    expect(wrapper!.text()).toContain("冲正来源")
    expect(wrapper!.text()).toContain("mov-2")
  })

  it("shows reversedBy when movement has been reversed", async () => {
    await mountDrawer(alreadyReversedMovement)

    expect(wrapper!.text()).toContain("冲正关系")
    expect(wrapper!.text()).toContain("已被冲正")
    expect(wrapper!.text()).toContain("mov-5")
  })

  it("shows reverse button for admin role", async () => {
    await mountDrawer(movement1)

    expect(wrapper!.text()).toContain("冲正此流水")
  })

  it("hides reverse button for MEMBER role", async () => {
    mockRole = "MEMBER"
    await mountDrawer(movement1)

    expect(wrapper!.text()).not.toContain("冲正此流水")
  })

  it("hides reverse button for REVERSAL type movement", async () => {
    await mountDrawer(reversedMovement)

    // REVERSAL type should not show the reverse button
    expect(wrapper!.text()).not.toContain("冲正此流水")
  })

  it("hides reverse button for already reversed movement", async () => {
    await mountDrawer(alreadyReversedMovement)

    // Already reversed should not show the reverse button
    expect(wrapper!.text()).not.toContain("冲正此流水")
  })

  it("calls reverseMovement on confirm", async () => {
    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)
    reverseMovementMock.mockResolvedValueOnce({
      reversalMovementId: "mov-new",
      lotId: "lot-1",
    })

    await mountDrawer(movement1)

    const reverseBtn = wrapper!.findAll("button").find((b) => b.text().includes("冲正此流水"))
    expect(reverseBtn).toBeDefined()
    await reverseBtn!.trigger("click")
    await flushPromises()

    expect(reverseMovementMock).toHaveBeenCalledWith(
      "mov-1",
      { reason: null, memo: null },
      expect.any(String),
    )

    messageSpy.mockRestore()
  })

  it("emits reversed event on successful reversal", async () => {
    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)
    reverseMovementMock.mockResolvedValueOnce({
      reversalMovementId: "mov-new",
      lotId: "lot-1",
    })

    await mountDrawer(movement1)

    const reverseBtn = wrapper!.findAll("button").find((b) => b.text().includes("冲正此流水"))
    await reverseBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.emitted("reversed")).toBeTruthy()
    expect(wrapper!.emitted("update:modelValue")).toBeTruthy()

    messageSpy.mockRestore()
  })

  it("shows error for ALREADY_REVERSED", async () => {
    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)
    const msgSpy = vi.spyOn(ElMessage, "error")
    const { ApiError } = await import("../../api/http")
    reverseMovementMock.mockRejectedValueOnce(
      new ApiError("流水已撤销", "INVENTORY_MOVEMENT_ALREADY_REVERSED", 409),
    )

    await mountDrawer(movement1)

    const reverseBtn = wrapper!.findAll("button").find((b) => b.text().includes("冲正此流水"))
    await reverseBtn!.trigger("click")
    await flushPromises()

    expect(msgSpy).toHaveBeenCalledWith("该流水已被冲正")
    msgSpy.mockRestore()
    messageSpy.mockRestore()
  })

  it("shows error for REVERSAL_NOT_ALLOWED", async () => {
    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)
    const msgSpy = vi.spyOn(ElMessage, "error")
    const { ApiError } = await import("../../api/http")
    reverseMovementMock.mockRejectedValueOnce(
      new ApiError("该类型流水不允许撤销", "INVENTORY_REVERSAL_NOT_ALLOWED", 409),
    )

    await mountDrawer(movement1)

    const reverseBtn = wrapper!.findAll("button").find((b) => b.text().includes("冲正此流水"))
    await reverseBtn!.trigger("click")
    await flushPromises()

    expect(msgSpy).toHaveBeenCalledWith("该类型流水不允许冲正")
    msgSpy.mockRestore()
    messageSpy.mockRestore()
  })

  it("shows error for REVERSAL_WOULD_NEGATIVE", async () => {
    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as any)
    const msgSpy = vi.spyOn(ElMessage, "error")
    const { ApiError } = await import("../../api/http")
    reverseMovementMock.mockRejectedValueOnce(
      new ApiError("撤销会导致库存为负", "INVENTORY_REVERSAL_WOULD_NEGATIVE", 409),
    )

    await mountDrawer(movement1)

    const reverseBtn = wrapper!.findAll("button").find((b) => b.text().includes("冲正此流水"))
    await reverseBtn!.trigger("click")
    await flushPromises()

    expect(msgSpy).toHaveBeenCalledWith("冲正会导致库存为负，无法执行")
    msgSpy.mockRestore()
    messageSpy.mockRestore()
  })

  it("does not call reverseMovement when user cancels confirmation", async () => {
    const messageSpy = vi.spyOn(ElMessageBox, "confirm").mockRejectedValue("cancel")

    await mountDrawer(movement1)

    const reverseBtn = wrapper!.findAll("button").find((b) => b.text().includes("冲正此流水"))
    await reverseBtn!.trigger("click")
    await flushPromises()

    expect(reverseMovementMock).not.toHaveBeenCalled()
    messageSpy.mockRestore()
  })
})
