import ElementPlus from "element-plus"
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils"
import { beforeEach, afterEach, describe, expect, it, vi } from "vitest"
import { fetchConsistencyReport, fetchLots } from "../../api/inventory"
import { fetchLocationTree } from "../../api/location"
import ConsistencyDialog from "./ConsistencyDialog.vue"
import type { LotListResponse } from "../../types/inventory"
import type { LocationTree } from "../../types/location"

vi.mock("../../api/inventory", () => ({
  fetchConsistencyReport: vi.fn(),
  fetchLots: vi.fn(),
}))

vi.mock("../../api/location", () => ({
  fetchLocationTree: vi.fn(),
}))

const fetchConsistencyReportMock = vi.mocked(fetchConsistencyReport)
const fetchLotsMock = vi.mocked(fetchLots)
const fetchLocationTreeMock = vi.mocked(fetchLocationTree)

const lotsResponse: LotListResponse = {
  items: [
    {
      lotId: "lot-1",
      itemId: "item-1",
      itemName: "纸巾",
      unitName: "包",
      totalQuantity: "10",
      purchaseDate: null,
      productionDate: null,
      expiryDate: null,
      lotNumber: "L20260101",
      serialNumber: null,
      memo: null,
      positions: [],
      version: 1,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
}

const locationTree: LocationTree = {
  roots: [
    {
      id: "loc-1",
      parentId: null,
      name: "厨房",
      sortOrder: 1,
      everReferenced: true,
      version: 1,
      children: [],
    },
  ],
}

describe("ConsistencyDialog", () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  async function mountDialog() {
    wrapper = mount(ConsistencyDialog, {
      props: { modelValue: false },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
  }

  it("shows an empty-result state when no discrepancies found", async () => {
    fetchConsistencyReportMock.mockResolvedValue({ discrepancies: [], total: 0 })
    fetchLotsMock.mockResolvedValue(lotsResponse)
    fetchLocationTreeMock.mockResolvedValue(locationTree)

    await mountDialog()

    expect(fetchConsistencyReportMock).toHaveBeenCalledTimes(1)
    expect(wrapper!.text()).toContain("库存数据一致")
  })

  it("lists discrepancies when found", async () => {
    fetchConsistencyReportMock.mockResolvedValue({
      total: 1,
      discrepancies: [
        { lotId: "lot-1", locationId: "loc-1", expected: "10", actual: "7" },
      ],
    })
    fetchLotsMock.mockResolvedValue(lotsResponse)
    fetchLocationTreeMock.mockResolvedValue(locationTree)

    await mountDialog()

    expect(wrapper!.text()).toContain("发现 1 处不一致")
    expect(wrapper!.text()).toContain("L20260101")
    expect(wrapper!.text()).toContain("厨房")
    expect(wrapper!.text()).toContain("10")
    expect(wrapper!.text()).toContain("7")
  })

  it("shows an error message when the report fails to load", async () => {
    fetchConsistencyReportMock.mockRejectedValue(new Error("一致性检查失败"))
    fetchLotsMock.mockResolvedValue(lotsResponse)
    fetchLocationTreeMock.mockResolvedValue(locationTree)

    await mountDialog()

    expect(wrapper!.text()).toContain("一致性检查失败")
    expect(wrapper!.text()).not.toContain("库存数据一致")
  })
})
