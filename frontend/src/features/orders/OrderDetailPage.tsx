import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import toast from 'react-hot-toast'
import { getOrder, cancelOrder, confirmOrder } from '@/api/orders'
import { useAuthStore } from '@/stores/authStore'
import { formatCurrency, formatDate } from '@/lib/format'
import { orderStatusLabel as statusLabel, orderStatusStyle as statusStyle } from './orderStatus'
import PaymentPanel from './PaymentPanel'
import PaymentHistory from './PaymentHistory'
import RefundDialog from './RefundDialog'


export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const role = useAuthStore((s) => s.role)
  const isAdmin = role === 'ADMIN'
  const qc = useQueryClient()
  const orderId = Number(id)
  const [refunding, setRefunding] = useState(false)

  const { data: order, isLoading, isError } = useQuery({
    queryKey: ['orders', orderId],
    queryFn: () => getOrder(orderId),
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(orderId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['orders', orderId] })
      toast.success('Order cancelled')
    },
    onError: () => toast.error('Failed to cancel order'),
  })

  const confirmMutation = useMutation({
    mutationFn: () => confirmOrder(orderId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['orders', orderId] })
      toast.success('Order confirmed without payment')
    },
    onError: () => toast.error('Failed to confirm order'),
  })

  if (isLoading) return <p className="text-sm text-gray-500">Loading…</p>
  if (isError || !order) return <p className="text-sm text-red-500">Order not found.</p>

  const canCancel = order.status === 'PENDING' || order.status === 'INVENTORY_RESERVED'
  const canConfirm = isAdmin && order.status === 'INVENTORY_RESERVED'
  const canPay = order.status === 'INVENTORY_RESERVED'
  const canRefund = isAdmin && order.status === 'CONFIRMED' && !!order.paymentId

  return (
    <div className="max-w-2xl">
      <button
        onClick={() => navigate('/orders')}
        className="mb-6 flex items-center gap-1.5 text-xs text-gray-500 hover:text-gray-900 transition-colors"
      >
        <ArrowLeft size={14} /> Back to orders
      </button>

      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Order #{order.id}</h1>
          <p className="mt-0.5 text-xs text-gray-400">
            Placed {formatDate(order.createdAt)}
            {order.status === 'REFUNDED' && (order.restocked ? ' · items returned to stock' : ' · items not restocked')}
          </p>
        </div>
        <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle[order.status]}`}>
          {statusLabel[order.status]}
        </span>
      </div>

      <div className="mb-6 overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Product</th>
              <th className="px-4 py-3">Unit price</th>
              <th className="px-4 py-3">Qty</th>
              <th className="px-4 py-3 text-right">Line total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {order.items.map((item) => (
              <tr key={item.id}>
                <td className="px-4 py-3 font-medium text-gray-900">{item.productNameSnapshot}</td>
                <td className="px-4 py-3 text-gray-500">{formatCurrency(item.unitPriceSnapshot)}</td>
                <td className="px-4 py-3 text-gray-500">{item.quantity}</td>
                <td className="px-4 py-3 text-right font-medium text-gray-900">{formatCurrency(item.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-gray-200 bg-gray-50">
              <td colSpan={3} className="px-4 py-3 text-right text-xs font-medium text-gray-500">Total</td>
              <td className="px-4 py-3 text-right text-sm font-semibold text-gray-900">
                {formatCurrency(order.totalAmount)}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      {canPay && <PaymentPanel orderId={order.id} total={order.totalAmount} />}
      <PaymentHistory orderId={order.id} />

      <div className="flex gap-2">
        {canConfirm && (
          <button
            onClick={() => confirmMutation.mutate()}
            disabled={confirmMutation.isPending}
            title="Admin override: confirms the order and its reservation without charging"
            className="rounded-md border border-gray-300 px-4 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            {confirmMutation.isPending ? 'Confirming…' : 'Confirm without payment'}
          </button>
        )}
        {canRefund && (
          <button
            onClick={() => setRefunding(true)}
            className="rounded-md border border-red-200 px-4 py-2 text-xs font-medium text-red-600 hover:bg-red-50 transition-colors"
          >
            Refund order
          </button>
        )}
        {canCancel && (
          <button
            onClick={() => cancelMutation.mutate()}
            disabled={cancelMutation.isPending}
            className="rounded-md border border-red-200 px-4 py-2 text-xs font-medium text-red-600 hover:bg-red-50 disabled:opacity-50 transition-colors"
          >
            {cancelMutation.isPending ? 'Cancelling…' : 'Cancel order'}
          </button>
        )}
      </div>

      {refunding && <RefundDialog orderId={order.id} amount={order.totalAmount} onClose={() => setRefunding(false)} />}
    </div>
  )
}
