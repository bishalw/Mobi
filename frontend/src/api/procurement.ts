import client from './client'
import type {
  PageResponse,
  PurchaseOrder,
  PurchaseOrderRequest,
  PurchaseOrderStatus,
  PurchaseOrderSummary,
  Supplier,
  SupplierProduct,
} from '@/types'

// --- Suppliers (read: admin, warehouse; write: admin) ---

export const getSuppliers = () => client.get<Supplier[]>('/procurementapi/suppliers').then((r) => r.data)

export const createSupplier = (data: { name: string; contactEmail: string; active: boolean }) =>
  client.post<Supplier>('/procurementapi/suppliers', data).then((r) => r.data)

export const updateSupplier = (id: number, data: { name: string; contactEmail: string; active: boolean }) =>
  client.put<Supplier>(`/procurementapi/suppliers/${id}`, data).then((r) => r.data)

export const getSupplierCatalogue = (id: number) =>
  client.get<SupplierProduct[]>(`/procurementapi/suppliers/${id}/products`).then((r) => r.data)

// Replaces the whole catalogue.
export const replaceSupplierCatalogue = (id: number, products: SupplierProduct[]) =>
  client.put<SupplierProduct[]>(`/procurementapi/suppliers/${id}/products`, { products }).then((r) => r.data)

// --- Purchase orders ---

export const getPurchaseOrders = (params: { status?: PurchaseOrderStatus[]; supplierId?: number; page?: number; size?: number }) =>
  client
    .get<PageResponse<PurchaseOrderSummary>>('/procurementapi/purchase-orders', {
      params: { ...params, status: params.status?.length ? params.status.join(',') : undefined },
    })
    .then((r) => r.data)

export const getPurchaseOrder = (id: number) =>
  client.get<PurchaseOrder>(`/procurementapi/purchase-orders/${id}`).then((r) => r.data)

export const createPurchaseOrder = (data: PurchaseOrderRequest) =>
  client.post<PurchaseOrder>('/procurementapi/purchase-orders', data).then((r) => r.data)

export const updatePurchaseOrder = (id: number, data: PurchaseOrderRequest) =>
  client.put<PurchaseOrder>(`/procurementapi/purchase-orders/${id}`, data).then((r) => r.data)

export const sendPurchaseOrder = (id: number) =>
  client.post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/send`).then((r) => r.data)

export const cancelPurchaseOrder = (id: number, note?: string) =>
  client.post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/cancel`, { note }).then((r) => r.data)

// Close short: the rest isn't coming. Note required.
export const closePurchaseOrder = (id: number, note: string) =>
  client.post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/close`, { note }).then((r) => r.data)

// --- Warehouse ---

// Counts goods in. Reuse the same key when retrying one submit: the server then returns
// the original result instead of counting twice.
export const receivePurchaseOrder = (
  id: number,
  idempotencyKey: string,
  data: { note?: string; lines: { poLineId: number; quantityGood: number; quantityDamaged: number }[] },
) =>
  client
    .post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/receipts`, data, { headers: { 'Idempotency-Key': idempotencyKey } })
    .then((r) => r.data)

// --- Supplier (own POs only) ---

export const acceptPurchaseOrder = (id: number, expectedDate: string) =>
  client.post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/accept`, { expectedDate }).then((r) => r.data)

export const rejectPurchaseOrder = (id: number, note: string) =>
  client.post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/reject`, { note }).then((r) => r.data)

export const sendShipmentNotice = (
  id: number,
  data: { carrier: string; trackingNumber?: string; expectedArrival?: string; lines: { poLineId: number; quantityShipped: number }[] },
) => client.post<PurchaseOrder>(`/procurementapi/purchase-orders/${id}/shipments`, data).then((r) => r.data)
