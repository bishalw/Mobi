import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, PackageCheck, Truck } from 'lucide-react'
import toast from 'react-hot-toast'
import { Link, useParams } from 'react-router-dom'
import { acceptPurchaseOrder, cancelPurchaseOrder, closePurchaseOrder, getPurchaseOrder, rejectPurchaseOrder, sendPurchaseOrder } from '@/api/procurement'
import { getProducts } from '@/api/products'
import { useAuthStore } from '@/stores/authStore'
import ShipmentNoticeDialog from './ShipmentNoticeDialog'
import Modal, { inputClass } from '@/components/Modal'
import { apiMessage } from '@/features/orders/orderStatus'
import { formatCurrency, formatDate } from '@/lib/format'
import type { PurchaseOrder } from '@/types'
import PurchaseOrderFormDialog from './PurchaseOrderFormDialog'
import { PoStatusBadge, formatDay } from './poStatus'

type Pending = 'edit' | 'send' | 'cancel' | 'close' | 'accept' | 'reject' | 'ship' | null

/**
 * One PO, for every role. What a viewer sees and can do comes from the response itself:
 * hidden fields are simply absent, and buttons follow its `actions` list.
 */
export default function PurchaseOrderDetailPage() {
  const id = Number(useParams().id)
  const [pending, setPending] = useState<Pending>(null)
  const isSupplier = useAuthStore((s) => s.role) === 'SUPPLIER'
  const backTo = isSupplier ? '/supplier' : '/purchase-orders'

  const { data: po, isLoading, isError } = useQuery({
    queryKey: ['purchase-order', id],
    queryFn: () => getPurchaseOrder(id),
    enabled: Number.isFinite(id),
  })
  const { data: products = [] } = useQuery({ queryKey: ['products'], queryFn: getProducts })
  const productName = (productId: number) => products.find((p) => p.id === productId)?.name ?? `Product #${productId}`

  if (isLoading) return <p className="text-sm text-gray-500">Loading…</p>
  if (isError || !po) return <p className="text-sm text-red-500">Purchase order not found.</p>

  const can = (action: PurchaseOrder['actions'][number]) => po.actions.includes(action)
  const showCosts = po.total != null

  return (
    <div>
      <Link to={backTo} className="mb-4 inline-flex items-center gap-1 text-xs text-gray-500 hover:text-gray-800">
        <ArrowLeft size={14} /> {isSupplier ? 'My purchase orders' : 'Purchase orders'}
      </Link>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="font-mono text-lg font-semibold text-gray-900">{po.poNumber}</h1>
            <PoStatusBadge status={po.status} />
          </div>
          <p className="mt-0.5 text-sm text-gray-500">
            {po.supplierName ?? `Supplier #${po.supplierId}`}
            {po.createdBy && ` · raised by ${po.createdBy} on ${formatDate(po.createdAt)}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {can('EDIT') && <ActionButton onClick={() => setPending('edit')}>Edit</ActionButton>}
          {can('CANCEL') && <ActionButton onClick={() => setPending('cancel')} tone="danger">Cancel PO</ActionButton>}
          {can('CLOSE') && <ActionButton onClick={() => setPending('close')}>Close short</ActionButton>}
          {can('SEND') && <ActionButton onClick={() => setPending('send')} tone="primary">Send to supplier</ActionButton>}
          {can('REJECT') && <ActionButton onClick={() => setPending('reject')} tone="danger">Reject</ActionButton>}
          {can('ACCEPT') && <ActionButton onClick={() => setPending('accept')} tone="primary">Accept</ActionButton>}
          {can('SHIP') && <ActionButton onClick={() => setPending('ship')} tone="primary">Send shipment notice</ActionButton>}
          {can('RECEIVE') && (
            <Link to={`/receiving/${po.id}`}
              className="inline-flex items-center gap-1.5 rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800">
              <PackageCheck size={15} /> Receive goods
            </Link>
          )}
        </div>
      </div>

      {po.statusNote && (
        <div className="mb-4 rounded-md border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-700">
          <span className="font-medium">{po.status === 'REJECTED' ? 'Rejected: ' : po.status === 'CANCELLED' ? 'Cancelled: ' : 'Note: '}</span>
          {po.statusNote}
        </div>
      )}

      <dl className="mb-6 grid grid-cols-2 gap-4 rounded-lg border border-gray-200 bg-white p-4 text-sm sm:grid-cols-4">
        <Fact label="Expected delivery" value={formatDay(po.expectedDate)} />
        <Fact label="Sent" value={po.sentAt ? formatDate(po.sentAt) : '—'} />
        <Fact label="Accepted" value={po.acceptedAt ? formatDate(po.acceptedAt) : '—'} />
        {showCosts ? <Fact label="Total" value={formatCurrency(po.total!)} /> : <Fact label="Closed" value={po.closedAt ? formatDate(po.closedAt) : '—'} />}
        {po.notes && <div className="col-span-2 sm:col-span-4"><Fact label="Notes" value={po.notes} /></div>}
      </dl>

      <div className="mb-6 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Product</th>
              <th className="px-4 py-3">Supplier SKU</th>
              <th className="px-4 py-3">Ordered</th>
              <th className="px-4 py-3">Shipped</th>
              <th className="px-4 py-3">Received</th>
              <th className="px-4 py-3">Damaged</th>
              <th className="px-4 py-3">Outstanding</th>
              {showCosts && <th className="px-4 py-3 text-right">Unit cost</th>}
              {showCosts && <th className="px-4 py-3 text-right">Line total</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {po.lines.map((line) => (
              <tr key={line.id}>
                <td className="px-4 py-3 font-medium text-gray-900">{productName(line.productId)}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{line.supplierSku ?? '—'}</td>
                <td className="px-4 py-3 text-gray-900">{line.quantityOrdered}</td>
                <td className="px-4 py-3 text-gray-500">{line.quantityShipped}</td>
                <td className="px-4 py-3 text-gray-500">{line.quantityReceived}</td>
                <td className={`px-4 py-3 ${line.quantityDamaged ? 'text-red-600' : 'text-gray-500'}`}>{line.quantityDamaged}</td>
                <td className="px-4 py-3 text-gray-500">{line.outstanding}</td>
                {showCosts && <td className="px-4 py-3 text-right text-gray-700">{formatCurrency(line.unitCost!)}</td>}
                {showCosts && <td className="px-4 py-3 text-right text-gray-900">{formatCurrency(line.lineTotal!)}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h2 className="mb-2 text-sm font-semibold text-gray-900">Shipment notices</h2>
      {po.shipments.length === 0 ? (
        <p className="rounded-lg border border-dashed border-gray-200 bg-white px-4 py-6 text-center text-sm text-gray-400">
          {isSupplier ? "You haven't sent a shipment notice yet." : "The supplier hasn't sent a shipment notice yet."}
        </p>
      ) : (
        <ul className="space-y-2">
          {po.shipments.map((s) => (
            <li key={s.id} className="rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Truck size={15} className="text-gray-400" />
                <span className="font-medium text-gray-900">{s.carrier}</span>
                {s.trackingNumber && <span className="font-mono text-xs text-gray-500">{s.trackingNumber}</span>}
                <span className="text-xs text-gray-500">arriving {formatDay(s.expectedArrival)}</span>
                <span className="ml-auto text-xs text-gray-400">{formatDate(s.createdAt)}</span>
              </div>
              <p className="mt-1 text-xs text-gray-600">
                {s.lines.map((l) => `${l.quantityShipped} × ${productName(l.productId)}`).join(' · ')}
              </p>
            </li>
          ))}
        </ul>
      )}

      <h2 className="mb-2 mt-6 text-sm font-semibold text-gray-900">Receipts</h2>
      {po.receipts.length === 0 ? (
        <p className="rounded-lg border border-dashed border-gray-200 bg-white px-4 py-6 text-center text-sm text-gray-400">
          Nothing has been counted in yet.
        </p>
      ) : (
        <ul className="space-y-2">
          {po.receipts.map((r) => (
            <li key={r.id} className="rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <PackageCheck size={15} className="text-gray-400" />
                <span className="font-medium text-gray-900">Receipt #{r.id}</span>
                {r.receivedBy && <span className="text-xs text-gray-500">by {r.receivedBy}</span>}
                <span className="ml-auto text-xs text-gray-400">{formatDate(r.receivedAt)}</span>
              </div>
              <p className="mt-1 text-xs text-gray-600">
                {r.lines.map((l) => `${productName(l.productId)}: ${l.quantityGood} good${l.quantityDamaged ? `, ${l.quantityDamaged} damaged` : ''}`).join(' · ')}
              </p>
              {r.note && <p className="mt-1 text-xs text-gray-500">{r.note}</p>}
            </li>
          ))}
        </ul>
      )}

      {pending === 'edit' && <PurchaseOrderFormDialog existing={po} onClose={() => setPending(null)} />}
      {pending === 'accept' && <AcceptDialog po={po} onClose={() => setPending(null)} />}
      {pending === 'reject' && (
        <ConfirmDialog po={po} title={`Reject ${po.poNumber}?`} confirmLabel="Reject" tone="danger" note="required"
          body="Mobi will be told you can't fulfil this order. Give a reason so they can plan around it."
          run={(note) => rejectPurchaseOrder(po.id, note!)} done="rejected" onClose={() => setPending(null)} />
      )}
      {pending === 'ship' && <ShipmentNoticeDialog po={po} productName={productName} onClose={() => setPending(null)} />}
      {pending === 'send' && (
        <ConfirmDialog po={po} title={`Send ${po.poNumber}?`} confirmLabel="Send"
          body={`${po.supplierName ?? 'The supplier'} will see it and can accept or reject it. It can't be edited after this.`}
          run={() => sendPurchaseOrder(po.id)} done="sent" onClose={() => setPending(null)} />
      )}
      {pending === 'cancel' && (
        <ConfirmDialog po={po} title={`Cancel ${po.poNumber}?`} confirmLabel="Cancel PO" tone="danger" note="optional"
          body="Nothing has been received against it, so it simply stops here."
          run={(note) => cancelPurchaseOrder(po.id, note)} done="cancelled" onClose={() => setPending(null)} />
      )}
      {pending === 'close' && (
        <ConfirmDialog po={po} title={`Close ${po.poNumber} short?`} confirmLabel="Close short" note="required"
          body="The outstanding units won't be delivered. Everything received so far stays on record."
          run={(note) => closePurchaseOrder(po.id, note!)} done="closed" onClose={() => setPending(null)} />
      )}
    </div>
  )
}

