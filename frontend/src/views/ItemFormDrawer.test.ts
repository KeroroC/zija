import ElementPlus, { ElMessage } from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createItem,
  updateItem,
  fetchCategories,
  fetchBrands,
  fetchUnits,
  fetchTags,
} from "../api/catalog"
import ItemFormDrawer from "./ItemFormDrawer.vue"
import type { CatalogItem, Brand, Unit, Tag } from "../types/catalog"

vi.mock("../api/catalog", () => ({
  createItem: vi.fn(),
  updateItem: vi.fn(),
  fetchCategories: vi.fn(),
  fetchBrands: vi.fn(),
  fetchUnits: vi.fn(),
  fetchTags: vi.fn(),
  createBrand: vi.fn(),
  createTag: vi.fn(),
}))

const createItemMock = vi.mocked(createItem)
const updateItemMock = vi.mocked(updateItem)
const fetchCategoriesMock = vi.mocked(fetchCategories)
const fetchBrandsMock = vi.mocked(fetchBrands)
const fetchUnitsMock = vi.mocked(fetchUnits)
const fetchTagsMock = vi.mocked(fetchTags)

const unit: Unit = {
  id: "unit-1",
  householdId: "h1",
  name: "个",
  decimalScale: 0,
  status: "ACTIVE",
  version: 1,
}

const brand: Brand = {
  id: "brand-1",
  householdId: "h1",
  name: "索尼",
  status: "ACTIVE",
  version: 1,
}

const tag: Tag = {
  id: "tag-1",
  householdId: "h1",
  name: "常用",
  status: "ACTIVE",
  version: 1,
}

const createdItem: CatalogItem = {
  id: "item-new",
  householdId: "h1",
  name: "新纸巾",
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
}

describe("ItemFormDrawer", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    createItemMock.mockReset().mockResolvedValue(createdItem)
    updateItemMock.mockReset().mockResolvedValue(createdItem)
    fetchCategoriesMock.mockReset().mockResolvedValue([])
    fetchBrandsMock.mockReset().mockResolvedValue([brand])
    fetchUnitsMock.mockReset().mockResolvedValue([unit])
    fetchTagsMock.mockReset().mockResolvedValue([tag])
    vi.spyOn(ElMessage, "success").mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, "error").mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.restoreAllMocks()
  })

  async function mountDrawer(props: {
    modelValue?: boolean
    item?: CatalogItem | null
    presetName?: string
  } = {}) {
    wrapper = mount(ItemFormDrawer, {
      props: {
        modelValue: props.modelValue ?? true,
        item: props.item ?? null,
        presetName: props.presetName,
      },
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
  }

  it("applies presetName when opening create drawer", async () => {
    await mountDrawer({ presetName: "厨房纸巾" })

    const nameInput = document.querySelector(
      'input[placeholder="请输入物品名称"]',
    ) as HTMLInputElement | null
    expect(nameInput).not.toBeNull()
    expect(nameInput!.value).toBe("厨房纸巾")
  })

  it("emits saved with created CatalogItem on create", async () => {
    await mountDrawer()

    const nameInput = document.querySelector(
      'input[placeholder="请输入物品名称"]',
    ) as HTMLInputElement
    nameInput.value = "新纸巾"
    nameInput.dispatchEvent(new Event("input"))
    await flushPromises()

    const unitSelect = wrapper!
      .findAllComponents({ name: "ElSelect" })
      .find((c) => c.props("placeholder") === "请选择单位")
    expect(unitSelect).toBeDefined()
    await unitSelect!.vm.$emit("update:modelValue", "unit-1")
    await flushPromises()

    const managementSelect = wrapper!
      .findAllComponents({ name: "ElSelect" })
      .find((c) => c.props("placeholder") === "请选择管理类型")
    await managementSelect!.vm.$emit("update:modelValue", "CONSUMABLE")
    await flushPromises()

    const createBtn = Array.from(document.querySelectorAll("button")).find(
      (b) => b.textContent?.trim() === "创建",
    )
    expect(createBtn).toBeDefined()
    createBtn!.click()
    await flushPromises()

    expect(createItemMock).toHaveBeenCalled()
    const savedEvents = wrapper!.emitted("saved")
    expect(savedEvents).toBeTruthy()
    expect(savedEvents![0][0]).toEqual(createdItem)
  })

  it("teleports drawer to body via append-to-body", async () => {
    await mountDrawer()

    const drawer = wrapper!.findComponent({ name: "ElDrawer" })
    expect(drawer.props("appendToBody")).toBe(true)
  })
})
