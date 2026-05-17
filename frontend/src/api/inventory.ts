import client from './client'
import type { CreateInventoryRequest, InventoryResponse } from '@/types'

export const getAllInventory = () =>
  client.get<InventoryResponse[]>('/inventoryapi/products').then((r) => r.data)

export const getInventory = (productId: number) =>
  client.get<InventoryResponse>(`/inventoryapi/products/${productId}`).then((r) => r.data)

export const createInventory = (data: CreateInventoryRequest) =>
  client.post<InventoryResponse>('/inventoryapi/products', data).then((r) => r.data)

export const adjustInventory = (productId: number, action: 'reserve' | 'release' | 'deduct', quantity: number) =>
  client
    .post<InventoryResponse>(`/inventoryapi/products/${productId}/${action}`, { quantity })
    .then((r) => r.data)
