import ElementPlus, { ElMessage } from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { beforeEach, afterEach, describe, expect, it, vi } from "vitest"
import { fetchItems, fetchUnits } from "../../api/catalog"
import { fetchLots, inboundNewLot, inboundExistingLot } from "../../api/inventory"
import { fetchLocationTree } from "../../api/location"
import { ApiError } from "../../api/http"
import InboundDialog from "./InboundDialog.vue"
import type { ItemListResponse, Unit } from "../../types/catalog"
import type { LotListResponse, InboundResult } from "../../types/inventory"
import type { LocationTree } from "../../types/location"

vi.mock("../../api/catalog", () => ({
  fetchItems: vi.fn(),
  fetchUnits: vi.fn(),
}))

vi.mock("../../api/inventory", () => ({
  fetchLots: vi.fn(),
  inboundNewLot: vi.fn(),
  inboundExistingLot: vi.fn(),
}))

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}))

const fetchItemsMock = vi.mocked(fetchItems)
const fetchUnitsMock = vi.mocked(fetchUnits)
const fetchLotsMock = vi.mocked(fetchLots)
const inboundNewLotMock = vi.mocked(inboundNewLot)
const inboundExistingLotMock = vi.mocked(inboundExistingLot)
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
    {
      id: "item-2",
      householdId: "h1",
      name: "洗衣液",
      managementType: "CONSUMABLE",
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
  pageSize: 1000,
}

