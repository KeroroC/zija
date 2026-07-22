export interface Category {
  id: string
  householdId: string
  parentId: string | null
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  sortOrder: number
  version: number
}

export interface Brand {
  id: string
  householdId: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
}

export interface Unit {
  id: string
  householdId: string
  name: string
  decimalScale: number
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
}

export interface Tag {
  id: string
  householdId: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
}

export interface CatalogItem {
  id: string
  householdId: string
  name: string
  managementType: 'CONSUMABLE' | 'DURABLE'
  categoryId: string | null
  brandId: string | null
  unitId: string
  coverFileId: string | null
  coverUrl?: string
  memo: string | null
  expiryReminderMode: 'INHERIT' | 'DISABLED' | 'CUSTOM'
  expiryReminderDays: number[] | null
  lowStockMode: 'INHERIT' | 'DISABLED' | 'CUSTOM'
  lowStockThreshold: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  tagIds: string[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface ItemListResponse {
  items: CatalogItem[]
  total: number
  page: number
  pageSize: number
}
