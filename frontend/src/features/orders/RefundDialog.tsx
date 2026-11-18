import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import toast from 'react-hot-toast'
import { refundOrder } from '@/api/orders'
import { formatCurrency } from '@/lib/format'
import { apiMessage } from './orderStatus'

interface Props {
  orderId: number
  amount: number
  onClose: () => void
}

// Refunds go through the order, not the payment: order-service refunds the payment and
// marks the order REFUNDED in one step, so the two can never disagree.
export default function RefundDialog({ orderId, amount, onClose }: Props) {
  const [reason, setReason] = useState('')
  const [restock, setRestock] = useState(false)
  const qc = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => refundOrder(orderId, reason.trim() || undefined, restock),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['orders'] })
      qc.invalidateQueries({ queryKey: ['payments'] })
      qc.invalidateQueries({ queryKey: ['admin-payments'] })
      qc.invalidateQueries({ queryKey: ['admin-stats'] })
      qc.invalidateQueries({ queryKey: ['inventory'] })
      toast.success(restock ? `Order #${orderId} refunded and restocked` : `Order #${orderId} refunded`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, 'Refund failed')),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div role="dialog" aria-modal="true" aria-labelledby="refund-title"
        className="w-full max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-lg">
        <div className="mb-4 flex items-center justify-between">
          <h2 id="refund-title" className="text-sm font-semibold text-gray-900">
            Refund order #{orderId}
          </h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-700">
            <X size={16} />
          </button>
        </div>

        <p className="mb-4 text-sm text-gray-600">
          Returns <span className="font-medium text-gray-900">{formatCurrency(amount)}</span> to the
          customer's card and marks the order refunded. The customer gets a receipt by email.
        </p>

        <form
          onSubmit={(e) => {
            e.preventDefault()
            mutation.mutate()
          }}
          className="space-y-4"
        >
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-gray-700">Reason (optional)</span>
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={255}
              placeholder="e.g. Item arrived damaged"
              className="w-full rounded-md border border-gray-200 px-3 py-2 text-sm outline-none focus:border-gray-400"
            />
          </label>

          <label className="flex items-start gap-2">
            <input
              type="checkbox"
              checked={restock}
              onChange={(e) => setRestock(e.target.checked)}
              className="mt-0.5"
            />
            <span className="text-sm text-gray-700">
              Put items back in stock
              <span className="block text-xs text-gray-400">
                Only if the goods came back and can be sold again. Leave unticked for damaged
                or never-returned items.
              </span>
            </span>
          </label>

          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose}
              className="rounded-md border border-gray-200 px-4 py-2 text-xs font-medium text-gray-600 hover:bg-gray-50">
              Keep payment
            </button>
            <button type="submit" disabled={mutation.isPending}
              className="rounded-md bg-red-600 px-4 py-2 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50">
              {mutation.isPending ? 'Refunding…' : `Refund ${formatCurrency(amount)}`}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
