import type { PurchaseOrderStatus } from '@/types'

export const poStatusLabel: Record<PurchaseOrderStatus, string> = {
  DRAFT: 'Draft',
  SENT: 'Sent',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  SHIPPED: 'Shipped',
  PARTIALLY_RECEIVED: 'Partially received',
  RECEIVED: 'Received',
  CANCELLED: 'Cancelled',
}

export const poStatusStyle: Record<PurchaseOrderStatus, string> = {
  DRAFT: 'bg-gray-100 text-gray-600',
  SENT: 'bg-blue-100 text-blue-700',
  ACCEPTED: 'bg-indigo-100 text-indigo-700',
  REJECTED: 'bg-red-100 text-red-700',
  SHIPPED: 'bg-amber-100 text-amber-700',
  PARTIALLY_RECEIVED: 'bg-orange-100 text-orange-700',
  RECEIVED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
}

export function PoStatusBadge({ status }: { status: PurchaseOrderStatus }) {
  return (
    <span className={`inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${poStatusStyle[status]}`}>
      {poStatusLabel[status]}
    </span>
  )
}

// Grouped the way people look for them.
export const statusFilters: { label: string; statuses: PurchaseOrderStatus[] }[] = [
  { label: 'All', statuses: [] },
  { label: 'Drafts', statuses: ['DRAFT'] },
  { label: 'Awaiting supplier', statuses: ['SENT'] },
  { label: 'Incoming', statuses: ['ACCEPTED', 'SHIPPED', 'PARTIALLY_RECEIVED'] },
  { label: 'Closed', statuses: ['RECEIVED', 'REJECTED', 'CANCELLED'] },
]

export const formatDay = (iso?: string) =>
  iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('en-US', { dateStyle: 'medium' }) : '—'
