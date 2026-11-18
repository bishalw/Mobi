import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import Modal, { inputClass, useDialogKeyboard } from '@/components/Modal'
import toast from 'react-hot-toast'
import { adjustStock, getStockHistory, updateInventoryDetails } from '@/api/inventory'
import { apiMessage } from '@/features/orders/orderStatus'
import { formatDate } from '@/lib/format'
import type { AdjustmentReason, InventoryResponse } from '@/types'

export const reasonLabel: Record<AdjustmentReason, string> = {
  INITIAL: 'Opening stock',
  RECEIVED: 'Received (PO)',
  DAMAGED: 'Damaged',
  LOST: 'Lost',
  COUNT_CORRECTION: 'Count correction',
}

// What a person can pick by hand. RECEIVED only comes from receiving a purchase order.
const manualReasons: { reason: AdjustmentReason; direction: 'add' | 'remove' | 'either'; hint: string }[] = [
  { reason: 'COUNT_CORRECTION', direction: 'either', hint: 'A stock count found a different number' },
  { reason: 'DAMAGED', direction: 'remove', hint: 'Broken or unsellable, written off' },
  { reason: 'LOST', direction: 'remove', hint: 'Missing from the shelf' },
  { reason: 'INITIAL', direction: 'add', hint: 'First stock for a product' },
]


function useInvalidateStock() {
  const qc = useQueryClient()
  return (productId: number) => {
    qc.invalidateQueries({ queryKey: ['inventory'] })
    qc.invalidateQueries({ queryKey: ['stock-history', productId] })
  }
}

interface AdjustProps {
  productId: number
  productName: string
  item?: InventoryResponse
  onClose: () => void
}

