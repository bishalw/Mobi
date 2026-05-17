import { X, Minus, Plus, Trash2, ShoppingCart } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useCartStore } from '@/stores/cartStore'
import { useAuthStore } from '@/stores/authStore'
import { createOrder } from '@/api/orders'
import { formatCurrency } from '@/lib/format'

interface Props {
  open: boolean
  onClose: () => void
}

export default function CartDrawer({ open, onClose }: Props) {
  const { items, remove, updateQuantity, clear, total, count } = useCartStore()
  const userId = useAuthStore((s) => s.userId)
  const navigate = useNavigate()

  const checkout = useMutation({
    mutationFn: () =>
      createOrder(
        { userId: userId!, items: items.map((i) => ({ productId: i.productId, quantity: i.quantity })) },
        crypto.randomUUID()
      ),
    onSuccess: (order) => {
      clear()
      onClose()
      navigate(`/orders/${order.id}`)
    },
  })

  if (!open) return null

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/20" onClick={onClose} />

      <div className="fixed right-0 top-0 z-50 flex h-full w-96 flex-col bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
          <div className="flex items-center gap-2">
            <ShoppingCart size={16} className="text-gray-700" />
            <span className="text-sm font-semibold text-gray-900">Cart</span>
            {count() > 0 && (
              <span className="rounded-full bg-gray-900 px-2 py-0.5 text-xs font-medium text-white">
                {count()}
              </span>
            )}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-700 transition-colors">
            <X size={16} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {items.length === 0 ? (
            <div className="flex h-full items-center justify-center">
              <p className="text-sm text-gray-400">Your cart is empty.</p>
            </div>
          ) : (
            <ul className="space-y-4">
              {items.map((item) => (
                <li key={item.productId} className="flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="truncate text-sm font-medium text-gray-900">{item.name}</p>
                    <p className="text-xs text-gray-500">{formatCurrency(item.price)} each</p>
                  </div>

                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => updateQuantity(item.productId, item.quantity - 1)}
                      className="flex h-6 w-6 items-center justify-center rounded border border-gray-200 text-gray-500 hover:border-gray-400 transition-colors"
                    >
                      <Minus size={12} />
                    </button>
                    <span className="w-6 text-center text-sm font-medium">{item.quantity}</span>
                    <button
                      onClick={() => updateQuantity(item.productId, item.quantity + 1)}
                      className="flex h-6 w-6 items-center justify-center rounded border border-gray-200 text-gray-500 hover:border-gray-400 transition-colors"
                    >
                      <Plus size={12} />
                    </button>
                  </div>

                  <div className="text-right">
                    <p className="text-sm font-medium text-gray-900">
                      {formatCurrency(item.price * item.quantity)}
                    </p>
                    <button
                      onClick={() => remove(item.productId)}
                      className="mt-0.5 text-gray-300 hover:text-red-500 transition-colors"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {items.length > 0 && (
          <div className="border-t border-gray-200 px-5 py-4 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Total</span>
              <span className="text-base font-semibold text-gray-900">{formatCurrency(total())}</span>
            </div>

            {checkout.isError && (
              <p className="text-xs text-red-500">Checkout failed. Some items may be out of stock.</p>
            )}

            <button
              onClick={() => checkout.mutate()}
              disabled={checkout.isPending}
              className="w-full rounded-md bg-gray-900 py-2.5 text-sm font-medium text-white hover:bg-gray-700 disabled:opacity-50 transition-colors"
            >
              {checkout.isPending ? 'Placing order…' : 'Place order'}
            </button>
          </div>
        )}
      </div>
    </>
  )
}
