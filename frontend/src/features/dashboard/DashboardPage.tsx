import { useQuery } from '@tanstack/react-query'
import { ShoppingBag, ClipboardList, Clock, AlertTriangle } from 'lucide-react'
import { getProducts } from '@/api/products'
import { getOrderStats } from '@/api/admin'
import { getAllInventory } from '@/api/inventory'
import { formatCurrency } from '@/lib/format'
import { useAuthStore } from '@/stores/authStore'


export default function DashboardPage() {
  const username = useAuthStore((s) => s.username)

  const { data: products = [] } = useQuery({ queryKey: ['products'], queryFn: getProducts })
  const { data: inventory = [] } = useQuery({ queryKey: ['inventory'], queryFn: getAllInventory })

  // Counted by the database over every order. These used to be derived in the browser from
  // a full download of the orders table, which made them correct only while that table was
  // small enough to send in one response.
  const { data: stats } = useQuery({ queryKey: ['order-stats'], queryFn: getOrderStats })

  const activeProducts = products.filter((p) => p.active).length
  const inactiveProducts = products.length - activeProducts

  const pendingOrders = stats?.pendingOrders ?? 0
  const confirmedOrders = stats?.confirmedOrders ?? 0
  const totalRevenue = stats?.totalRevenue ?? 0

  // Each product's own reorder level decides "low"; an empty shelf always counts.
  const lowStockItems = inventory.filter((i) => i.lowStock || i.availableQuantity === 0)

  const productMap = new Map(products.map((p) => [p.id, p]))

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-lg font-semibold text-gray-900">Dashboard</h1>
        <p className="mt-0.5 text-sm text-gray-500">Welcome back, {username}</p>
      </div>

      <div className="mb-8 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard
          icon={<ShoppingBag size={16} />}
          label="Products"
          value={products.length}
          sub={inactiveProducts > 0 ? `${inactiveProducts} inactive` : 'All active'}
          subColor={inactiveProducts > 0 ? 'text-amber-600' : 'text-gray-400'}
        />
        <StatCard
          icon={<ClipboardList size={16} />}
          label="Confirmed orders"
          value={confirmedOrders}
          sub={`${formatCurrency(totalRevenue)} revenue`}
          subColor="text-gray-400"
        />
        <StatCard
          icon={<Clock size={16} />}
          label="Pending orders"
          value={pendingOrders}
          sub={pendingOrders > 0 ? 'Needs attention' : 'All clear'}
          subColor={pendingOrders > 0 ? 'text-amber-600' : 'text-gray-400'}
          highlight={pendingOrders > 0 ? 'amber' : undefined}
        />
        <StatCard
          icon={<AlertTriangle size={16} />}
          label="Low stock"
          value={lowStockItems.length}
          sub={lowStockItems.length > 0 ? 'Items below 5 units' : 'Stock levels healthy'}
          subColor={lowStockItems.length > 0 ? 'text-red-500' : 'text-gray-400'}
          highlight={lowStockItems.length > 0 ? 'red' : undefined}
        />
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold text-gray-900">Low stock items</h2>

        {lowStockItems.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white px-4 py-10 text-center">
            <p className="text-sm text-gray-400">All stock levels are healthy.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
                  <th className="px-4 py-3">Product</th>
                  <th className="px-4 py-3">SKU</th>
                  <th className="px-4 py-3">Available</th>
                  <th className="px-4 py-3">Reserved</th>
                  <th className="px-4 py-3">Location</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {lowStockItems.map((item) => {
                  const product = productMap.get(item.productId)
                  return (
                    <tr key={item.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {product?.name ?? `Product #${item.productId}`}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-400">
                        {product?.sku ?? '-'}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                          item.availableQuantity === 0
                            ? 'bg-red-100 text-red-600'
                            : 'bg-amber-100 text-amber-700'
                        }`}>
                          {item.availableQuantity === 0 ? 'Out of stock' : `${item.availableQuantity} left`}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">{item.reservedQuantity}</td>
                      <td className="px-4 py-3 text-xs text-gray-400">{item.warehouseLocation ?? '-'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

interface StatCardProps {
  icon: React.ReactNode
  label: string
  value: number
  sub: string
  subColor: string
  highlight?: 'amber' | 'red'
}

function StatCard({ icon, label, value, sub, subColor, highlight }: StatCardProps) {
  const borderColor = highlight === 'red'
    ? 'border-red-200'
    : highlight === 'amber'
    ? 'border-amber-200'
    : 'border-gray-200'

  const iconColor = highlight === 'red'
    ? 'text-red-400'
    : highlight === 'amber'
    ? 'text-amber-500'
    : 'text-gray-400'

  return (
    <div className={`rounded-lg border ${borderColor} bg-white p-5`}>
      <div className={`mb-3 ${iconColor}`}>{icon}</div>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
      <p className="mt-0.5 text-xs text-gray-500">{label}</p>
      <p className={`mt-2 text-xs ${subColor}`}>{sub}</p>
    </div>
  )
}
