import { useQuery } from '@tanstack/react-query'
import { getPaymentsForOrder } from '@/api/payments'
import { formatCurrency, formatDate } from '@/lib/format'
import { paymentStatusLabel, paymentStatusStyle as statusStyle } from './orderStatus'

export default function PaymentHistory({ orderId }: { orderId: number }) {
  const { data: payments } = useQuery({
    queryKey: ['payments', orderId],
    queryFn: () => getPaymentsForOrder(orderId),
  })

  if (!payments || payments.length === 0) return null

  return (
    <div className="mb-6 overflow-hidden rounded-lg border border-gray-200 bg-white">
      <div className="border-b border-gray-200 bg-gray-50 px-4 py-2 text-xs font-medium text-gray-500">Payment attempts</div>
      <ul className="divide-y divide-gray-100">
        {payments.map((p) => (
          <li key={p.paymentId} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
            <div className="min-w-0">
              <p className="text-gray-900">
                {formatCurrency(p.amount)}
                {p.cardLast4 && <span className="text-gray-500"> · {p.cardBrand} •••• {p.cardLast4}</span>}
              </p>
              <p className="truncate text-xs text-gray-400">
                {formatDate(p.createdAt)}
                {p.failureMessage && ` · ${p.failureMessage}`}
                {p.refundReason && ` · ${p.refundReason}`}
              </p>
            </div>
            <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle[p.status]}`}>
              {paymentStatusLabel(p.status)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
