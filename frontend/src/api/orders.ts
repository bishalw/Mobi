import client from './client'
import type { CreateOrderRequest, OrderResponse } from '@/types'

export const getAllOrders = () =>
  client.get<OrderResponse[]>('/orderapi/orders').then((r) => r.data)

export const getOrder = (id: number) =>
  client.get<OrderResponse>(`/orderapi/orders/${id}`).then((r) => r.data)

export const getOrdersByUser = (userId: number) =>
  client.get<OrderResponse[]>(`/orderapi/users/${userId}/orders`).then((r) => r.data)

export const createOrder = (data: CreateOrderRequest, idempotencyKey?: string) =>
  client
    .post<OrderResponse>('/orderapi/orders', data, {
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    })
    .then((r) => r.data)

export const cancelOrder = (id: number) =>
  client.post<OrderResponse>(`/orderapi/orders/${id}/cancel`).then((r) => r.data)

export const confirmOrder = (id: number) =>
  client.post(`/orderapi/orders/${id}/confirm`)
