import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getAllOrders, getOrdersByUser, cancelOrder } from '@/api/orders'
import { useAuthStore } from '@/stores/authStore'
import { formatCurrency, formatDate } from '@/lib/format'
import type { OrderStatus } from '@/types'

const statusStyle: Record<OrderStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  INVENTORY_RESERVED: 'bg-amber-100 text-amber-700',
  CONFIRMED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-600',
}

const statusLabel: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  INVENTORY_RESERVED: 'Reserved',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
}

export default function OrdersPage() {
  const { role, userId } = useAuthStore()
  const isAdmin = role === 'ADMIN'
  const qc = useQueryClient()

  const queryKey = isAdmin ? ['orders'] : ['orders', 'user', userId]

  const { data: orders = [], isLoading, isError } = useQuery({
    queryKey,
    queryFn: isAdmin ? getAllOrders : () => getOrdersByUser(userId!),
  })

  const cancelMutation = useMutation({
    mutationFn: cancelOrder,
    onSuccess: () => qc.invalidateQueries({ queryKey }),
  })

  if (isLoading) return <p className="text-sm text-gray-500">Loading…</p>
  if (isError) return <p className="text-sm text-red-500">Failed to load orders.</p>

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-gray-900">Orders</h1>
      </div>

      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Order ID</th>
              {isAdmin && <th className="px-4 py-3">User ID</th>}
              <th className="px-4 py-3">Items</th>
              <th className="px-4 py-3">Total</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Created</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {orders.map((o) => {
              const canCancel = o.status === 'PENDING' || o.status === 'INVENTORY_RESERVED'
              return (
                <tr key={o.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <Link
                      to={`/orders/${o.id}`}
                      className="font-mono text-xs text-gray-900 underline-offset-2 hover:underline"
                    >
                      #{o.id}
                    </Link>
                  </td>
                  {isAdmin && <td className="px-4 py-3 font-mono text-xs text-gray-500">#{o.userId}</td>}
                  <td className="px-4 py-3 text-gray-500 text-xs max-w-xs truncate">
                    {o.items.map((i) => `${i.productNameSnapshot} ×${i.quantity}`).join(', ')}
                  </td>
                  <td className="px-4 py-3 font-medium text-gray-900">{formatCurrency(o.totalAmount)}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle[o.status]}`}>
                      {statusLabel[o.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-400">{formatDate(o.createdAt)}</td>
                  <td className="px-4 py-3">
                    {canCancel && (
                      <button
                        onClick={() => cancelMutation.mutate(o.id)}
                        disabled={cancelMutation.isPending && cancelMutation.variables === o.id}
                        className="text-xs text-red-500 hover:text-red-700 disabled:opacity-50 transition-colors"
                      >
                        Cancel
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
            {orders.length === 0 && (
              <tr>
                <td colSpan={isAdmin ? 7 : 6} className="px-4 py-8 text-center text-sm text-gray-400">
                  No orders yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
