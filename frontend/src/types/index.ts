// Auth
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token: string
  type: string
  userId: number
  username: string
  role: string
}

export interface RegisterResponse {
  userId: number
  username: string
  message: string
}

// Products
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

// Inventory
export interface CreateInventoryRequest {
  productId: number
  availableQuantity: number
  warehouseLocation?: string
}

export interface InventoryResponse {
  id: number
  productId: number
  availableQuantity: number
  reservedQuantity: number
  warehouseLocation?: string
  version: number
  createdAt: string
  updatedAt: string
}

// Orders
export type OrderStatus = 'PENDING' | 'INVENTORY_RESERVED' | 'CONFIRMED' | 'CANCELLED'

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
  createdAt: string
  updatedAt: string
}
