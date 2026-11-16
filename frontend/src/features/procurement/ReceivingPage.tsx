import { useQuery } from '@tanstack/react-query'
import { PackageCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getPurchaseOrders } from '@/api/procurement'
import type { PurchaseOrderStatus } from '@/types'
import { PoStatusBadge, formatDay } from './poStatus'

const RECEIVABLE: PurchaseOrderStatus[] = ['SHIPPED', 'PARTIALLY_RECEIVED', 'ACCEPTED']

/** The warehouse's queue: everything that can still be counted in, soonest arrival first. */
export default function ReceivingPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['purchase-orders', RECEIVABLE, 'receiving'],
    queryFn: () => getPurchaseOrders({ status: RECEIVABLE, size: 100 }),
  })
  const arrival = (po: { expectedArrival?: string; expectedDate?: string }) => po.expectedArrival ?? po.expectedDate ?? '9999-12-31'
  const queue = [...(data?.content ?? [])].sort((a, b) => arrival(a).localeCompare(arrival(b)))

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-gray-900">Receiving</h1>
        <p className="mt-0.5 text-sm text-gray-500">
          Count deliveries in against their purchase order. Good units go into stock; damaged ones are recorded on the PO only.
        </p>
      </div>

      {data && data.totalElements > queue.length && (
        <p className="mb-3 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Showing the {queue.length} newest of {data.totalElements} receivable purchase orders. Older ones are on the Purchase orders page (Incoming).
        </p>
      )}
      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">PO</th>
              <th className="px-4 py-3">Supplier</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Expected</th>
              <th className="px-4 py-3">Units outstanding</th>
              <th className="px-4 py-3 text-right" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {queue.map((po) => (
              <tr key={po.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-mono text-xs font-medium text-gray-900">
                  <Link to={`/purchase-orders/${po.id}`} className="hover:underline">{po.poNumber}</Link>
                </td>
                <td className="px-4 py-3 text-gray-700">{po.supplierName ?? `Supplier #${po.supplierId}`}</td>
                <td className="px-4 py-3"><PoStatusBadge status={po.status} /></td>
                <td className="px-4 py-3 text-gray-600">
                  {po.expectedArrival ? <>{formatDay(po.expectedArrival)} <span className="text-xs text-gray-400">(shipment notice)</span></> : formatDay(po.expectedDate)}
                </td>
                <td className="px-4 py-3 text-gray-900">{po.unitsOutstanding} of {po.unitsOrdered}</td>
                <td className="px-4 py-3 text-right">
                  <Link to={`/receiving/${po.id}`}
                    className="inline-flex items-center gap-1.5 rounded-md bg-gray-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-gray-800">
                    <PackageCheck size={14} /> Receive
                  </Link>
                </td>
              </tr>
            ))}
            {!isLoading && queue.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                  {isError ? 'Could not load the receiving queue.' : 'Nothing is waiting to be received.'}
                </td>
              </tr>
            )}
            {isLoading && <tr><td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">Loading…</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}
