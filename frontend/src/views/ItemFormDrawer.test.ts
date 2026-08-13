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
  createUnit,
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
  createUnit: vi.fn(),
}))

const createItemMock = vi.mocked(createItem)
const updateItemMock = vi.mocked(updateItem)
const fetchCategoriesMock = vi.mocked(fetchCategories)
const fetchBrandsMock = vi.mocked(fetchBrands)
const fetchUnitsMock = vi.mocked(fetchUnits)
const fetchTagsMock = vi.mocked(fetchTags)
const createUnitMock = vi.mocked(createUnit)

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
    createUnitMock.mockReset().mockResolvedValue({
      id: "unit-new",
      householdId: "h1",
      name: "箱",
      decimalScale: 0,
      status: "ACTIVE",
      version: 1,
    })
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

  function unitSelect() {
    return wrapper!
      .findAllComponents({ name: "ElSelect" })
      .find((c) => c.props("placeholder") === "请选择单位")
  }

  async function openCreateUnitDialog(filterText?: string) {
    const select = unitSelect()
    expect(select).toBeDefined()
    if (filterText !== undefined) {
      const filterMethod = select!.props("filterMethod") as ((query: string) => void) | undefined
      expect(filterMethod).toBeTypeOf("function")
      filterMethod!(filterText)
      await flushPromises()
    }
    const entry = Array.from(document.querySelectorAll("button")).find((el) =>
      el.textContent?.includes("新建单位"),
    )
    expect(entry).toBeDefined()
    entry!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }))
    await flushPromises()
  }

  it("unit select is filterable and offers create entry", async () => {
    await mountDrawer()

    expect(unitSelect()!.props("filterable")).toBe(true)

    const entry = Array.from(document.querySelectorAll("button")).find((el) =>
      el.textContent?.includes("新建单位"),
    )
    expect(entry).toBeDefined()
  })

  it("opens create-unit dialog with default decimal scale 0 and prefills filter text", async () => {
    await mountDrawer()
    await openCreateUnitDialog("箱")

    const nameInput = document.querySelector(
      'input[placeholder="请输入单位名称"]',
    ) as HTMLInputElement | null
    expect(nameInput).not.toBeNull()
    expect(nameInput!.value).toBe("箱")

    const scaleInput = document.querySelector(
      ".create-unit-dialog .el-input-number input",
    ) as HTMLInputElement | null
    expect(scaleInput).not.toBeNull()
    expect(Number(scaleInput!.value)).toBe(0)
  })

  it("creates unit via API, selects it, toasts success, and closes dialog", async () => {
    await mountDrawer()
    await openCreateUnitDialog("箱")

    const confirmBtn = Array.from(
      document.querySelectorAll(".create-unit-dialog button, .el-dialog__footer button"),
    ).find((b) => b.textContent?.trim() === "确定")
    expect(confirmBtn).toBeDefined()
    confirmBtn!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(createUnitMock).toHaveBeenCalledWith({ name: "箱", decimalScale: 0 })
    expect(ElMessage.success).toHaveBeenCalledWith("单位已创建")
    expect(unitSelect()!.props("modelValue")).toBe("unit-new")
    const createDialog = wrapper!
      .findAllComponents({ name: "ElDialog" })
      .find((d) => d.props("title") === "新建单位")
    expect(createDialog?.props("modelValue")).toBe(false)
  })

  it("shows created unit name after create even if dialog name differs from filter", async () => {
    createUnitMock.mockResolvedValueOnce({
      id: "unit-bag",
      householdId: "h1",
      name: "袋",
      decimalScale: 0,
      status: "ACTIVE",
      version: 1,
    })
    await mountDrawer()
    await openCreateUnitDialog("箱")

    const nameField = wrapper!
      .findAllComponents({ name: "ElInput" })
      .find((c) => c.props("placeholder") === "请输入单位名称")
    expect(nameField).toBeDefined()
    await nameField!.setValue("袋")
    await flushPromises()

    const confirmBtn = Array.from(
      document.querySelectorAll(".create-unit-dialog button, .el-dialog__footer button"),
    ).find((b) => b.textContent?.trim() === "确定")
    confirmBtn!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(createUnitMock).toHaveBeenCalledWith({ name: "袋", decimalScale: 0 })
    expect(unitSelect()!.props("modelValue")).toBe("unit-bag")
    expect(unitSelect()!.text()).toContain("袋")
    expect(unitSelect()!.text()).not.toContain("unit-bag")
  })

  it("opens create-unit dialog with empty name when there is no filter query", async () => {
    await mountDrawer()
    await unitSelect()!.vm.$emit("update:modelValue", "unit-1")
    await flushPromises()
    await openCreateUnitDialog()

    const nameInput = document.querySelector(
      'input[placeholder="请输入单位名称"]',
    ) as HTMLInputElement | null
    expect(nameInput).not.toBeNull()
    expect(nameInput!.value).toBe("")
  })

  it("keeps create-unit dialog open and does not select on API failure", async () => {
    createUnitMock.mockRejectedValueOnce(new Error("名称已存在"))
    await mountDrawer()
    await openCreateUnitDialog("个")

    const confirmBtn = Array.from(
      document.querySelectorAll(".create-unit-dialog button, .el-dialog__footer button"),
    ).find((b) => b.textContent?.trim() === "确定")
    confirmBtn!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }))
    await flushPromises()

    expect(createUnitMock).toHaveBeenCalledWith({ name: "个", decimalScale: 0 })
    expect(ElMessage.error).toHaveBeenCalled()
    expect(unitSelect()!.props("modelValue")).toBe("")
    const createDialog = wrapper!
      .findAllComponents({ name: "ElDialog" })
      .find((d) => d.props("title") === "新建单位")
    expect(createDialog?.props("modelValue")).toBe(true)
  })
})