const units: Unit[] = [
  { id: "unit-1", householdId: "h1", name: "包", decimalScale: 0, status: "ACTIVE", version: 1 },
  { id: "unit-2", householdId: "h1", name: "瓶", decimalScale: 1, status: "ACTIVE", version: 1 },
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

const lotsResponse: LotListResponse = {
  items: [
    {
      lotId: "lot-1",
      itemId: "item-1",
      itemName: "纸巾",
      unitName: "包",
      totalQuantity: "10",
      purchaseDate: "2026-07-01",
      productionDate: null,
      expiryDate: "2027-07-01",
      lotNumber: "LOT-001",
      serialNumber: null,
      memo: null,
      positions: [{ locationId: "loc-1", locationName: "家", quantity: "10", revision: 1 }],
      version: 1,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 1000,
}

const inboundResult: InboundResult = {
  lotId: "lot-new",
  locationId: "loc-1",
  movementId: "mov-1",
  quantityAfter: "5",
  serialDuplicated: false,
}

describe("InboundDialog", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    fetchItemsMock.mockReset().mockResolvedValue(itemsResponse)
    fetchUnitsMock.mockReset().mockResolvedValue(units)
    fetchLotsMock.mockReset().mockResolvedValue(lotsResponse)
    fetchLocationTreeMock.mockReset().mockResolvedValue(locationTree)
    inboundNewLotMock.mockReset().mockResolvedValue(inboundResult)
    inboundExistingLotMock.mockReset().mockResolvedValue(inboundResult)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountDialog() {
    wrapper = mount(InboundDialog, {
      props: { modelValue: true },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
  }

  it("loads items, units, and locations on open", async () => {
    await mountDialog()

    expect(fetchItemsMock).toHaveBeenCalledWith({ status: "ACTIVE", pageSize: 1000 })
    expect(fetchUnitsMock).toHaveBeenCalledOnce()
    expect(fetchLocationTreeMock).toHaveBeenCalledOnce()
  })

  it("shows step 0 with mode selection on open", async () => {
    await mountDialog()

    expect(wrapper!.text()).toContain("入库方式")
    expect(wrapper!.text()).toContain("新建批次")
    expect(wrapper!.text()).toContain("补充现有批次")
  })

  it("defaults to new lot mode", async () => {
    await mountDialog()

    // Should show lot metadata fields for new mode
    expect(wrapper!.text()).toContain("购入日期")
    expect(wrapper!.text()).toContain("有效期至")
    expect(wrapper!.text()).toContain("序列号")
  })

  it("shows item select component", async () => {
    await mountDialog()

    const itemSelect = wrapper!.findAllComponents({ name: "ElSelect" })[0]
    expect(itemSelect).toBeDefined()
    // Items are loaded in the select component (dropdown not visible in text)
    expect(fetchItemsMock).toHaveBeenCalledOnce()
  })

  it("loads lots when item is selected in existing mode", async () => {
    await mountDialog()

    // Switch to existing mode (need to update v-model and emit change)
    const radioGroup = wrapper!.findComponent({ name: "ElRadioGroup" })
    await radioGroup.vm.$emit("update:modelValue", "existing")
    await radioGroup.vm.$emit("change", "existing")
    await flushPromises()

    // Select item
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    expect(fetchLotsMock).toHaveBeenCalledWith({ itemId: "item-1", pageSize: 1000 })
  })

  it("uses unit decimalScale for quantity precision", async () => {
    await mountDialog()

    // Select item-1 (unit-1, decimalScale=0)
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    const inputNumber = wrapper!.findComponent({ name: "ElInputNumber" })
    expect(inputNumber.props("precision")).toBe(0)
  })

  it("disables next button when required fields are missing", async () => {
    await mountDialog()

    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    expect(nextBtn).toBeDefined()
    expect(nextBtn!.attributes("disabled")).toBeDefined()
  })

  it("shows preview in step 1", async () => {
    await mountDialog()

    // Fill form: select item and location
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    const treeSelect = wrapper!.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await treeSelect.vm.$emit("change", "loc-1")
    await flushPromises()

    // Click next
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    // Should show preview
    expect(wrapper!.text()).toContain("确认入库")
    expect(wrapper!.text()).toContain("纸巾")
    expect(wrapper!.text()).toContain("家")
  })

  it("submits inboundNewLot for new mode", async () => {
    await mountDialog()

    // Fill form
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    const treeSelect = wrapper!.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await treeSelect.vm.$emit("change", "loc-1")
    await flushPromises()

    // Go to step 1
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    // Submit
    const submitBtn = wrapper!.findAll("button").find((b) => b.text().includes("确认入库"))
    await submitBtn!.trigger("click")
    await flushPromises()

    expect(inboundNewLotMock).toHaveBeenCalledWith(
      expect.objectContaining({
        itemId: "item-1",
        locationId: "loc-1",
      }),
      expect.any(String),
    )
    expect(wrapper!.emitted("done")).toBeTruthy()
    expect(wrapper!.emitted("update:modelValue")).toBeTruthy()
  })

  it("submits inboundExistingLot for existing mode", async () => {
    await mountDialog()

    // Switch to existing mode (need to update v-model and emit change)
    const radioGroup = wrapper!.findComponent({ name: "ElRadioGroup" })
    await radioGroup.vm.$emit("update:modelValue", "existing")
    await radioGroup.vm.$emit("change", "existing")
    await flushPromises()

    // Select item
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    // Select lot
    const lotSelect = wrapper!.findAllComponents({ name: "ElSelect" })[1]
    await lotSelect.vm.$emit("update:modelValue", "lot-1")
    await lotSelect.vm.$emit("change", "lot-1")
    await flushPromises()

    // Select location
    const treeSelect = wrapper!.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await treeSelect.vm.$emit("change", "loc-1")
    await flushPromises()

    // Go to step 1
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    // Submit
    const submitBtn = wrapper!.findAll("button").find((b) => b.text().includes("确认入库"))
    await submitBtn!.trigger("click")
    await flushPromises()

    expect(inboundExistingLotMock).toHaveBeenCalledWith(
      expect.objectContaining({
        lotId: "lot-1",
        locationId: "loc-1",
      }),
      expect.any(String),
    )
  })

  it("shows serial duplicate warning when result has serialDuplicated", async () => {
    inboundNewLotMock.mockResolvedValue({
      ...inboundResult,
      serialDuplicated: true,
    })

    await mountDialog()

    // Fill form
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    const treeSelect = wrapper!.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await treeSelect.vm.$emit("change", "loc-1")
    await flushPromises()

    // Go to step 1 and submit
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    const submitBtn = wrapper!.findAll("button").find((b) => b.text().includes("确认入库"))
    await submitBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("序列号重复提醒")
  })

  it("shows friendly error for INVENTORY_ARCHIVED_ITEM", async () => {
    const messageSpy = vi.spyOn(ElMessage, "error")
    inboundNewLotMock.mockRejectedValue(
      new ApiError("Item is archived", "INVENTORY_ARCHIVED_ITEM", 400),
    )

    await mountDialog()

    // Fill form
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    const treeSelect = wrapper!.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await treeSelect.vm.$emit("change", "loc-1")
    await flushPromises()

    // Go to step 1 and submit
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    const submitBtn = wrapper!.findAll("button").find((b) => b.text().includes("确认入库"))
    await submitBtn!.trigger("click")
    await flushPromises()

    expect(messageSpy).toHaveBeenCalledWith("该物品已归档，无法入库。请先恢复物品状态。")
    messageSpy.mockRestore()
  })

  it("resets form when mode changes", async () => {
    await mountDialog()

    // Select item
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    // Switch mode
    const radioGroup = wrapper!.findComponent({ name: "ElRadioGroup" })
    await radioGroup.vm.$emit("change", "existing")
    await flushPromises()

    // Lot should be reset
    const lotSelect = wrapper!.findAllComponents({ name: "ElSelect" })[1]
    expect(lotSelect.props("modelValue")).toBe("")
  })

  it("navigates back from step 1 to step 0", async () => {
    await mountDialog()

    // Fill form
    const selects = wrapper!.findAllComponents({ name: "ElSelect" })
    await selects[0].vm.$emit("update:modelValue", "item-1")
    await selects[0].vm.$emit("change", "item-1")
    await flushPromises()

    const treeSelect = wrapper!.findComponent({ name: "ElTreeSelect" })
    await treeSelect.vm.$emit("update:modelValue", "loc-1")
    await treeSelect.vm.$emit("change", "loc-1")
    await flushPromises()

    // Go to step 1
    const nextBtn = wrapper!.findAll("button").find((b) => b.text().includes("下一步"))
    await nextBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("确认入库")

    // Go back
    const backBtn = wrapper!.findAll("button").find((b) => b.text().includes("上一步"))
    await backBtn!.trigger("click")
    await flushPromises()

    expect(wrapper!.text()).toContain("入库方式")
  })
})
