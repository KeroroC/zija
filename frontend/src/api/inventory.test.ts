import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { clearCsrf } from "./http"
import type { InboundResult } from "../types/inventory"
import {
  fetchStockPositions,
  fetchLots,
  fetchLot,
  updateLotMeta,
  fetchMovements,
  fetchStocktakes,
  fetchStocktake,
  createStocktake,
  updateStocktakeDraft,
  refreshStocktakeDraft,
  confirmStocktake,
  cancelStocktake,
  inboundNewLot,
  inboundExistingLot,
  consumeStock,
  lossStock,
  transferStock,
  reverseMovement,
  fetchConsistencyReport,
} from "./inventory"

function jsonResponse(body: unknown, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  })
}

describe("inventory API", () => {
  beforeEach(() => {
    clearCsrf()
  })

  afterEach(() => {
    clearCsrf()
    vi.unstubAllGlobals()
  })

  function mockFetch(responses: Response[]) {
    const fetchMock = vi.fn()
    // CSRF fetch always comes first for non-GET requests
    for (const res of responses) {
      fetchMock.mockResolvedValueOnce(res)
    }
    vi.stubGlobal("fetch", fetchMock)
    return fetchMock
  }

  function mockFetchWithCsrf(businessResponse: Response) {
    return mockFetch([
      jsonResponse({ token: "csrf-token" }),
      businessResponse,
    ])
  }

  // ==================== GET endpoints ====================

  describe("fetchStockPositions", () => {
    it("fetches stock positions with default params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchStockPositions()
      expect(result).toEqual(body)
      expect(fetchMock).toHaveBeenCalledOnce()
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/inventory/stock-positions")
    })

    it("passes query parameters", async () => {
      const body = { items: [], total: 0, page: 2, pageSize: 10 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      await fetchStockPositions({
        itemId: "item-1",
        locationId: "loc-1",
        page: 2,
        pageSize: 10,
      })
      const url = fetchMock.mock.calls[0][0] as string
      expect(url).toContain("itemId=item-1")
      expect(url).toContain("locationId=loc-1")
      expect(url).toContain("page=2")
      expect(url).toContain("pageSize=10")
    })
  })

  describe("fetchLots", () => {
    it("fetches lots with default params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchLots()
      expect(result).toEqual(body)
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/inventory/lots")
    })

    it("passes itemId and pagination params", async () => {
      const body = { items: [], total: 0, page: 3, pageSize: 50 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      await fetchLots({ itemId: "item-2", page: 3, pageSize: 50 })
      const url = fetchMock.mock.calls[0][0] as string
      expect(url).toContain("itemId=item-2")
      expect(url).toContain("page=3")
      expect(url).toContain("pageSize=50")
    })
  })

  describe("fetchLot", () => {
    it("fetches a single lot by ID", async () => {
      const lot = { lotId: "lot-1", itemId: "item-1", version: 1 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(lot))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchLot("lot-1")
      expect(result).toEqual(lot)
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/inventory/lots/lot-1")
    })
  })

  describe("fetchMovements", () => {
    it("fetches movements with default params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchMovements()
      expect(result).toEqual(body)
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/inventory/movements")
    })

    it("passes all filter params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      await fetchMovements({
        lotId: "lot-1",
        type: "INBOUND",
        itemId: "item-1",
        locationId: "loc-1",
        page: 2,
        pageSize: 50,
      })
      const url = fetchMock.mock.calls[0][0] as string
      expect(url).toContain("lotId=lot-1")
      expect(url).toContain("type=INBOUND")
      expect(url).toContain("itemId=item-1")
      expect(url).toContain("locationId=loc-1")
      expect(url).toContain("page=2")
      expect(url).toContain("pageSize=50")
    })
  })

  describe("fetchConsistencyReport", () => {
    it("fetches consistency report without filters", async () => {
      const body = { discrepancies: [], total: 0 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchConsistencyReport()
      expect(result).toEqual(body)
      expect(fetchMock.mock.calls[0][0]).toBe(
        "/api/v1/inventory/consistency-report",
      )
    })

    it("passes itemId filter", async () => {
      const body = { discrepancies: [], total: 0 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      await fetchConsistencyReport({ itemId: "item-1" })
      const url = fetchMock.mock.calls[0][0] as string
      expect(url).toContain("itemId=item-1")
    })
  })

  describe("fetchStocktakes", () => {
    it("fetches stocktakes with default params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchStocktakes()
      expect(result).toEqual(body)
      expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/inventory/stocktakes")
    })

    it("passes status and pagination params", async () => {
      const body = { items: [], total: 0, page: 1, pageSize: 20 }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body))
      vi.stubGlobal("fetch", fetchMock)

      await fetchStocktakes({ status: "DRAFT", page: 1, pageSize: 20 })
      const url = fetchMock.mock.calls[0][0] as string
      expect(url).toContain("status=DRAFT")
      expect(url).toContain("page=1")
      expect(url).toContain("pageSize=20")
    })
  })

  describe("fetchStocktake", () => {
    it("fetches stocktake detail by ID", async () => {
      const detail = {
        id: "st-1",
        status: "DRAFT",
        createdBy: "user-1",
        createdAt: "2025-01-01T00:00:00Z",
        completedAt: null,
        version: 1,
        items: [],
      }
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(detail))
      vi.stubGlobal("fetch", fetchMock)

      const result = await fetchStocktake("st-1")
      expect(result).toEqual(detail)
      expect(fetchMock.mock.calls[0][0]).toBe(
        "/api/v1/inventory/stocktakes/st-1",
      )
    })
  })

  // ==================== POST endpoints (with Idempotency-Key) ====================

  describe("inboundNewLot", () => {
    it("posts to /api/v1/inventory/lots with idempotency key", async () => {
      const result: InboundResult = {
        lotId: "lot-1",
        locationId: "loc-1",
        movementId: "mov-1",
        quantityAfter: "10",
        serialDuplicated: false,
      }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await inboundNewLot(
        {
          itemId: "item-1",
          quantity: "10",
          locationId: "loc-1",
          expiryDate: "2025-12-31",
        },
        "idem-key-1",
      )
      expect(actual).toEqual(result)

      // Check business request
      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/lots")
      expect(businessCall[1].method).toBe("POST")
      expect(businessCall[1].headers["Idempotency-Key"]).toBe("idem-key-1")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        itemId: "item-1",
        quantity: "10",
        locationId: "loc-1",
        expiryDate: "2025-12-31",
      })
    })
  })

  describe("inboundExistingLot", () => {
    it("posts to /api/v1/inventory/inbound with idempotency key", async () => {
      const result: InboundResult = {
        lotId: "lot-1",
        locationId: "loc-1",
        movementId: "mov-2",
        quantityAfter: "20",
        serialDuplicated: false,
      }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await inboundExistingLot(
        { lotId: "lot-1", locationId: "loc-1", quantity: "10", memo: "restock" },
        "idem-key-2",
      )
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/inbound")
      expect(businessCall[1].method).toBe("POST")
      expect(businessCall[1].headers["Idempotency-Key"]).toBe("idem-key-2")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        lotId: "lot-1",
        locationId: "loc-1",
        quantity: "10",
        memo: "restock",
      })
    })
  })

  describe("consumeStock", () => {
    it("posts to /api/v1/inventory/consume with idempotency key", async () => {
      const result: InboundResult = {
        lotId: "lot-1",
        locationId: "loc-1",
        movementId: "mov-3",
        quantityAfter: "5",
        serialDuplicated: false,
      }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await consumeStock(
        {
          lotId: "lot-1",
          locationId: "loc-1",
          quantity: "5",
          reason: "used up",
          memo: "monthly consumption",
        },
        "idem-key-3",
      )
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/consume")
      expect(businessCall[1].method).toBe("POST")
      expect(businessCall[1].headers["Idempotency-Key"]).toBe("idem-key-3")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        lotId: "lot-1",
        locationId: "loc-1",
        quantity: "5",
        reason: "used up",
        memo: "monthly consumption",
      })
    })
  })

  describe("lossStock", () => {
    it("posts to /api/v1/inventory/loss with idempotency key", async () => {
      const result: InboundResult = {
        lotId: "lot-1",
        locationId: "loc-1",
        movementId: "mov-4",
        quantityAfter: "0",
        serialDuplicated: false,
      }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await lossStock(
        {
          lotId: "lot-1",
          locationId: "loc-1",
          quantity: "3",
          reason: "expired",
        },
        "idem-key-4",
      )
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/loss")
      expect(businessCall[1].method).toBe("POST")
      expect(businessCall[1].headers["Idempotency-Key"]).toBe("idem-key-4")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        lotId: "lot-1",
        locationId: "loc-1",
        quantity: "3",
        reason: "expired",
      })
    })
  })

  describe("transferStock", () => {
    it("posts to /api/v1/inventory/transfer with idempotency key", async () => {
      const result: InboundResult = {
        lotId: "lot-1",
        locationId: "loc-2",
        movementId: "mov-5",
        quantityAfter: "10",
        serialDuplicated: false,
      }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await transferStock(
        {
          lotId: "lot-1",
          fromLocationId: "loc-1",
          toLocationId: "loc-2",
          quantity: "10",
          memo: "moving to shelf B",
        },
        "idem-key-5",
      )
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/transfer")
      expect(businessCall[1].method).toBe("POST")
      expect(businessCall[1].headers["Idempotency-Key"]).toBe("idem-key-5")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        lotId: "lot-1",
        fromLocationId: "loc-1",
        toLocationId: "loc-2",
        quantity: "10",
        memo: "moving to shelf B",
      })
    })
  })

  describe("reverseMovement", () => {
    it("posts to /api/v1/inventory/movements/{id}/reverse with idempotency key", async () => {
      const result = { reversalMovementId: "mov-6", lotId: "lot-1" }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await reverseMovement(
        "mov-1",
        { reason: "mistake", memo: "entered wrong quantity" },
        "idem-key-6",
      )
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/movements/mov-1/reverse")
      expect(businessCall[1].method).toBe("POST")
      expect(businessCall[1].headers["Idempotency-Key"]).toBe("idem-key-6")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        reason: "mistake",
        memo: "entered wrong quantity",
      })
    })
  })

  describe("createStocktake", () => {
    it("posts to /api/v1/inventory/stocktakes", async () => {
      const result = { id: "st-1" }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await createStocktake({ locationId: "loc-1" })
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/stocktakes")
      expect(businessCall[1].method).toBe("POST")
      expect(JSON.parse(businessCall[1].body)).toEqual({ locationId: "loc-1" })
    })
  })

  describe("confirmStocktake", () => {
    it("posts to /api/v1/inventory/stocktakes/{id}/confirm", async () => {
      const result = { stocktakeId: "st-1", adjustedCount: 3 }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await confirmStocktake("st-1", 2)
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe(
        "/api/v1/inventory/stocktakes/st-1/confirm",
      )
      expect(businessCall[1].method).toBe("POST")
      expect(JSON.parse(businessCall[1].body)).toEqual({ version: 2 })
    })
  })

  describe("cancelStocktake", () => {
    it("posts to /api/v1/inventory/stocktakes/{id}/cancel", async () => {
      const result = { status: "ok" }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await cancelStocktake("st-1", 1)
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe(
        "/api/v1/inventory/stocktakes/st-1/cancel",
      )
      expect(businessCall[1].method).toBe("POST")
      expect(JSON.parse(businessCall[1].body)).toEqual({ version: 1 })
    })
  })

  // ==================== PUT endpoints ====================

  describe("updateLotMeta", () => {
    it("puts to /api/v1/inventory/lots/{id}", async () => {
      const lot = { id: "lot-1", version: 2, memo: "updated" }
      const fetchMock = mockFetchWithCsrf(jsonResponse(lot))

      const actual = await updateLotMeta("lot-1", {
        version: 1,
        memo: "updated",
        expiryDate: "2026-01-01",
      })
      expect(actual).toEqual(lot)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/lots/lot-1")
      expect(businessCall[1].method).toBe("PUT")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        version: 1,
        memo: "updated",
        expiryDate: "2026-01-01",
      })
    })
  })

  describe("updateStocktakeDraft", () => {
    it("puts to /api/v1/inventory/stocktakes/{id}", async () => {
      const result = { status: "ok" }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await updateStocktakeDraft("st-1", {
        version: 1,
        updates: [
          {
            lotId: "lot-1",
            locationId: "loc-1",
            actualQuantity: "8",
            reason: "counted",
          },
        ],
      })
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe("/api/v1/inventory/stocktakes/st-1")
      expect(businessCall[1].method).toBe("PUT")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        version: 1,
        updates: [
          {
            lotId: "lot-1",
            locationId: "loc-1",
            actualQuantity: "8",
            reason: "counted",
          },
        ],
      })
    })
  })

  describe("refreshStocktakeDraft", () => {
    it("puts to /api/v1/inventory/stocktakes/{id}/refresh", async () => {
      const result = { status: "ok" }
      const fetchMock = mockFetchWithCsrf(jsonResponse(result))

      const actual = await refreshStocktakeDraft("st-1", {
        version: 1,
        locationId: "loc-1",
      })
      expect(actual).toEqual(result)

      const [, businessCall] = fetchMock.mock.calls
      expect(businessCall[0]).toBe(
        "/api/v1/inventory/stocktakes/st-1/refresh",
      )
      expect(businessCall[1].method).toBe("PUT")
      expect(JSON.parse(businessCall[1].body)).toEqual({
        version: 1,
        locationId: "loc-1",
      })
    })
  })
})
