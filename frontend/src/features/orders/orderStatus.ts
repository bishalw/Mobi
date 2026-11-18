import type { OrderStatus, PaymentStatus } from '@/types'

// One place for status colours and labels, shared by the order list, order detail and
// payments pages, so a new status cannot be styled in one view and missed in another.

export const orderStatusStyle: Record<OrderStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  INVENTORY_RESERVED: 'bg-amber-100 text-amber-700',
  CONFIRMED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-600',
  REFUNDED: 'bg-blue-100 text-blue-700',
}

export const orderStatusLabel: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  INVENTORY_RESERVED: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
}

export const paymentStatusStyle: Record<PaymentStatus, string> = {
  PROCESSING: 'bg-gray-100 text-gray-600',
  SUCCEEDED: 'bg-green-100 text-green-700',
  DECLINED: 'bg-red-100 text-red-600',
  REFUNDED: 'bg-blue-100 text-blue-700',
}

export const paymentStatusLabel = (status: PaymentStatus) =>
  status.charAt(0) + status.slice(1).toLowerCase()

// Surfaces the message the API sent (e.g. "Only a paid, confirmed order can be
// refunded") rather than a generic one.
export function apiMessage(err: unknown, fallback: string) {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message && message.trim().length > 0 ? message : fallback
}
