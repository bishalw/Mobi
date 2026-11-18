export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
  phone?: string
}

export interface AuthResponse {
  token: string
  type: string
  userId: number
  username: string
  role: string
  // Seconds until the access token expires.
  expiresIn: number
}

export interface RegisterResponse {
  userId: number
  username: string
  message: string
}

export interface ProductRequest {
  sku: string
  name: string
  description?: string
  price: number
  active: boolean
}

export interface ProductResponse {
  id: number
  sku: string
  name: string
  description?: string
  price: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface InventoryResponse {
  id: number
  productId: number
  availableQuantity: number
  reservedQuantity: number
  warehouseLocation?: string
  // At or below this the product is low on stock; 0 means no alert.
  reorderLevel: number
  lowStock: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export type AdjustmentReason = 'INITIAL' | 'RECEIVED' | 'DAMAGED' | 'LOST' | 'COUNT_CORRECTION'

export interface StockAdjustment {
  id: number
  productId: number
  quantityDelta: number
  reason: AdjustmentReason
  note?: string | null
  referenceType?: string | null
  referenceId?: string | null
  createdBy: string
  createdAt: string
  availableAfter: number
}

export interface StockAdjustmentResult {
  item: InventoryResponse
  adjustment: StockAdjustment
}

export type OrderStatus = 'PENDING' | 'INVENTORY_RESERVED' | 'CONFIRMED' | 'CANCELLED' | 'REFUNDED'

export interface CreateOrderItemRequest {
  productId: number
  quantity: number
}

export interface CreateOrderRequest {
  userId: number
  items: CreateOrderItemRequest[]
}

export interface OrderItemResponse {
  id: number
  productId: number
  productNameSnapshot: string
  unitPriceSnapshot: number
  quantity: number
  lineTotal: number
}

export interface OrderResponse {
  id: number
  userId: number
  status: OrderStatus
  totalAmount: number
  items: OrderItemResponse[]
  paymentId?: string | null
  // A refund also returned the items to stock.
  restocked?: boolean
  createdAt: string
  updatedAt: string
}

export type PaymentStatus = 'PROCESSING' | 'SUCCEEDED' | 'DECLINED' | 'REFUNDED'

export interface PaymentResponse {
  paymentId: string
  orderId: number
  userId: number
  status: PaymentStatus
  amount: number
  currency: string
  provider: string
  cardBrand: string | null
  cardLast4: string | null
  failureCode: string | null
  failureMessage: string | null
  refundReason: string | null
  refundedAt: string | null
  createdAt: string
  updatedAt: string
}

// Shared envelope for every paginated admin list. Both services return this same shape.
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminUser {
  id: number
  username: string
  email?: string | null
  role: string
  enabled: boolean
  createdAt: string
  lastLoginAt?: string | null
  // Set while locked out after too many wrong passwords.
  lockedUntil?: string | null
  // The supplier a SUPPLIER account acts for.
  supplierId?: number | null
}

export interface CustomerSummary {
  userId: number
  username: string
  email?: string | null
  enabled: boolean
  signedUpAt: string
  orderCount: number
  totalSpend: number
  lastOrderAt?: string | null
}

export interface AdminOrder {
  id: number
  userId: number
  // Absent when the projection has not yet seen this user.
  username?: string | null
  status: OrderStatus
  totalAmount: number
  createdAt: string
  updatedAt: string
}

export interface OrderStats {
  totalOrders: number
  confirmedOrders: number
  pendingOrders: number
  totalRevenue: number
  distinctCustomers: number
}

export interface Supplier {
  id: number
  name: string
  contactEmail: string
  active: boolean
  createdAt: string
}

export interface SupplierProduct {
  productId: number
  supplierSku?: string | null
  // Admins only.
  unitCost?: number | null
}

export type PurchaseOrderStatus =
  | 'DRAFT'
  | 'SENT'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'SHIPPED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CANCELLED'

export type PurchaseOrderAction = 'EDIT' | 'SEND' | 'CANCEL' | 'ACCEPT' | 'REJECT' | 'SHIP' | 'RECEIVE' | 'CLOSE'

// Costs (total, unitCost, lineTotal) are left out for warehouse users; createdBy for suppliers.
export interface PurchaseOrderSummary {
  id: number
  poNumber: string
  supplierId: number
  supplierName?: string
  status: PurchaseOrderStatus
  expectedDate?: string
  // From the latest shipment notice.
  expectedArrival?: string
  lineCount: number
  unitsOrdered: number
  unitsOutstanding: number
  total?: number
  createdAt: string
  updatedAt: string
}

export interface PurchaseOrderLine {
  id: number
  productId: number
  supplierSku?: string
  quantityOrdered: number
  unitCost?: number
  lineTotal?: number
  quantityShipped: number
  quantityReceived: number
  quantityDamaged: number
  outstanding: number
}

export interface ShipmentNotice {
  id: number
  carrier: string
  trackingNumber?: string
  expectedArrival?: string
  createdBy?: string
  createdAt: string
  lines: { poLineId: number; productId: number; quantityShipped: number }[]
}

export interface Receipt {
  id: number
  receivedBy?: string
  receivedAt: string
  note?: string
  lines: { poLineId: number; productId: number; quantityGood: number; quantityDamaged: number }[]
}

export interface PurchaseOrder {
  id: number
  poNumber: string
  supplierId: number
  supplierName?: string
  status: PurchaseOrderStatus
  statusNote?: string
  expectedDate?: string
  notes?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
  sentAt?: string
  acceptedAt?: string
  rejectedAt?: string
  cancelledAt?: string
  closedAt?: string
  total?: number
  lines: PurchaseOrderLine[]
  shipments: ShipmentNotice[]
  receipts: Receipt[]
  // What the signed-in user can do next.
  actions: PurchaseOrderAction[]
}

export interface PurchaseOrderRequest {
  supplierId: number
  expectedDate?: string | null
  notes?: string | null
  lines: { productId: number; quantity: number; unitCost?: number | null }[]
}
