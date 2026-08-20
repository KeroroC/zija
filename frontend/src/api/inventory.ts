import {
  getJson,
  postJsonWithIdempotency,
  putJsonWithIdempotency,
} from "./http"
import type {
  StockPositionListResponse,
  LotSummary,
  LotListResponse,
  MovementListResponse,
  StocktakeListResponse,
  StocktakeDetail,
  InboundResult,
  ConsistencyDiscrepancy,
} from "../types/inventory"

// ==================== Stock Positions ====================

export function fetchStockPositions(params?: {
  itemId?: string
  locationId?: string
  page?: number
  pageSize?: number
}): Promise<StockPositionListResponse> {
  const query = new URLSearchParams()
  if (params?.itemId) query.set("itemId", params.itemId)
  if (params?.locationId) query.set("locationId", params.locationId)
  if (params?.page) query.set("page", String(params.page))
  if (params?.pageSize) query.set("pageSize", String(params.pageSize))
  const qs = query.toString()
  return getJson<StockPositionListResponse>(
    `/api/v1/inventory/stock-positions${qs ? "?" + qs : ""}`,
  )
}

// ==================== Lots ====================

export function fetchLots(params?: {
  itemId?: string
  page?: number
  pageSize?: number
}): Promise<LotListResponse> {
  const query = new URLSearchParams()
  if (params?.itemId) query.set("itemId", params.itemId)
  if (params?.page) query.set("page", String(params.page))
  if (params?.pageSize) query.set("pageSize", String(params.pageSize))
  const qs = query.toString()
  return getJson<LotListResponse>(
    `/api/v1/inventory/lots${qs ? "?" + qs : ""}`,
  )
}

export function fetchLot(lotId: string): Promise<LotSummary> {
  return getJson<LotSummary>(`/api/v1/inventory/lots/${lotId}`)
}

export function updateLotMeta(
  lotId: string,
  data: {
    version: number
    purchaseDate?: string | null
    productionDate?: string | null
    expiryDate?: string | null
    serialNumber?: string | null
    memo?: string | null
  },
): Promise<LotSummary> {
  return putJsonWithIdempotency<LotSummary>(
    `/api/v1/inventory/lots/${lotId}`,
    data,
    "", // no idempotency key needed for metadata update
  )
}

// ==================== Movements ====================

export function fetchMovements(params?: {
  lotId?: string
  type?: string
  itemId?: string
  locationId?: string
  from?: string
  to?: string
  page?: number
  pageSize?: number
}): Promise<MovementListResponse> {
  const query = new URLSearchParams()
  if (params?.lotId) query.set("lotId", params.lotId)
  if (params?.type) query.set("type", params.type)
  if (params?.itemId) query.set("itemId", params.itemId)
  if (params?.locationId) query.set("locationId", params.locationId)
  if (params?.from) query.set("from", params.from)
  if (params?.to) query.set("to", params.to)
  if (params?.page) query.set("page", String(params.page))
  if (params?.pageSize) query.set("pageSize", String(params.pageSize))
  const qs = query.toString()
  return getJson<MovementListResponse>(
    `/api/v1/inventory/movements${qs ? "?" + qs : ""}`,
  )
}

// ==================== Stocktakes ====================

export function fetchStocktakes(params?: {
  status?: string
  page?: number
  pageSize?: number
}): Promise<StocktakeListResponse> {
  const query = new URLSearchParams()
  if (params?.status) query.set("status", params.status)
  if (params?.page) query.set("page", String(params.page))
  if (params?.pageSize) query.set("pageSize", String(params.pageSize))
  const qs = query.toString()
  return getJson<StocktakeListResponse>(
    `/api/v1/inventory/stocktakes${qs ? "?" + qs : ""}`,
  )
}

export function fetchStocktake(id: string): Promise<StocktakeDetail> {
  return getJson<StocktakeDetail>(`/api/v1/inventory/stocktakes/${id}`)
}

