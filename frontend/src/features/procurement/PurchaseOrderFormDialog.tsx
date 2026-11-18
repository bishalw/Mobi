import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'
import { createPurchaseOrder, getSupplierCatalogue, getSuppliers, updatePurchaseOrder } from '@/api/procurement'
import { getProducts } from '@/api/products'
import Modal, { inputClass } from '@/components/Modal'
import { apiMessage } from '@/features/orders/orderStatus'
import { formatCurrency } from '@/lib/format'
import { isMoney, isQuantity } from '@/lib/validation'
import type { PurchaseOrder } from '@/types'

interface LineDraft {
  productId: number | ''
  quantity: string
  unitCost: string // blank = the supplier's catalogue price
}

/** Create a draft PO, or edit one (`existing`). Products are limited to the supplier's catalogue. */
export default function PurchaseOrderFormDialog({ existing, onClose }: { existing?: PurchaseOrder; onClose: () => void }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [supplierId, setSupplierId] = useState<number | ''>(existing?.supplierId ?? '')
  const [expectedDate, setExpectedDate] = useState(existing?.expectedDate ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [lines, setLines] = useState<LineDraft[]>(
    existing?.lines.map((l) => ({ productId: l.productId, quantity: String(l.quantityOrdered), unitCost: String(l.unitCost ?? '') }))
      ?? [{ productId: '', quantity: '', unitCost: '' }],
  )

  const { data: suppliers = [] } = useQuery({ queryKey: ['suppliers'], queryFn: getSuppliers })
  const { data: products = [] } = useQuery({ queryKey: ['products'], queryFn: getProducts })
  const { data: catalogue = [], isFetching: catalogueLoading } = useQuery({
    queryKey: ['supplier-catalogue', supplierId],
    queryFn: () => getSupplierCatalogue(Number(supplierId)),
    enabled: supplierId !== '',
  })

  const productName = (id: number) => products.find((p) => p.id === id)?.name ?? `Product #${id}`
  const priceOf = (id: number | '') => catalogue.find((c) => c.productId === id)?.unitCost ?? undefined

  const mutation = useMutation({
    mutationFn: () => {
      const body = {
        supplierId: Number(supplierId),
        expectedDate: expectedDate || null,
        notes: notes.trim() || null,
        lines: lines.map((l) => ({
          productId: Number(l.productId),
          quantity: Number(l.quantity),
          unitCost: l.unitCost.trim() ? Number(l.unitCost) : null,
        })),
      }
      return existing ? updatePurchaseOrder(existing.id, body) : createPurchaseOrder(body)
    },
    onSuccess: (po) => {
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
      qc.setQueryData(['purchase-order', po.id], po)
      toast.success(existing ? `${po.poNumber} saved` : `${po.poNumber} created as a draft`)
      onClose()
      if (!existing) navigate(`/purchase-orders/${po.id}`)
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not save the purchase order')),
  })

  const setLine = (index: number, patch: Partial<LineDraft>) =>
    setLines((ls) => ls.map((l, i) => (i === index ? { ...l, ...patch } : l)))

  const chosen = new Set(lines.map((l) => l.productId))
  const valid = supplierId !== '' && lines.length > 0
    && lines.every((l) => l.productId !== '' && isQuantity(l.quantity) && (l.unitCost.trim() === '' || isMoney(l.unitCost)))
  const total = lines.reduce((sum, l) => {
    const cost = l.unitCost.trim() ? Number(l.unitCost) : priceOf(l.productId) ?? 0
    return sum + cost * (Number(l.quantity) || 0)
  }, 0)

  return (
    <Modal title={existing ? `Edit ${existing.poNumber}` : 'New purchase order'} onClose={onClose} wide>
      <form onSubmit={(e) => { e.preventDefault(); if (valid) mutation.mutate() }} className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label className="block text-xs font-medium text-gray-600">
            Supplier
            <select value={supplierId} className={`${inputClass} mt-1`}
              onChange={(e) => { setSupplierId(e.target.value ? Number(e.target.value) : ''); setLines([{ productId: '', quantity: '', unitCost: '' }]) }}>
              <option value="">Choose a supplier…</option>
              {suppliers.filter((s) => s.active || s.id === existing?.supplierId).map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </label>
          <label className="block text-xs font-medium text-gray-600">
            Expected delivery <span className="font-normal text-gray-400">(optional)</span>
            <input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} className={`${inputClass} mt-1`} />
          </label>
        </div>

        <div>
          <div className="mb-1 flex items-center justify-between">
            <span className="text-xs font-medium text-gray-600">Lines</span>
            {supplierId !== '' && !catalogueLoading && catalogue.length === 0 && (
              <span className="text-xs text-amber-600">This supplier has no catalogue yet. Add products on the Suppliers page.</span>
            )}
          </div>
          <div className="space-y-2">
            {lines.map((line, index) => (
              <div key={index} className="grid grid-cols-[1fr_5rem_6.5rem_2rem] items-center gap-2">
                <select value={line.productId} aria-label="Product" disabled={supplierId === ''} className={inputClass}
                  onChange={(e) => setLine(index, { productId: e.target.value ? Number(e.target.value) : '' })}>
                  <option value="">Product…</option>
                  {catalogue.filter((c) => c.productId === line.productId || !chosen.has(c.productId)).map((c) => (
                    <option key={c.productId} value={c.productId}>{productName(c.productId)}</option>
                  ))}
                </select>
                <input type="number" min={1} max={100000} step={1} value={line.quantity} placeholder="Qty" aria-label="Quantity"
                  onChange={(e) => setLine(index, { quantity: e.target.value })} className={inputClass} />
                <input type="number" min={0.01} step={0.01} value={line.unitCost} aria-label="Unit cost"
                  placeholder={priceOf(line.productId) != null ? String(priceOf(line.productId)) : 'Unit cost'}
                  onChange={(e) => setLine(index, { unitCost: e.target.value })} className={inputClass} />
                <button type="button" aria-label="Remove line" disabled={lines.length === 1}
                  onClick={() => setLines((ls) => ls.filter((_, i) => i !== index))}
                  className="flex justify-center text-gray-400 hover:text-red-600 disabled:opacity-30">
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
          </div>
          <div className="mt-2 flex items-center justify-between">
            <button type="button" disabled={supplierId === '' || lines.length >= catalogue.length}
              onClick={() => setLines((ls) => [...ls, { productId: '', quantity: '', unitCost: '' }])}
              className="inline-flex items-center gap-1 text-xs font-medium text-gray-700 hover:text-gray-900 disabled:opacity-40">
              <Plus size={14} /> Add line
            </button>
            <span className="text-xs text-gray-500">Unit cost defaults to the catalogue price · Total {formatCurrency(total)}</span>
          </div>
        </div>

        <label className="block text-xs font-medium text-gray-600">
          Notes for the supplier <span className="font-normal text-gray-400">(optional)</span>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} maxLength={1000} className={`${inputClass} mt-1`} />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
          <button type="submit" disabled={!valid || mutation.isPending}
            className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : existing ? 'Save draft' : 'Create draft'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
