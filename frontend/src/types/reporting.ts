// ==================== Search ====================

/** 全局搜索结果 */
export interface SearchResult {
  items: SearchItemHit[]
  lots: SearchLotHit[]
  locations: SearchLocationHit[]
}

export interface SearchItemHit {
  itemId: string
  name: string
  brand: string
  tags: string
  category: string
  unit: string
  matchedFields: string[]
}

export interface SearchLotHit {
  lotId: string
  itemName: string
  lotNumber: string
  serialNumber: string
  matchedFields: string[]
}

export interface SearchLocationHit {
  locationId: string
  name: string
  path: string
  matchedFields: string[]
}

// ==================== Report Page ====================

/** 通用分页响应 */
export interface ReportPage<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

// ==================== Report Row Types ====================

/** 库存与位置分布行 */
export interface StockByLocationRow {
  location_path: string
  item_name: string
  lot_number: string
  serial_number: string
  unit_name: string
  quantity: number
  expiry_date: string | null
}

/** 临期批次行 */
export interface ExpiringLotRow {
  lot_number: string
  serial_number: string
  item_name: string
  location_path: string
  quantity: number
  expiry_date: string
  days_until_expiry: number
}

/** 低库存行 */
export interface LowStockRow {
  item_name: string
  total_quantity: number
  low_stock_threshold: number
}

/** 流水行 */
export interface MovementRow {
  item_name: string
  type: string
  quantity_delta: number
  from_location_path: string
  to_location_path: string
  operator_display_name: string
  reason: string
  reversal_of: string
  business_time: string
  created_at: string
}
