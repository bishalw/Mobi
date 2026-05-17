import { ShoppingCart, Check } from 'lucide-react'
import { useCartStore } from '@/stores/cartStore'
import { formatCurrency } from '@/lib/format'
import type { ProductResponse } from '@/types'

interface Props {
  products: ProductResponse[]
}

export default function ProductGrid({ products }: Props) {
  const { add, items } = useCartStore()

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {products.map((p) => {
        const inCart = items.some((i) => i.productId === p.id)

        return (
          <div
            key={p.id}
            className={`flex flex-col rounded-xl border bg-white p-5 transition-shadow hover:shadow-md ${
              p.active ? 'border-gray-200' : 'border-gray-100 opacity-60'
            }`}
          >
            <div className="mb-3 flex items-start justify-between gap-2">
              <h3 className="text-sm font-semibold leading-snug text-gray-900">{p.name}</h3>
              {!p.active && (
                <span className="shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-400">
                  Inactive
                </span>
              )}
            </div>

            {p.description && (
              <p className="mb-4 line-clamp-2 text-xs leading-relaxed text-gray-400">
                {p.description}
              </p>
            )}

            <div className="mt-auto flex items-center justify-between pt-3 border-t border-gray-100">
              <span className="text-base font-semibold text-gray-900">
                {formatCurrency(p.price)}
              </span>

              {p.active && (
                <button
                  onClick={() => add({ productId: p.id, name: p.name, price: p.price })}
                  className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                    inCart
                      ? 'bg-green-50 text-green-700 hover:bg-green-100'
                      : 'bg-gray-900 text-white hover:bg-gray-700'
                  }`}
                >
                  {inCart ? (
                    <>
                      <Check size={12} /> In cart
                    </>
                  ) : (
                    <>
                      <ShoppingCart size={12} /> Add to cart
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
