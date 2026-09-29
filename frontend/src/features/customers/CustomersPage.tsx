import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { getCustomers } from '@/api/admin'
import { formatCurrency, formatDate } from '@/lib/format'

const PAGE_SIZE = 25

// Sorting happens in the database, not here — the page only names the column. Sorting a
// single page in the browser would rank 25 arbitrary customers instead of all of them.
const SORTS = [
  { value: 'orderCount,desc', label: 'Most orders' },
  { value: 'totalSpend,desc', label: 'Highest spend' },
  { value: 'lastOrderAt,desc', label: 'Recently ordered' },
  { value: 'signedUpAt,desc', label: 'Newest signups' },
  { value: 'username,asc', label: 'Name (A–Z)' },
]

export default function CustomersPage() {
  const [search, setSearch] = useState('')
  const [term, setTerm] = useState('')
  const [sort, setSort] = useState(SORTS[0].value)
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-customers', term, sort, page],
    queryFn: () => getCustomers({ page, size: PAGE_SIZE, sort, search: term || undefined }),
  })

  const customers = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  function submitSearch(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setTerm(search.trim())
  }

  return (
    <div>
      <div className="mb-6 flex items-end justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Customers</h1>
          <p className="mt-0.5 text-sm text-gray-500">Who signed up, and what they have ordered</p>
        </div>

        <div className="flex items-center gap-2">
          <select
            value={sort}
            onChange={(e) => {
              setPage(0)
              setSort(e.target.value)
            }}
            className="rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 outline-none focus:border-gray-400"
          >
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          <form onSubmit={submitSearch} className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search customers"
              className="w-56 rounded-md border border-gray-200 py-2 pl-9 pr-3 text-sm outline-none focus:border-gray-400"
            />
          </form>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium text-gray-500">
              <th className="px-4 py-3">Customer</th>
              <th className="px-4 py-3">Email</th>
              <th className="px-4 py-3">Signed up</th>
              <th className="px-4 py-3 text-right">Orders</th>
              <th className="px-4 py-3 text-right">Total spend</th>
              <th className="px-4 py-3">Last order</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-gray-400">
                  Loading customers…
                </td>
              </tr>
            )}

            {!isLoading && customers.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-gray-400">
                  {term ? `No customers matching "${term}".` : 'No customers yet.'}
                </td>
              </tr>
            )}

            {customers.map((customer) => (
              <tr key={customer.userId} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-gray-900">{customer.username}</span>
                    {!customer.enabled && (
                      <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-600">
                        Disabled
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-500">
                  {customer.email ?? <span className="text-gray-300">—</span>}
                </td>
                <td className="px-4 py-3 text-xs text-gray-500">{formatDate(customer.signedUpAt)}</td>
                <td className="px-4 py-3 text-right font-medium text-gray-900">{customer.orderCount}</td>
                <td className="px-4 py-3 text-right text-gray-900">
                  {formatCurrency(customer.totalSpend ?? 0)}
                </td>
                <td className="px-4 py-3 text-xs text-gray-400">
                  {customer.lastOrderAt ? formatDate(customer.lastOrderAt) : 'Never ordered'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <span className="text-gray-500">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 disabled:opacity-40 hover:bg-gray-50"
            >
              Previous
            </button>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={page + 1 >= totalPages}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 disabled:opacity-40 hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
