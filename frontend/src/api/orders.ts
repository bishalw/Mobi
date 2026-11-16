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

export const payForOrder = (id: number, paymentMethodToken: string) =>
  client.post<OrderResponse>(`/orderapi/orders/${id}/pay`, { paymentMethodToken }).then((r) => r.data)

// Admin-only: refunds the payment and marks the order REFUNDED.
// restock: also put the items back into available stock (the goods came back).
export const refundOrder = (id: number, reason: string | undefined, restock: boolean) =>
  client.post<OrderResponse>(`/orderapi/orders/${id}/refund`, { reason, restock }).then((r) => r.data)

// Admin-only manual override: confirms without taking payment.
export const confirmOrder = (id: number) =>
  client.post(`/orderapi/orders/${id}/confirm`)
