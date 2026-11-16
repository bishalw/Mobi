import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAllInventory } from '@/api/inventory'
import { getProducts } from '@/api/products'
import { formatDate } from '@/lib/format'
import type { InventoryResponse } from '@/types'
import { AdjustStockDialog, StockDetailsDialog, StockHistoryDrawer } from './StockDialogs'

function StockBadge({ item }: { item?: InventoryResponse }) {
  if (!item) return (
    <span className="inline-flex rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500">Not stocked</span>
  )
  if (item.availableQuantity === 0) return (
    <span className="inline-flex rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">Out of stock</span>
  )
  if (item.lowStock) return (
    <span title={`At or below reorder level ${item.reorderLevel}`}
      className="inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Low stock</span>
  )
  return (
    <span className="inline-flex rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">Available</span>
  )
}

type Open =
  | { kind: 'adjust'; productId: number }
  | { kind: 'details'; productId: number }
  | { kind: 'history'; productId: number }
  | null

export default function InventoryPage() {
  const [open, setOpen] = useState<Open>(null)

  const { data: inventory = [], isLoading: invLoading } = useQuery({
    queryKey: ['inventory'],
    queryFn: getAllInventory,
  })

  const { data: products = [], isLoading: prodLoading } = useQuery({
    queryKey: ['products'],
    queryFn: getProducts,
  })

  if (invLoading || prodLoading) return <p className="text-sm text-gray-500">Loading…</p>

  // Every product gets a row, stocked or not, so a new product can be given opening stock here.
  const stockByProduct = new Map(inventory.map((i) => [i.productId, i]))
  const rows = [
    ...products.map((p) => ({ productId: p.id, name: p.name, sku: p.sku, item: stockByProduct.get(p.id) })),
    ...inventory
      .filter((i) => !products.some((p) => p.id === i.productId))
      .map((i) => ({ productId: i.productId, name: `Product #${i.productId}`, sku: undefined, item: i })),
  ]
  const nameOf = (productId: number) => rows.find((r) => r.productId === productId)?.name ?? `Product #${productId}`
  const openItem = open ? stockByProduct.get(open.productId) : undefined

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-gray-900">Inventory</h1>
        <p className="mt-0.5 text-sm text-gray-500">
          Every change is recorded with who made it and why. Deliveries are added by receiving a purchase order.
        </p>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Product</th>
              <th className="px-4 py-3">SKU</th>
              <th className="px-4 py-3">Available</th>
              <th className="px-4 py-3">Reserved</th>
              <th className="px-4 py-3">Reorder at</th>
              <th className="px-4 py-3">Location</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Updated</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.map(({ productId, name, sku, item }) => (
              <tr key={productId} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-900">{name}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{sku ?? '—'}</td>
                <td className="px-4 py-3 font-medium text-gray-900">{item?.availableQuantity ?? '—'}</td>
                <td className="px-4 py-3 text-gray-500">{item?.reservedQuantity ?? '—'}</td>
                <td className="px-4 py-3 text-gray-500">{item?.reorderLevel ? item.reorderLevel : '—'}</td>
                <td className="px-4 py-3 text-gray-500">{item?.warehouseLocation ?? '—'}</td>
                <td className="px-4 py-3"><StockBadge item={item} /></td>
                <td className="px-4 py-3 text-xs text-gray-400">{item ? formatDate(item.updatedAt) : '—'}</td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2 whitespace-nowrap">
                    <button onClick={() => setOpen({ kind: 'adjust', productId })}
                      className="rounded-md border border-gray-200 px-2.5 py-1 text-xs text-gray-700 hover:bg-gray-50">
                      {item ? 'Adjust' : 'Add stock'}
                    </button>
                    {item && (
                      <>
                        <button onClick={() => setOpen({ kind: 'details', productId })}
                          className="rounded-md border border-gray-200 px-2.5 py-1 text-xs text-gray-600 hover:bg-gray-50">
                          Edit
                        </button>
                        <button onClick={() => setOpen({ kind: 'history', productId })}
                          className="rounded-md border border-gray-200 px-2.5 py-1 text-xs text-gray-600 hover:bg-gray-50">
                          History
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={9} className="px-4 py-8 text-center text-sm text-gray-400">No products yet.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {open?.kind === 'adjust' && (
        <AdjustStockDialog productId={open.productId} productName={nameOf(open.productId)} item={openItem}
          onClose={() => setOpen(null)} />
      )}
      {open?.kind === 'details' && openItem && (
        <StockDetailsDialog item={openItem} productName={nameOf(open.productId)} onClose={() => setOpen(null)} />
      )}
      {open?.kind === 'history' && (
        <StockHistoryDrawer productId={open.productId} productName={nameOf(open.productId)} onClose={() => setOpen(null)} />
      )}
    </div>
  )
}
