import ElementPlus, { ElMessage } from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { beforeEach, afterEach, describe, expect, it, vi } from "vitest"
import { fetchItems } from "../../api/catalog"
import { fetchStockPositions, consumeStock } from "../../api/inventory"
import { fetchLocationTree } from "../../api/location"
import { ApiError } from "../../api/http"
import ConsumeDialog from "./ConsumeDialog.vue"
import type { ItemListResponse } from "../../types/catalog"
import type { StockPositionListResponse, InboundResult } from "../../types/inventory"
import type { LocationTree } from "../../types/location"

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
}))

vi.mock("../../api/inventory", () => ({
  fetchStockPositions: vi.fn(),
  consumeStock: vi.fn(),
}))

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}))

const fetchItemsMock = vi.mocked(fetchItems)
const fetchStockPositionsMock = vi.mocked(fetchStockPositions)
const consumeStockMock = vi.mocked(consumeStock)
const fetchLocationTreeMock = vi.mocked(fetchLocationTree)

const itemsResponse: ItemListResponse = {
  items: [
    {
      id: "item-1",
      householdId: "h1",
      name: "纸巾",
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
  ],
  total: 1,
  page: 1,
  pageSize: 1000,
}

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

const stockPositions: StockPositionListResponse = {
  items: [
    {
      lotId: "lot-1",
      locationId: "loc-1",
      itemName: "纸巾",
      itemManagementType: "CONSUMABLE",
      unitName: "包",
      quantity: "10",
      revision: 1,
      expiryDate: "2027-07-01",
      lotNumber: "LOT-001",
      serialNumber: null,
      updatedAt: "2026-07-25T10:00:00Z",
    },
    {
      lotId: "lot-2",
      locationId: "loc-2",
      itemName: "纸巾",
      itemManagementType: "CONSUMABLE",
      unitName: "包",
      quantity: "5",
      revision: 1,
      expiryDate: "2026-12-01",
      lotNumber: "LOT-002",
      serialNumber: null,
      updatedAt: "2026-07-25T10:00:00Z",
    },
    {
      lotId: "lot-3",
      locationId: "loc-1",
      itemName: "纸巾",
      itemManagementType: "CONSUMABLE",
      unitName: "包",
      quantity: "3",
      revision: 1,
      expiryDate: null,
      lotNumber: "LOT-003",
      serialNumber: null,
      updatedAt: "2026-07-25T10:00:00Z",
    },
  ],
  total: 3,
  page: 1,
  pageSize: 1000,
}

const consumeResult: InboundResult = {
  lotId: "lot-1",
  locationId: "loc-1",
  movementId: "mov-1",
  quantityAfter: "7",
  serialDuplicated: false,
}

describe("ConsumeDialog", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    fetchItemsMock.mockReset().mockResolvedValue(itemsResponse)
    fetchLocationTreeMock.mockReset().mockResolvedValue(locationTree)
    fetchStockPositionsMock.mockReset().mockResolvedValue(stockPositions)
    consumeStockMock.mockReset().mockResolvedValue(consumeResult)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountDialog() {
    wrapper = mount(ConsumeDialog, {
      props: { modelValue: true },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
  }

  it("loads items and locations on open", async () => {
    await mountDialog()

    expect(fetchItemsMock).toHaveBeenCalledWith({ status: "ACTIVE", pageSize: 1000 })
    expect(fetchLocationTreeMock).toHaveBeenCalledOnce()
  })

  it("shows step 0 with item selection on open", async () => {
    await mountDialog()

    expect(wrapper!.text()).toContain("选择物品")
    // Items are loaded in the select component (dropdown not visible in text)
    expect(wrapper!.findComponent({ name: "ElSelect" }).exists()).toBe(true)
  })

  it("loads stock positions when item is selected and next is clicked", async () => {
    await mountDialog()

    // Select item
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    // Click next
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    expect(fetchStockPositionsMock).toHaveBeenCalledWith({
      itemId: "item-1",
      pageSize: 1000,
    })
  })

  it("sorts positions by expiry ASC with null last", async () => {
    await mountDialog()

    // Select item and go to step 1
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    // Check table rows order: lot-2 (2026-12-01), lot-1 (2027-07-01), lot-3 (null)
    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows.length).toBe(3)
    // First row should be lot-2 (earliest expiry)
    expect(rows[0].text()).toContain("LOT-002")
    // Last row should be lot-3 (no expiry)
    expect(rows[2].text()).toContain("LOT-003")
  })

  it("filters out positions with zero quantity", async () => {
    fetchStockPositionsMock.mockResolvedValue({
      items: [
        ...stockPositions.items,
        {
          lotId: "lot-zero",
          locationId: "loc-1",
          itemName: "纸巾",
          itemManagementType: "CONSUMABLE",
          unitName: "包",
          quantity: "0",
          revision: 1,
          expiryDate: null,
          lotNumber: "LOT-ZERO",
          serialNumber: null,
          updatedAt: "2026-07-25T10:00:00Z",
        },
      ],
      total: 4,
      page: 1,
      pageSize: 1000,
    })

    await mountDialog()

    // Select item and go to step 1
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    // Should only show 3 rows (not the zero quantity one)
    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    expect(rows.length).toBe(3)
  })

  it("shows empty state when no positions available", async () => {
    fetchStockPositionsMock.mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      pageSize: 1000,
    })

    await mountDialog()

    // Select item and go to step 1
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("该物品暂无库存")
  })

  it("enables next button in step 1 when position is selected", async () => {
    await mountDialog()

    // Go to step 1
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn0 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn0!.trigger("click")
    await flushPromises()

    // Select a row
    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    await rows[0].trigger("click")
    await flushPromises()

    // Next button should be enabled
    const nextBtn1 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    expect(nextBtn1!.attributes("disabled")).toBeUndefined()
  })

  it("shows step 2 with quantity and reason fields", async () => {
    await mountDialog()

    // Navigate to step 2
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn0 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn0!.trigger("click")
    await flushPromises()

    // Select a row
    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    await rows[0].trigger("click")
    await flushPromises()

    const nextBtn1 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn1!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("领用详情")
    expect(wrapper!.text()).toContain("数量")
    expect(wrapper!.text()).toContain("原因")
    expect(wrapper!.text()).toContain("备注")
  })

  it("submits consumeStock with correct data", async () => {
    await mountDialog()

    // Navigate to step 2
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn0 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn0!.trigger("click")
    await flushPromises()

    // Select a row
    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    await rows[0].trigger("click")
    await flushPromises()

    const nextBtn1 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn1!.trigger("click")
    await flushPromises()

    // Submit
    const submitBtn = wrapper!.findAll("button").find((b) => b.text().includes("确认领用"))
    await submitBtn!.trigger("click")
    await flushPromises()

    expect(consumeStockMock).toHaveBeenCalledWith(
      expect.objectContaining({
        lotId: "lot-2",
        locationId: "loc-2",
      }),
      expect.any(String),
    )
    expect(wrapper!.emitted("done")).toBeTruthy()
    expect(wrapper!.emitted("update:modelValue")).toBeTruthy()
  })

  it("shows friendly error for INVENTORY_INSUFFICIENT_STOCK", async () => {
    const messageSpy = vi.spyOn(ElMessage, "error")
    consumeStockMock.mockRejectedValue(
      new ApiError("Insufficient stock", "INVENTORY_INSUFFICIENT_STOCK", 400),
    )

    await mountDialog()

    // Navigate to step 2
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn0 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn0!.trigger("click")
    await flushPromises()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    await rows[0].trigger("click")
    await flushPromises()

    const nextBtn1 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn1!.trigger("click")
    await flushPromises()

    const submitBtn = wrapper!.findAll("button").find((b) => b.text().includes("确认领用"))
    await submitBtn!.trigger("click")
    await flushPromises()

    expect(messageSpy).toHaveBeenCalledWith("库存不足，请减少领用数量或选择其他批次。")
    messageSpy.mockRestore()
  })

  it("shows max quantity hint from selected position", async () => {
    await mountDialog()

    // Navigate to step 2
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn0 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn0!.trigger("click")
    await flushPromises()

    // Select row with quantity 5
    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    await rows[0].trigger("click")
    await flushPromises()

    const nextBtn1 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn1!.trigger("click")
    await flushPromises()

    // Should show available quantity hint
    expect(wrapper!.text()).toContain("可用: 5")

    // Input number should have max set
    const inputNumber = wrapper!.findComponent({ name: "ElInputNumber" })
    expect(inputNumber.props("max")).toBe(5)
  })

  it("navigates back from step 2 to step 1", async () => {
    await mountDialog()

    // Navigate to step 2
    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn0 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn0!.trigger("click")
    await flushPromises()

    const rows = wrapper!.findAll(".el-table__body .el-table__row")
    await rows[0].trigger("click")
    await flushPromises()

    const nextBtn1 = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn1!.trigger("click")
    await flushPromises()

    // Go back
    const backBtn = wrapper!.findAll("button").find((b) => b.text().includes("上一步"))
    await backBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("选择批次")
  })

  it("navigates back from step 1 to step 0", async () => {
    await mountDialog()

    const itemSelect = wrapper!.findComponent({ name: "ElSelect" })
    await itemSelect.vm.$emit("update:modelValue", "item-1")
    await itemSelect.vm.$emit("change", "item-1")
    await flushPromises()

    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    const backBtn = wrapper!.findAll("button").find((b) => b.text().includes("上一步"))
    await backBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("选择物品")
  })
})
