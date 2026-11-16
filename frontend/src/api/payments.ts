import client from './client'
import type { PageResponse, PaymentResponse } from '@/types'

export const getPaymentsForOrder = (orderId: number) =>
  client.get<PaymentResponse[]>(`/paymentapi/orders/${orderId}/payments`).then((r) => r.data)

// Admin: every payment, newest first.
export const getAdminPayments = (page = 0, size = 20) =>
  client
    .get<PageResponse<PaymentResponse>>('/paymentapi/admin/payments', { params: { page, size } })
    .then((r) => r.data)
