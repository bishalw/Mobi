import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { getPurchaseOrders } from '@/api/procurement'
import { formatCurrency, formatDate } from '@/lib/format'
import { useAuthStore } from '@/stores/authStore'
import PurchaseOrderFormDialog from './PurchaseOrderFormDialog'
import { PoStatusBadge, formatDay, statusFilters } from './poStatus'

const PAGE_SIZE = 20

/**
 * Admin: every PO, and drafting new ones. Warehouse: the same list, read-only and without
 * costs. Supplier: only its own POs (the server never returns drafts to a supplier).
 */
export default function PurchaseOrdersPage() {
  const navigate = useNavigate()
  const role = useAuthStore((s) => s.role)
  const isAdmin = role === 'ADMIN'
  const isSupplier = role === 'SUPPLIER'
  const showCosts = isAdmin || isSupplier
  const filters = isSupplier ? statusFilters.filter((f) => f.label !== 'Drafts') : statusFilters
  const detailPath = (id: number) => (isSupplier ? `/supplier/orders/${id}` : `/purchase-orders/${id}`)
  const [filter, setFilter] = useState(0)
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)

  const statuses = filters[filter].statuses
  const { data, isLoading, isError } = useQuery({
    queryKey: ['purchase-orders', statuses, page],
    queryFn: () => getPurchaseOrders({ status: statuses, page, size: PAGE_SIZE }),
  })
  const orders = data?.content ?? []

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">{isSupplier ? 'My purchase orders' : 'Purchase orders'}</h1>
          <p className="mt-0.5 text-sm text-gray-500">
            {isSupplier
              ? 'Orders Mobi has sent you. Accept or reject new ones, and send a shipment notice when goods leave.'
              : 'Stock goes up only when the warehouse counts goods in against a purchase order.'}
          </p>
        </div>
        {isAdmin && (
          <button onClick={() => setCreating(true)}
            className="inline-flex items-center gap-1.5 rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800">
            <Plus size={15} /> New purchase order
          </button>
        )}
      </div>

      <div className="mb-4 flex flex-wrap gap-1.5">
        {filters.map((f, i) => (
          <button key={f.label} onClick={() => { setFilter(i); setPage(0) }}
            className={`rounded-full px-3 py-1 text-xs font-medium ${i === filter ? 'bg-gray-900 text-white' : 'bg-white text-gray-600 ring-1 ring-gray-200 hover:bg-gray-50'}`}>
            {f.label}
          </button>
        ))}
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">PO</th>
              <th className="px-4 py-3">Supplier</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Lines</th>
              <th className="px-4 py-3">Units</th>
              <th className="px-4 py-3">Outstanding</th>
              {showCosts && <th className="px-4 py-3 text-right">Total</th>}
              <th className="px-4 py-3">Expected</th>
              <th className="px-4 py-3">Created</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {orders.map((po) => (
              <tr key={po.id} onClick={() => navigate(detailPath(po.id))} className="cursor-pointer hover:bg-gray-50">
                <td className="px-4 py-3 font-mono text-xs font-medium text-gray-900">{po.poNumber}</td>
                <td className="px-4 py-3 text-gray-700">{po.supplierName ?? `Supplier #${po.supplierId}`}</td>
                <td className="px-4 py-3"><PoStatusBadge status={po.status} /></td>
                <td className="px-4 py-3 text-gray-500">{po.lineCount}</td>
                <td className="px-4 py-3 text-gray-700">{po.unitsOrdered}</td>
                <td className="px-4 py-3 text-gray-500">{po.unitsOutstanding}</td>
                {showCosts && <td className="px-4 py-3 text-right text-gray-900">{po.total != null ? formatCurrency(po.total) : '—'}</td>}
                <td className="px-4 py-3 text-gray-500">{formatDay(po.expectedDate)}</td>
                <td className="px-4 py-3 text-xs text-gray-400">{formatDate(po.createdAt)}</td>
              </tr>
            ))}
            {!isLoading && orders.length === 0 && (
              <tr>
                <td colSpan={9} className="px-4 py-8 text-center text-sm text-gray-400">
                  {isError ? 'Could not load purchase orders.' : 'No purchase orders here.'}
                </td>
              </tr>
            )}
            {isLoading && (
              <tr><td colSpan={9} className="px-4 py-8 text-center text-sm text-gray-400">Loading…</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-3 flex items-center justify-between text-xs">
          <span className="text-gray-500">Page {page + 1} of {data.totalPages}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((n) => Math.max(0, n - 1))} disabled={page === 0}
              className="rounded-md border border-gray-200 bg-white px-2.5 py-1 text-gray-600 disabled:opacity-40">Newer</button>
            <button onClick={() => setPage((n) => n + 1)} disabled={page + 1 >= data.totalPages}
              className="rounded-md border border-gray-200 bg-white px-2.5 py-1 text-gray-600 disabled:opacity-40">Older</button>
          </div>
        </div>
      )}

      {creating && <PurchaseOrderFormDialog onClose={() => setCreating(false)} />}
    </div>
  )
}