// Today in the user's own time zone (toISOString would give the UTC date, which is
// already tomorrow in the evening west of UTC).
function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function AcceptDialog({ po, onClose }: { po: PurchaseOrder; onClose: () => void }) {
  const qc = useQueryClient()
  const today = localToday()
  const [date, setDate] = useState(po.expectedDate && po.expectedDate >= today ? po.expectedDate : '')
  const mutation = useMutation({
    mutationFn: () => acceptPurchaseOrder(po.id, date),
    onSuccess: (updated) => {
      qc.setQueryData(['purchase-order', po.id], updated)
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
      toast.success(`${po.poNumber} accepted`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, `Could not accept ${po.poNumber}`)),
  })
  return (
    <Modal title={`Accept ${po.poNumber}`} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); if (date) mutation.mutate() }} className="space-y-4">
        <label className="block text-xs font-medium text-gray-600">
          When will it be delivered?
          <input type="date" min={today} value={date} onChange={(e) => setDate(e.target.value)} className={`${inputClass} mt-1`} />
        </label>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">Back</button>
          <button type="submit" disabled={!date || mutation.isPending}
            className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Accepting…' : 'Accept'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-gray-500">{label}</dt>
      <dd className="mt-0.5 text-gray-900">{value}</dd>
    </div>
  )
}

