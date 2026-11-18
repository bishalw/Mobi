import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { sendShipmentNotice } from '@/api/procurement'
import Modal, { inputClass } from '@/components/Modal'
import { apiMessage } from '@/features/orders/orderStatus'
import type { PurchaseOrder } from '@/types'

/** Supplier: tell Mobi what's on its way. Quantities default to everything not yet counted in. */
export default function ShipmentNoticeDialog({ po, productName, onClose }: {
  po: PurchaseOrder
  productName: (productId: number) => string
  onClose: () => void
}) {
  const qc = useQueryClient()
  // The server caps each line at what hasn't been counted in yet (so replacements for a
  // short delivery can be announced).
  const limit = (line: PurchaseOrder['lines'][number]) => line.outstanding
  const [carrier, setCarrier] = useState('')
  const [tracking, setTracking] = useState('')
  const [arrival, setArrival] = useState(po.expectedDate ?? '')
  const [qty, setQty] = useState<Record<number, string>>(Object.fromEntries(po.lines.map((l) => [l.id, String(limit(l))])))

  const lines = po.lines.map((l) => ({ line: l, value: Number(qty[l.id] || 0) }))
  const invalid = lines.some(({ line, value }) => !Number.isInteger(value) || value < 0 || value > limit(line))
  const total = lines.reduce((s, l) => s + l.value, 0)
  const valid = carrier.trim() && !invalid && total > 0

  const mutation = useMutation({
    mutationFn: () => sendShipmentNotice(po.id, {
      carrier: carrier.trim(),
      trackingNumber: tracking.trim() || undefined,
      expectedArrival: arrival || undefined,
      lines: lines.filter((l) => l.value > 0).map((l) => ({ poLineId: l.line.id, quantityShipped: l.value })),
    }),
    onSuccess: (updated) => {
      qc.setQueryData(['purchase-order', po.id], updated)
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
      toast.success(`Shipment notice sent for ${po.poNumber}`)
      onClose()
    },
    onError: (err) => toast.error(apiMessage(err, 'Could not send the shipment notice')),
  })

  return (
    <Modal title={`Shipment notice for ${po.poNumber}`} onClose={onClose} wide>
      <form onSubmit={(e) => { e.preventDefault(); if (valid) mutation.mutate() }} className="space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <label className="block text-xs font-medium text-gray-600">
            Carrier
            <input value={carrier} onChange={(e) => setCarrier(e.target.value)} maxLength={100} className={`${inputClass} mt-1`} />
          </label>
          <label className="block text-xs font-medium text-gray-600">
            Tracking number <span className="font-normal text-gray-400">(optional)</span>
            <input value={tracking} onChange={(e) => setTracking(e.target.value)} maxLength={100} className={`${inputClass} mt-1`} />
          </label>
          <label className="block text-xs font-medium text-gray-600">
            Expected arrival
            <input type="date" value={arrival} onChange={(e) => setArrival(e.target.value)} className={`${inputClass} mt-1`} />
          </label>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs font-medium text-gray-500">
              <th className="py-2">Product</th>
              <th className="py-2">Ordered</th>
              <th className="py-2">Already shipped</th>
              <th className="py-2">Outstanding</th>
              <th className="py-2">In this shipment</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {lines.map(({ line, value }) => (
              <tr key={line.id}>
                <td className="py-2 text-gray-900">{productName(line.productId)}</td>
                <td className="py-2 text-gray-600">{line.quantityOrdered}</td>
                <td className="py-2 text-gray-600">{line.quantityShipped}</td>
                <td className="py-2 text-gray-600">{line.outstanding}</td>
                <td className="py-2">
                  <input type="number" min={0} max={limit(line)} value={qty[line.id]} disabled={limit(line) === 0}
                    aria-label={`Units of ${productName(line.productId)} shipped`}
                    onChange={(e) => setQty((q) => ({ ...q, [line.id]: e.target.value }))}
                    className={`${inputClass} w-24 ${value > limit(line) ? 'border-red-300' : ''}`} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="text-xs text-gray-500">Each line can announce at most what Mobi hasn't counted in yet. Mobi's stock only changes when its warehouse counts the goods in.</p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
          <button type="submit" disabled={!valid || mutation.isPending}
            className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50">
            {mutation.isPending ? 'Sending…' : `Send notice (${total} units)`}
          </button>
        </div>
      </form>
    </Modal>
  )
}
