import client from './client'
import type { AdjustmentReason, InventoryResponse, PageResponse, StockAdjustment, StockAdjustmentResult } from '@/types'

export const getAllInventory = () =>
  client.get<InventoryResponse[]>('/inventoryapi/products').then((r) => r.data)

export const getInventory = (productId: number) =>
  client.get<InventoryResponse>(`/inventoryapi/products/${productId}`).then((r) => r.data)

// The only way to change stock by hand. The first positive adjustment creates the record.
export const adjustStock = (productId: number, quantityDelta: number, reason: AdjustmentReason, note?: string) =>
  client
    .post<StockAdjustmentResult>(`/inventoryapi/products/${productId}/adjustments`, { quantityDelta, reason, note })
    .then((r) => r.data)

export const getStockHistory = (productId: number, page = 0, size = 20) =>
  client
    .get<PageResponse<StockAdjustment>>(`/inventoryapi/products/${productId}/adjustments`, { params: { page, size } })
    .then((r) => r.data)

export const updateInventoryDetails = (productId: number, data: { warehouseLocation?: string; reorderLevel?: number }) =>
  client.patch<InventoryResponse>(`/inventoryapi/products/${productId}`, data).then((r) => r.data)
