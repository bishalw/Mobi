import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import toast from 'react-hot-toast'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getPurchaseOrder, receivePurchaseOrder } from '@/api/procurement'
import { getProducts } from '@/api/products'
import Modal, { inputClass } from '@/components/Modal'
import { apiMessage } from '@/features/orders/orderStatus'
import { isQuantity } from '@/lib/validation'
import type { PurchaseOrder } from '@/types'
import { PoStatusBadge } from './poStatus'

interface Count { good: string; damaged: string }

// crypto.randomUUID only exists on https or localhost; a plain-http deployment still needs a key.
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`

/** Count one delivery in. Each line defaults to everything still outstanding. */
export default function ReceivePage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const qc = useQueryClient()
  // One key per attempt: a double click or a retry after a timeout sends the same key, and
  // the server counts the delivery once. A new key is made only after a definite rejection.
  const [idempotencyKey, setIdempotencyKey] = useState(newKey)
  // Set while we can't tell whether a submit was recorded (network error, 5xx). Counts are
  // locked and Retry resends the same key: if it was recorded, the server replays it.
  const [unsure, setUnsure] = useState(false)
  const seeded = useRef(false)
  const [counts, setCounts] = useState<Record<number, Count>>({})
  const [note, setNote] = useState('')
  const [confirming, setConfirming] = useState(false)

  const { data: po, isLoading, isError, refetch } = useQuery({ queryKey: ['purchase-order', id], queryFn: () => getPurchaseOrder(id) })
  const { data: products = [] } = useQuery({ queryKey: ['products'], queryFn: getProducts })
  const productName = (productId: number) => products.find((p) => p.id === productId)?.name ?? `Product #${productId}`

  const seedCounts = (from: PurchaseOrder) =>
    setCounts(Object.fromEntries(from.lines.map((l) => [l.id, { good: String(l.outstanding), damaged: '0' }])))

  useEffect(() => {
    if (po && !seeded.current) {
      seeded.current = true
      seedCounts(po)
    }
  }, [po])

  const finish = (updated: PurchaseOrder) => {
    qc.setQueryData(['purchase-order', id], updated)
    qc.invalidateQueries({ queryKey: ['purchase-orders'] })
    // inventory-service applies the receipt from the event a few seconds later.
    setTimeout(() => {
      qc.invalidateQueries({ queryKey: ['inventory'] })
      qc.invalidateQueries({ queryKey: ['stock-history'] })
    }, 4000)
    toast.success(`${updated.poNumber} received. Stock will update shortly.`)
    navigate('/receiving')
  }

  const mutation = useMutation({
    mutationFn: () => receivePurchaseOrder(id, idempotencyKey, {
      note: note.trim() || undefined,
      lines: (po?.lines ?? []).map((l) => ({
        poLineId: l.id,
        quantityGood: Number(counts[l.id]?.good || 0),
        quantityDamaged: Number(counts[l.id]?.damaged || 0),
      })).filter((l) => l.quantityGood + l.quantityDamaged > 0),
    }),
    onSuccess: finish,
    onError: async (err) => {
      setConfirming(false)
      const status = (err as { response?: { status?: number } }).response?.status
      if (status != null && status >= 400 && status < 500) {
        // Definitely refused, e.g. someone else counted part of it first. Keep what this
        // clerk counted (it's what they physically saw); refresh the outstanding figures
        // so lines now over the limit are flagged, and make the next submit a new attempt.
        await refetch()
        setIdempotencyKey(newKey())
        setUnsure(false)
        toast.error(`${apiMessage(err, 'Could not record the receipt')}. Outstanding figures are refreshed; check your counts.`)
      } else {
        // Unknown outcome: keep the same key and counts so a retry can't count twice.
        setUnsure(true)
        toast.error("Couldn't confirm the receipt was recorded. Retry sends the same count.")
      }
    },
  })

  if (isLoading) return <p className="text-sm text-gray-500">Loading…</p>
  if (isError || !po) return <p className="text-sm text-red-500">Purchase order not found.</p>
  if (!po.actions.includes('RECEIVE')) {
    return (
      <div>
        <Link to="/receiving" className="mb-4 inline-flex items-center gap-1 text-xs text-gray-500 hover:text-gray-800"><ArrowLeft size={14} /> Receiving</Link>
        <p className="text-sm text-gray-600">{po.poNumber} can't be received while it is <PoStatusBadge status={po.status} />.</p>
      </div>
    )
  }

  const rows = po.lines.map((line) => {
    const c = counts[line.id] ?? { good: '0', damaged: '0' }
    const good = Number(c.good || 0)
    const damaged = Number(c.damaged || 0)
    const invalid = !isQuantity(c.good, 0) || !isQuantity(c.damaged, 0) || good + damaged > line.outstanding
    return { line, c, good, damaged, invalid }
  })
  const totalGood = rows.reduce((s, r) => s + r.good, 0)
  const totalDamaged = rows.reduce((s, r) => s + r.damaged, 0)
  const valid = rows.every((r) => !r.invalid) && totalGood + totalDamaged > 0
  const set = (lineId: number, patch: Partial<Count>) => setCounts((cs) => ({ ...cs, [lineId]: { ...cs[lineId], ...patch } }))
  const latestNotice = po.shipments[po.shipments.length - 1]

  return (
    <div>
      <Link to="/receiving" className="mb-4 inline-flex items-center gap-1 text-xs text-gray-500 hover:text-gray-800">
        <ArrowLeft size={14} /> Receiving
      </Link>
      <div className="mb-6">
        <div className="flex items-center gap-2">
          <h1 className="text-lg font-semibold text-gray-900">Receive <span className="font-mono">{po.poNumber}</span></h1>
          <PoStatusBadge status={po.status} />
        </div>
        <p className="mt-0.5 text-sm text-gray-500">
          {po.supplierName}
          {latestNotice && ` · latest notice: ${latestNotice.carrier}${latestNotice.trackingNumber ? ` ${latestNotice.trackingNumber}` : ''}`}
        </p>
      </div>

      <div className="mb-4 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Product</th>
              <th className="px-4 py-3">Ordered</th>
              <th className="px-4 py-3">Shipped</th>
              <th className="px-4 py-3">Already in</th>
              <th className="px-4 py-3">Outstanding</th>
              <th className="px-4 py-3">Good</th>
              <th className="px-4 py-3">Damaged</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.map(({ line, c, invalid }) => (
              <tr key={line.id} className={invalid ? 'bg-red-50' : ''}>
                <td className="px-4 py-3 font-medium text-gray-900">{productName(line.productId)}</td>
                <td className="px-4 py-3 text-gray-700">{line.quantityOrdered}</td>
                <td className="px-4 py-3 text-gray-500">{line.quantityShipped}</td>
                <td className="px-4 py-3 text-gray-500">
                  {line.quantityReceived}{line.quantityDamaged > 0 && <span className="text-red-600"> +{line.quantityDamaged} damaged</span>}
                </td>
                <td className="px-4 py-3 text-gray-900">{line.outstanding}</td>
                <td className="px-4 py-3">
                  <input type="number" min={0} max={line.outstanding} value={c.good} disabled={line.outstanding === 0 || unsure}
                    aria-label={`Good units of ${productName(line.productId)}`}
                    onChange={(e) => set(line.id, { good: e.target.value })} className={`${inputClass} w-24`} />
                </td>
                <td className="px-4 py-3">
                  <input type="number" min={0} max={line.outstanding} value={c.damaged} disabled={line.outstanding === 0 || unsure}
                    aria-label={`Damaged units of ${productName(line.productId)}`}
                    onChange={(e) => set(line.id, { damaged: e.target.value })} className={`${inputClass} w-24`} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <label className="mb-4 block max-w-xl text-xs font-medium text-gray-600">
        Note <span className="font-normal text-gray-400">(optional, e.g. what was damaged)</span>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={500} className={`${inputClass} mt-1`} />
      </label>

      <div className="flex items-center gap-3">
        <button onClick={() => (unsure ? mutation.mutate() : setConfirming(true))} disabled={(!valid && !unsure) || mutation.isPending}
          className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
          {unsure ? 'Retry' : 'Record receipt'}
        </button>
        {unsure && (
          <button onClick={async () => { setUnsure(false); setIdempotencyKey(newKey()); const fresh = (await refetch()).data; if (fresh) seedCounts(fresh) }}
            className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">
            Start over
          </button>
        )}
        {!valid && !unsure && <span className="text-xs text-red-600">Each line's good + damaged must be between 0 and what's outstanding, and something must be counted.</span>}
      </div>

      {confirming && (
        <Modal title={`Confirm receipt for ${po.poNumber}`} onClose={() => setConfirming(false)}>
          <p className="text-sm text-gray-600">
            <strong className="text-gray-900">{totalGood}</strong> good unit{totalGood === 1 ? '' : 's'} go into stock
            {totalDamaged > 0 && <>; <strong className="text-red-600">{totalDamaged}</strong> damaged are recorded on the PO only</>}.
            This can't be undone here; fix mistakes later with a count correction on the Inventory page.
          </p>
          <div className="mt-4 flex justify-end gap-2">
            <button onClick={() => setConfirming(false)} className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">Back</button>
            <button onClick={() => mutation.mutate()} disabled={mutation.isPending}
              className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
              {mutation.isPending ? 'Recording…' : 'Confirm'}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
