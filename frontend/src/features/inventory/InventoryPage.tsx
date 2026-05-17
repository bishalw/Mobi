import { useQuery } from '@tanstack/react-query'
import { getAllInventory } from '@/api/inventory'
import { getProducts } from '@/api/products'
import { formatDate } from '@/lib/format'

function StockBadge({ available }: { available: number }) {
  if (available === 0) return (
    <span className="inline-flex rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">Out of stock</span>
  )
  if (available <= 5) return (
    <span className="inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Low stock</span>
  )
  return (
    <span className="inline-flex rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">Available</span>
  )
}

export default function InventoryPage() {
  const { data: inventory = [], isLoading: invLoading } = useQuery({
    queryKey: ['inventory'],
    queryFn: getAllInventory,
  })

  const { data: products = [], isLoading: prodLoading } = useQuery({
    queryKey: ['products'],
    queryFn: getProducts,
  })

  const productMap = Object.fromEntries(products.map((p) => [p.id, p]))
  const isLoading = invLoading || prodLoading

  if (isLoading) return <p className="text-sm text-gray-500">Loading…</p>

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-lg font-semibold text-gray-900">Inventory</h1>
      </div>

      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Product</th>
              <th className="px-4 py-3">SKU</th>
              <th className="px-4 py-3">Available</th>
              <th className="px-4 py-3">Reserved</th>
              <th className="px-4 py-3">Location</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Updated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {inventory.map((item) => {
              const product = productMap[item.productId]
              return (
                <tr key={item.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">{product?.name ?? `Product #${item.productId}`}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{product?.sku ?? '—'}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{item.availableQuantity}</td>
                  <td className="px-4 py-3 text-gray-500">{item.reservedQuantity}</td>
                  <td className="px-4 py-3 text-gray-500">{item.warehouseLocation ?? '—'}</td>
                  <td className="px-4 py-3">
                    <StockBadge available={item.availableQuantity} />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-400">{formatDate(item.updatedAt)}</td>
                </tr>
              )
            })}
            {inventory.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-sm text-gray-400">
                  No inventory records.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
