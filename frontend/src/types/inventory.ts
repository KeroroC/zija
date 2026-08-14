// ==================== Stock Position ====================

export interface StockPosition {
  lotId: string
  locationId: string
  itemName: string
  itemManagementType: "CONSUMABLE" | "DURABLE"
  unitName: string
  quantity: string
  revision: number
  expiryDate: string | null
  lotNumber: string | null
  serialNumber: string | null
  updatedAt: string
}

export interface StockPositionListResponse {
  items: StockPosition[]
  total: number
  page: number
  pageSize: number
}

// ==================== Lot ====================

export interface LotSummary {
  lotId: string
  itemId: string
  itemName: string
  unitName: string
  totalQuantity: string
  purchaseDate: string | null
  productionDate: string | null
  expiryDate: string | null
  lotNumber: string | null
  serialNumber: string | null
  memo: string | null
  positions: {
    locationId: string
    locationName: string
    quantity: string
    revision: number
  }[]
  version: number
}

export interface LotListResponse {
  items: LotSummary[]
  total: number
  page: number
  pageSize: number
}

// ==================== Movement ====================

export type MovementType =
  | "INBOUND"
  | "CONSUME"
  | "LOSS"
  | "ADJUSTMENT"
  | "TRANSFER"
  | "REVERSAL"

export interface Movement {
  id: string
  lotId: string
  itemId: string
  itemName: string
  type: MovementType
  quantity: string
  unitName: string
  fromLocationId: string | null
  fromLocationName: string | null
  toLocationId: string | null
  toLocationName: string | null
  reason: string | null
  memo: string | null
  operatorAccountId?: string | null
  operatorUsername: string | null
  operatorDisplayName?: string | null
  businessTime: string
  createdAt: string
  idempotencyKey: string
  reversalOf: string | null
  reversedBy: string | null
}

export interface MovementListResponse {
  items: Movement[]
  total: number
  page: number
  pageSize: number
}

// ==================== Stocktake ====================

export type StocktakeStatus = "DRAFT" | "COMPLETED" | "CANCELLED"

export interface StocktakeSummary {
  id: string
  status: StocktakeStatus
  createdBy: string
  createdAt: string
  completedAt: string | null
  version: number
}

export interface StocktakeListResponse {
  items: StocktakeSummary[]
  total: number
  page: number
  pageSize: number
}

export interface StocktakeItem {
  lotId: string
  locationId: string
  bookQuantity: string
  actualQuantity: string
  reason: string | null
  /** 物品名称（读时 join 补齐，缺失时前端兜底显示「—」） */
  itemName?: string | null
  /** 批次号（可为 null，前端兜底显示「—」） */
  lotNumber?: string | null
  /** 单位（可为 null，前端兜底显示「—」） */
  unitName?: string | null
}

export interface StocktakeDetail extends StocktakeSummary {
  items: StocktakeItem[]
}

// ==================== Command Results ====================

export interface InboundResult {
  lotId: string
  locationId: string
  movementId: string
  quantityAfter: string
  serialDuplicated: boolean
}

export interface ConsistencyDiscrepancy {
  lotId: string
  locationId: string
  expected: string
  actual: string
}
