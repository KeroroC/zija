import { getJson, postJson, putJson } from './http'
import type {
  Category, Brand, Unit, Tag, CatalogItem, ItemListResponse
} from '../types/catalog'

// Items
export async function fetchItems(params: {
  q?: string
  managementType?: string
  categoryId?: string
  brandId?: string
  tagId?: string
  status?: string
  page?: number
  pageSize?: number
  sort?: string
}): Promise<ItemListResponse> {
  const query = new URLSearchParams()
  if (params.q) query.set('q', params.q)
  if (params.managementType) query.set('managementType', params.managementType)
  if (params.categoryId) query.set('categoryId', params.categoryId)
  if (params.brandId) query.set('brandId', params.brandId)
  if (params.tagId) query.set('tagId', params.tagId)
  if (params.status) query.set('status', params.status)
  if (params.page) query.set('page', String(params.page))
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  if (params.sort) query.set('sort', params.sort)
  return getJson<ItemListResponse>(`/api/v1/items?${query}`)
}

export async function fetchItem(id: string): Promise<CatalogItem> {
  return getJson<CatalogItem>(`/api/v1/items/${id}`)
}

export async function createItem(data: {
  name: string
  managementType: string
  categoryId?: string
  brandId?: string
  unitId: string
  memo?: string
  expiryReminderMode?: string
  expiryReminderDays?: number[]
  lowStockMode?: string
  lowStockThreshold?: string
  tagIds?: string[]
}): Promise<CatalogItem> {
  return postJson<CatalogItem>('/api/v1/items', data)
}

export async function updateItem(id: string, data: Record<string, unknown>): Promise<CatalogItem> {
  return putJson<CatalogItem>(`/api/v1/items/${id}`, data)
}

export async function archiveItem(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/items/${id}/archive`, { version })
}

export async function restoreItem(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/items/${id}/restore`, { version })
}

// Categories
export async function fetchCategories(includeArchived = false): Promise<Category[]> {
  return getJson<Category[]>(`/api/v1/categories/tree?includeArchived=${includeArchived}`)
}

export async function createCategory(data: { name: string; parentId?: string; sortOrder?: number }): Promise<Category> {
  return postJson<Category>('/api/v1/categories', data)
}

export async function renameCategory(id: string, name: string, version: number): Promise<Category> {
  return putJson<Category>(`/api/v1/categories/${id}`, { name, version })
}

export async function moveCategory(id: string, parentId: string | null, sortOrder: number, version: number): Promise<void> {
  return putJson<void>(`/api/v1/categories/${id}/position`, { parentId, sortOrder, version })
}

export async function archiveCategory(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/categories/${id}/archive`, { version })
}

export async function restoreCategory(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/categories/${id}/restore`, { version })
}

// Brands
export async function fetchBrands(includeArchived = false): Promise<Brand[]> {
  return getJson<Brand[]>(`/api/v1/brands?includeArchived=${includeArchived}`)
}

export async function createBrand(name: string): Promise<Brand> {
  return postJson<Brand>('/api/v1/brands', { name })
}

export async function archiveBrand(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/brands/${id}/archive`, { version })
}

export async function renameBrand(id: string, name: string, version: number): Promise<void> {
  return putJson<void>(`/api/v1/brands/${id}`, { name, version })
}

export async function restoreBrand(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/brands/${id}/restore`, { version })
}

// Units
export async function fetchUnits(includeArchived = false): Promise<Unit[]> {
  return getJson<Unit[]>(`/api/v1/units?includeArchived=${includeArchived}`)
}

export async function createUnit(data: { name: string; decimalScale: number }): Promise<Unit> {
  return postJson<Unit>('/api/v1/units', data)
}

export async function renameUnit(id: string, name: string, version: number): Promise<Unit> {
  return putJson<Unit>(`/api/v1/units/${id}`, { name, version })
}

export async function archiveUnit(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/units/${id}/archive`, { version })
}

export async function restoreUnit(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/units/${id}/restore`, { version })
}

export async function updateUnitDecimalScale(
  id: string,
  decimalScale: number,
  version: number,
  confirmed = false
): Promise<{ affectedItems: number; needsConfirmation?: boolean; currentScale?: number; newScale?: number }> {
  return putJson(`/api/v1/units/${id}/decimal-scale`, { decimalScale, version, confirmed })
}

// Tags
export async function fetchTags(includeArchived = false): Promise<Tag[]> {
  return getJson<Tag[]>(`/api/v1/tags?includeArchived=${includeArchived}`)
}

export async function createTag(name: string): Promise<Tag> {
  return postJson<Tag>('/api/v1/tags', { name })
}

export async function archiveTag(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/tags/${id}/archive`, { version })
}

export async function renameTag(id: string, name: string, version: number): Promise<Tag> {
  return putJson<Tag>(`/api/v1/tags/${id}`, { name, version })
}

export async function restoreTag(id: string, version: number): Promise<void> {
  return postJson<void>(`/api/v1/tags/${id}/restore`, { version })
}