function ActionButton({ onClick, tone = 'default', children }: { onClick: () => void; tone?: 'default' | 'primary' | 'danger'; children: React.ReactNode }) {
  const style = tone === 'primary' ? 'bg-gray-900 text-white hover:bg-gray-800'
    : tone === 'danger' ? 'border border-red-200 text-red-600 hover:bg-red-50'
    : 'border border-gray-200 text-gray-700 hover:bg-gray-50'
  return <button onClick={onClick} className={`rounded-md px-3 py-2 text-sm font-medium ${style}`}>{children}</button>
}

function ConfirmDialog({ po, title, body, confirmLabel, tone, note, run, done, onClose }: {
  po: PurchaseOrder
  title: string
  body: string
  confirmLabel: string
  tone?: 'danger'
  note?: 'optional' | 'required'
  run: (note?: string) => Promise<PurchaseOrder>
  done: string
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const mutation = useMutation({
    mutationFn: () => run(text.trim() || undefined),
    onSuccess: (updated) => {
      qc.setQueryData(['purchase-order', po.id], updated)
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
      toast.success(`${po.poNumber} ${done}`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, `Could not update ${po.poNumber}`)),
  })
  const blocked = note === 'required' && !text.trim()

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); if (!blocked) mutation.mutate() }} className="space-y-4">
        <p className="text-sm text-gray-600">{body}</p>
        {note && (
          <label className="block text-xs font-medium text-gray-600">
            Reason {note === 'optional' && <span className="font-normal text-gray-400">(optional)</span>}
            <textarea value={text} onChange={(e) => setText(e.target.value)} rows={2} maxLength={500} className={`${inputClass} mt-1`} />
          </label>
        )}
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">
            Back
          </button>
          <button type="submit" disabled={blocked || mutation.isPending}
            className={`rounded-md px-3 py-2 text-sm font-medium text-white disabled:opacity-50 ${tone === 'danger' ? 'bg-red-600 hover:bg-red-700' : 'bg-gray-900 hover:bg-gray-800'}`}>
            {mutation.isPending ? 'Working…' : confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  )
}