export function createStocktake(data: {
  locationId: string
}): Promise<{ id: string }> {
  return postJsonWithIdempotency<{ id: string }>(
    "/api/v1/inventory/stocktakes",
    data,
    "", // server generates idempotency key internally
  )
}

export function updateStocktakeDraft(
  id: string,
  data: {
    version: number
    updates: {
      lotId: string
      locationId: string
      actualQuantity: string
      reason?: string | null
    }[]
  },
): Promise<{ status: string }> {
  return putJsonWithIdempotency<{ status: string }>(
    `/api/v1/inventory/stocktakes/${id}`,
    data,
    "", // no idempotency key needed for draft update
  )
}

export function refreshStocktakeDraft(
  id: string,
  data: {
    version: number
    locationId: string
  },
): Promise<{ status: string }> {
  return putJsonWithIdempotency<{ status: string }>(
    `/api/v1/inventory/stocktakes/${id}/refresh`,
    data,
    "", // no idempotency key needed for refresh
  )
}

export function confirmStocktake(
  id: string,
  version: number,
): Promise<{ stocktakeId: string; adjustedCount: number }> {
  return postJsonWithIdempotency(
    `/api/v1/inventory/stocktakes/${id}/confirm`,
    { version },
    "", // no idempotency key needed for confirm
  )
}

export function cancelStocktake(
  id: string,
  version: number,
): Promise<{ status: string }> {
  return postJsonWithIdempotency(
    `/api/v1/inventory/stocktakes/${id}/cancel`,
    { version },
    "", // no idempotency key needed for cancel
  )
}

// ==================== Inbound / Consume / Loss / Transfer / Reverse ====================

export function inboundNewLot(
  data: {
    itemId: string
    quantity: string
    locationId: string
    purchaseDate?: string | null
    productionDate?: string | null
    expiryDate?: string | null
    serialNumber?: string | null
    memo?: string | null
  },
  idempotencyKey: string,
): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>(
    "/api/v1/inventory/lots",
    data,
    idempotencyKey,
  )
}

export function inboundExistingLot(
  data: {
    lotId: string
    locationId: string
    quantity: string
    memo?: string | null
  },
  idempotencyKey: string,
): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>(
    "/api/v1/inventory/inbound",
    data,
    idempotencyKey,
  )
}

export function consumeStock(
  data: {
    lotId: string
    locationId: string
    quantity: string
    reason?: string | null
    memo?: string | null
  },
  idempotencyKey: string,
): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>(
    "/api/v1/inventory/consume",
    data,
    idempotencyKey,
  )
}

export function lossStock(
  data: {
    lotId: string
    locationId: string
    quantity: string
    reason: string
    memo?: string | null
  },
  idempotencyKey: string,
): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>(
    "/api/v1/inventory/loss",
    data,
    idempotencyKey,
  )
}

export function transferStock(
  data: {
    lotId: string
    fromLocationId: string
    toLocationId: string
    quantity: string
    memo?: string | null
  },
  idempotencyKey: string,
): Promise<InboundResult> {
  return postJsonWithIdempotency<InboundResult>(
    "/api/v1/inventory/transfer",
    data,
    idempotencyKey,
  )
}

export function reverseMovement(
  movementId: string,
  data: {
    reason?: string | null
    memo?: string | null
  },
  idempotencyKey: string,
): Promise<{ reversalMovementId: string; lotId: string }> {
  return postJsonWithIdempotency(
    `/api/v1/inventory/movements/${movementId}/reverse`,
    data,
    idempotencyKey,
  )
}

// ==================== Consistency Report ====================

export function fetchConsistencyReport(params?: {
  itemId?: string
}): Promise<{ discrepancies: ConsistencyDiscrepancy[]; total: number }> {
  const query = new URLSearchParams()
  if (params?.itemId) query.set("itemId", params.itemId)
  const qs = query.toString()
  return getJson(
    `/api/v1/inventory/consistency-report${qs ? "?" + qs : ""}`,
  )
}