export function AdjustStockDialog({ productId, productName, item, onClose }: AdjustProps) {
  const unstocked = !item
  const [reason, setReason] = useState<AdjustmentReason>(unstocked ? 'INITIAL' : 'COUNT_CORRECTION')
  const [direction, setDirection] = useState<'add' | 'remove'>('add')
  const [quantity, setQuantity] = useState('')
  const [note, setNote] = useState('')
  const invalidate = useInvalidateStock()

  const rule = manualReasons.find((r) => r.reason === reason)!
  const sign = rule.direction === 'either' ? (direction === 'add' ? 1 : -1) : rule.direction === 'add' ? 1 : -1
  const amount = Number(quantity)
  const valid = Number.isInteger(amount) && amount > 0 && (reason === 'INITIAL' || note.trim().length > 0)
  const current = item?.availableQuantity ?? 0

  const mutation = useMutation({
    mutationFn: () => adjustStock(productId, sign * amount, reason, note.trim() || undefined),
    onSuccess: (result) => {
      invalidate(productId)
      toast.success(`${productName}: now ${result.item.availableQuantity} available`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not adjust stock')),
  })

  const choices = unstocked ? manualReasons.filter((r) => r.reason === 'INITIAL') : manualReasons

  return (
    <Modal title={`Adjust stock: ${productName}`} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); if (valid) mutation.mutate() }} className="space-y-4">
        <label className="block">
          <span className="mb-1 block text-xs font-medium text-gray-700">Reason</span>
          <select value={reason} onChange={(e) => setReason(e.target.value as AdjustmentReason)} className={inputClass}>
            {choices.map((r) => (
              <option key={r.reason} value={r.reason}>{reasonLabel[r.reason]}</option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-gray-400">{rule.hint}</span>
        </label>

        <div className="flex gap-3">
          {rule.direction === 'either' && (
            <label className="block">
              <span className="mb-1 block text-xs font-medium text-gray-700">Direction</span>
              <select value={direction} onChange={(e) => setDirection(e.target.value as 'add' | 'remove')} className={inputClass}>
                <option value="add">Add</option>
                <option value="remove">Remove</option>
              </select>
            </label>
          )}
          <label className="block flex-1">
            <span className="mb-1 block text-xs font-medium text-gray-700">Quantity</span>
            <input type="number" min={1} step={1} value={quantity} onChange={(e) => setQuantity(e.target.value)}
              required className={inputClass} />
          </label>
        </div>

        <label className="block">
          <span className="mb-1 block text-xs font-medium text-gray-700">
            Note {reason === 'INITIAL' ? '(optional)' : '(required)'}
          </span>
          <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500}
            placeholder={reason === 'DAMAGED' ? 'e.g. Screen cracked in storage' : 'e.g. Cycle count, aisle B'}
            className={inputClass} />
        </label>

        {amount > 0 && (
          <p className="text-xs text-gray-500">
            Available: {current} → <span className="font-medium text-gray-900">{current + sign * amount}</span>
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose}
            className="rounded-md border border-gray-200 px-4 py-2 text-xs font-medium text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
          <button type="submit" disabled={!valid || mutation.isPending}
            className="rounded-md bg-gray-900 px-4 py-2 text-xs font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : 'Save adjustment'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function StockDetailsDialog({ item, productName, onClose }: { item: InventoryResponse; productName: string; onClose: () => void }) {
  const [location, setLocation] = useState(item.warehouseLocation ?? '')
  const [reorderLevel, setReorderLevel] = useState(String(item.reorderLevel))
  const invalidate = useInvalidateStock()
  const level = Number(reorderLevel)

  const mutation = useMutation({
    mutationFn: () => updateInventoryDetails(item.productId, { warehouseLocation: location, reorderLevel: level }),
    onSuccess: () => {
      invalidate(item.productId)
      toast.success(`${productName} updated`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not save')),
  })

  return (
    <Modal title={`Shelf details: ${productName}`} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate() }} className="space-y-4">
        <label className="block">
          <span className="mb-1 block text-xs font-medium text-gray-700">Location</span>
          <input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={100}
            placeholder="e.g. Shelf A1" className={inputClass} />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs font-medium text-gray-700">Reorder level</span>
          <input type="number" min={0} step={1} value={reorderLevel} onChange={(e) => setReorderLevel(e.target.value)}
            className={inputClass} />
          <span className="mt-1 block text-xs text-gray-400">Flagged as low stock at or below this. 0 turns it off.</span>
        </label>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose}
            className="rounded-md border border-gray-200 px-4 py-2 text-xs font-medium text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
          <button type="submit" disabled={reorderLevel.trim() === '' || !Number.isInteger(level) || level < 0 || mutation.isPending}
            className="rounded-md bg-gray-900 px-4 py-2 text-xs font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function StockHistoryDrawer({ productId, productName, onClose }: { productId: number; productName: string; onClose: () => void }) {
  const [page, setPage] = useState(0)
  const drawer = useRef<HTMLElement>(null)
  useDialogKeyboard(drawer, onClose)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['stock-history', productId, page],
    queryFn: () => getStockHistory(productId, page),
  })

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <aside ref={drawer} role="dialog" aria-modal="true" aria-label={`Stock history for ${productName}`}
        onClick={(e) => e.stopPropagation()}
        className="flex h-full w-full max-w-md flex-col border-l border-gray-200 bg-white shadow-lg">
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">Stock history</h2>
            <p className="text-xs text-gray-500">{productName}</p>
          </div>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-700">
            <X size={16} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto">
          {isLoading && <p className="px-5 py-8 text-center text-sm text-gray-400">Loading…</p>}
          {isError && <p className="px-5 py-8 text-center text-sm text-red-500">Could not load history.</p>}
          {data && data.content.length === 0 && (
            <p className="px-5 py-8 text-center text-sm text-gray-400">No adjustments yet.</p>
          )}
          <ul className="divide-y divide-gray-100">
            {data?.content.map((a) => (
              <li key={a.id} className="px-5 py-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-gray-900">{reasonLabel[a.reason]}</span>
                  <span className={`text-sm font-semibold ${a.quantityDelta > 0 ? 'text-green-700' : 'text-red-600'}`}>
                    {a.quantityDelta > 0 ? `+${a.quantityDelta}` : a.quantityDelta}
                  </span>
                </div>
                {a.note && <p className="mt-0.5 text-xs text-gray-600">{a.note}</p>}
                <p className="mt-1 text-xs text-gray-400">
                  {formatDate(a.createdAt)} · {a.createdBy} · {a.availableAfter} after
                  {a.referenceType === 'PO_RECEIPT' && ` · receipt ${a.referenceId}`}
                </p>
              </li>
            ))}
          </ul>
        </div>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-gray-200 px-5 py-3 text-xs">
            <span className="text-gray-500">Page {page + 1} of {data.totalPages}</span>
            <div className="flex gap-2">
              <button onClick={() => setPage((n) => Math.max(0, n - 1))} disabled={page === 0}
                className="rounded-md border border-gray-200 px-2.5 py-1 text-gray-600 disabled:opacity-40">Newer</button>
              <button onClick={() => setPage((n) => n + 1)} disabled={page + 1 >= data.totalPages}
                className="rounded-md border border-gray-200 px-2.5 py-1 text-gray-600 disabled:opacity-40">Older</button>
            </div>
          </div>
        )}
      </aside>
    </div>
  )
}
