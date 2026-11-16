import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { createSupplier, getSupplierCatalogue, getSuppliers, replaceSupplierCatalogue, updateSupplier } from '@/api/procurement'
import { getProducts } from '@/api/products'
import Modal, { inputClass, useDialogKeyboard } from '@/components/Modal'
import { apiMessage } from '@/features/orders/orderStatus'
import { formatCurrency, formatDate } from '@/lib/format'
import { isMoney } from '@/lib/validation'
import type { ProductResponse, Supplier } from '@/types'

type Open = { kind: 'edit'; supplier?: Supplier } | { kind: 'catalogue'; supplier: Supplier } | null

export default function SuppliersPage() {
  const [open, setOpen] = useState<Open>(null)
  const { data: suppliers = [], isLoading } = useQuery({ queryKey: ['suppliers'], queryFn: getSuppliers })

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Suppliers</h1>
          <p className="mt-0.5 text-sm text-gray-500">Who we buy from, what each sells us, and at what unit cost.</p>
        </div>
        <button onClick={() => setOpen({ kind: 'edit' })}
          className="inline-flex items-center gap-1.5 rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800">
          <Plus size={15} /> Add supplier
        </button>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Name</th>
              <th className="px-4 py-3">Contact email</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Added</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {suppliers.map((s) => (
              <tr key={s.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-900">{s.name}</td>
                <td className="px-4 py-3 text-gray-600">{s.contactEmail}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${s.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                    {s.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-gray-400">{formatDate(s.createdAt)}</td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2 whitespace-nowrap">
                    <button onClick={() => setOpen({ kind: 'catalogue', supplier: s })}
                      className="rounded-md border border-gray-200 px-2.5 py-1 text-xs text-gray-700 hover:bg-gray-50">Catalogue</button>
                    <button onClick={() => setOpen({ kind: 'edit', supplier: s })}
                      className="rounded-md border border-gray-200 px-2.5 py-1 text-xs text-gray-600 hover:bg-gray-50">Edit</button>
                  </div>
                </td>
              </tr>
            ))}
            {!isLoading && suppliers.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-400">No suppliers yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {open?.kind === 'edit' && <SupplierDialog supplier={open.supplier} onClose={() => setOpen(null)} />}
      {open?.kind === 'catalogue' && <CatalogueDrawer supplier={open.supplier} onClose={() => setOpen(null)} />}
    </div>
  )
}

function SupplierDialog({ supplier, onClose }: { supplier?: Supplier; onClose: () => void }) {
  const qc = useQueryClient()
  const [name, setName] = useState(supplier?.name ?? '')
  const [contactEmail, setContactEmail] = useState(supplier?.contactEmail ?? '')
  const [active, setActive] = useState(supplier?.active ?? true)

  const mutation = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), contactEmail: contactEmail.trim(), active }
      return supplier ? updateSupplier(supplier.id, body) : createSupplier(body)
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ['suppliers'] })
      toast.success(`${saved.name} saved`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not save the supplier')),
  })
  const valid = name.trim() && /\S+@\S+\.\S+/.test(contactEmail)

  return (
    <Modal title={supplier ? `Edit ${supplier.name}` : 'Add supplier'} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); if (valid) mutation.mutate() }} className="space-y-3">
        <label className="block text-xs font-medium text-gray-600">
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={150} className={`${inputClass} mt-1`} />
        </label>
        <label className="block text-xs font-medium text-gray-600">
          Contact email <span className="font-normal text-gray-400">(purchase orders are sent here)</span>
          <input type="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} className={`${inputClass} mt-1`} />
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Active <span className="text-xs text-gray-400">(inactive suppliers can't be sent new orders)</span>
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
          <button type="submit" disabled={!valid || mutation.isPending}
            className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface Row { included: boolean; supplierSku: string; unitCost: string }

const NO_PRODUCTS: ProductResponse[] = []

/** Every product, ticked if this supplier sells it. Saving replaces the whole catalogue. */
function CatalogueDrawer({ supplier, onClose }: { supplier: Supplier; onClose: () => void }) {
  const qc = useQueryClient()
  // Not `= []`: a fresh array each render would re-run the effect below forever.
  const { data: products = NO_PRODUCTS } = useQuery({ queryKey: ['products'], queryFn: getProducts })
  const { data: catalogue, isLoading } = useQuery({
    queryKey: ['supplier-catalogue', supplier.id],
    queryFn: () => getSupplierCatalogue(supplier.id),
  })
  const [rows, setRows] = useState<Record<number, Row>>({})
  const drawer = useRef<HTMLElement>(null)
  useDialogKeyboard(drawer, onClose)
  // Seed the rows once both lists have loaded; a background refetch later must not wipe
  // ticks and costs the admin hasn't saved yet.
  const seeded = useRef(false)

  useEffect(() => {
    if (!catalogue || products.length === 0 || seeded.current) return
    seeded.current = true
    const next: Record<number, Row> = {}
    for (const p of products) next[p.id] = { included: false, supplierSku: '', unitCost: '' }
    for (const c of catalogue) {
      next[c.productId] = { included: true, supplierSku: c.supplierSku ?? '', unitCost: c.unitCost != null ? String(c.unitCost) : '' }
    }
    setRows(next)
  }, [catalogue, products])

  const set = (productId: number, patch: Partial<Row>) =>
    setRows((r) => ({ ...r, [productId]: { ...r[productId], ...patch } }))

  const chosen = Object.entries(rows).filter(([, r]) => r.included)
  const valid = chosen.every(([, r]) => isMoney(r.unitCost))

  const mutation = useMutation({
    mutationFn: () => replaceSupplierCatalogue(supplier.id, chosen.map(([productId, r]) => ({
      productId: Number(productId),
      supplierSku: r.supplierSku.trim() || null,
      unitCost: Number(r.unitCost),
    }))),
    onSuccess: (saved) => {
      qc.setQueryData(['supplier-catalogue', supplier.id], saved)
      toast.success(`${supplier.name}: ${saved.length} product${saved.length === 1 ? '' : 's'} in catalogue`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not save the catalogue')),
  })

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <aside ref={drawer} role="dialog" aria-modal="true" aria-label={`Catalogue for ${supplier.name}`} onClick={(e) => e.stopPropagation()}
        className="flex h-full w-full max-w-xl flex-col border-l border-gray-200 bg-white shadow-lg">
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">Catalogue</h2>
            <p className="text-xs text-gray-500">{supplier.name} · tick what they sell; the unit cost is the default on new POs</p>
          </div>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-700"><X size={16} /></button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {isLoading ? <p className="px-5 py-8 text-center text-sm text-gray-400">Loading…</p> : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-left text-xs font-medium text-gray-500">
                  <th className="px-5 py-2">Product</th>
                  <th className="px-2 py-2">Supplier SKU</th>
                  <th className="px-5 py-2">Unit cost</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {products.map((p) => {
                  const row = rows[p.id]
                  if (!row) return null
                  return (
                    <tr key={p.id}>
                      <td className="px-5 py-2">
                        <label className="flex items-center gap-2">
                          <input type="checkbox" checked={row.included} onChange={(e) => set(p.id, { included: e.target.checked })} />
                          <span className="text-gray-900">{p.name}</span>
                        </label>
                        <span className="ml-6 text-xs text-gray-400">retail {formatCurrency(p.price)}</span>
                      </td>
                      <td className="px-2 py-2">
                        <input value={row.supplierSku} disabled={!row.included} onChange={(e) => set(p.id, { supplierSku: e.target.value })}
                          aria-label={`Supplier SKU for ${p.name}`} maxLength={100} className={`${inputClass} disabled:bg-gray-50`} />
                      </td>
                      <td className="px-5 py-2">
                        <input type="number" min={0.01} step={0.01} value={row.unitCost} disabled={!row.included}
                          aria-label={`Unit cost for ${p.name}`} onChange={(e) => set(p.id, { unitCost: e.target.value })}
                          className={`${inputClass} w-28 disabled:bg-gray-50`} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
        <div className="flex items-center justify-between border-t border-gray-200 px-5 py-3">
          <span className="text-xs text-gray-500">{chosen.length} selected{!valid && ' · every ticked product needs a unit cost above 0, with at most 2 decimals'}</span>
          <button onClick={() => mutation.mutate()} disabled={!valid || mutation.isPending || isLoading || Object.keys(rows).length === 0}
            className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : 'Save catalogue'}
          </button>
        </div>
      </aside>
    </div>
  )
}
