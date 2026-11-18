import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getAdminPayments } from '@/api/payments'
import { getUsersByIds } from '@/api/admin'
import { formatCurrency, formatDate } from '@/lib/format'
import { paymentStatusLabel, paymentStatusStyle } from '@/features/orders/orderStatus'
import RefundDialog from '@/features/orders/RefundDialog'
import type { PaymentResponse } from '@/types'

const PAGE_SIZE = 20

export default function PaymentsPage() {
  const [page, setPage] = useState(0)
  const [refunding, setRefunding] = useState<PaymentResponse | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['admin-payments', page],
    queryFn: () => getAdminPayments(page, PAGE_SIZE),
  })

  const payments = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  // Payments only carry a user id; names come from auth-service in one request per page.
  const userIds = [...new Set(payments.map((p) => p.userId))].sort((a, b) => a - b)
  const { data: users } = useQuery({
    queryKey: ['admin-users', 'by-ids', userIds],
    queryFn: () => getUsersByIds(userIds),
    enabled: userIds.length > 0,
    staleTime: 60_000,
  })
  const usernames = new Map((users ?? []).map((u) => [u.id, u.username]))

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-gray-900">Payments</h1>
        <p className="mt-0.5 text-sm text-gray-500">
          {data ? `${data.totalElements} payment attempt${data.totalElements === 1 ? '' : 's'}, newest first` : 'Loading…'}
        </p>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Date</th>
              <th className="px-4 py-3">Order</th>
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3">Card</th>
              <th className="px-4 py-3 text-right">Amount</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Details</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-sm text-gray-400">Loading payments…</td>
              </tr>
            )}
            {isError && (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-sm text-red-500">Could not load payments.</td>
              </tr>
            )}
            {!isLoading && !isError && payments.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-sm text-gray-400">No payments yet.</td>
              </tr>
            )}

            {payments.map((p) => (
              <tr key={p.paymentId} className="hover:bg-gray-50">
                <td className="whitespace-nowrap px-4 py-3 text-xs text-gray-500">{formatDate(p.createdAt)}</td>
                <td className="px-4 py-3">
                  <Link to={`/orders/${p.orderId}`} className="font-medium text-gray-900 hover:underline">
                    #{p.orderId}
                  </Link>
                </td>
                <td className="px-4 py-3 text-gray-700">
                  {usernames.get(p.userId) ?? <span className="text-gray-400">User {p.userId}</span>}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-gray-500">
                  {p.cardLast4 ? `${p.cardBrand ?? 'Card'} •••• ${p.cardLast4}` : <span className="text-gray-300">—</span>}
                </td>
                <td className="px-4 py-3 text-right font-medium text-gray-900">{formatCurrency(p.amount)}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${paymentStatusStyle[p.status]}`}>
                    {paymentStatusLabel(p.status)}
                  </span>
                </td>
                <td className="max-w-56 truncate px-4 py-3 text-xs text-gray-400" title={p.failureMessage ?? p.refundReason ?? ''}>
                  {p.failureMessage ?? p.refundReason ?? ''}
                </td>
                <td className="px-4 py-3 text-right">
                  {p.status === 'SUCCEEDED' && (
                    <button
                      onClick={() => setRefunding(p)}
                      className="rounded-md border border-red-200 px-2.5 py-1 text-xs text-red-600 hover:bg-red-50"
                    >
                      Refund
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <span className="text-gray-500">Page {page + 1} of {totalPages}</span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((n) => Math.max(0, n - 1))}
              disabled={page === 0}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 disabled:opacity-40 hover:bg-gray-50"
            >
              Previous
            </button>
            <button
              onClick={() => setPage((n) => n + 1)}
              disabled={page + 1 >= totalPages}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 disabled:opacity-40 hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        </div>
      )}

      {/* The refund is of the order this payment paid for; see RefundDialog. */}
      {refunding && (
        <RefundDialog orderId={refunding.orderId} amount={refunding.amount} onClose={() => setRefunding(null)} />
      )}
    </div>
  )
}
