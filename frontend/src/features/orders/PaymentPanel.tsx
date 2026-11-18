import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import toast from 'react-hot-toast'
import { CreditCard } from 'lucide-react'
import { payForOrder } from '@/api/orders'
import { formatCurrency } from '@/lib/format'

// Test payment methods. In production the browser would collect the card with the
// provider's own widget (Stripe Elements, for example) and get back a token like
// these; card numbers never reach Mobi's servers. The tokens below are understood by
// both the simulated provider and Stripe's test mode.
const testCards = [
  { token: 'tok_visa', label: 'Visa •••• 4242', hint: 'Succeeds' },
  { token: 'tok_mastercard', label: 'Mastercard •••• 4444', hint: 'Succeeds' },
  { token: 'tok_chargeDeclined', label: 'Visa •••• 0002', hint: 'Declined' },
  { token: 'tok_chargeDeclinedInsufficientFunds', label: 'Visa •••• 9995', hint: 'Insufficient funds' },
  { token: 'tok_providerError', label: 'Provider outage', hint: 'Payment service unavailable' },
]

interface Props {
  orderId: number
  total: number
}

export default function PaymentPanel({ orderId, total }: Props) {
  const qc = useQueryClient()
  const [token, setToken] = useState(testCards[0].token)
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => payForOrder(orderId, token),
    onMutate: () => setError(null),
    onSuccess: () => {
      toast.success('Payment received. Order confirmed.')
      qc.invalidateQueries({ queryKey: ['orders'] })
      qc.invalidateQueries({ queryKey: ['payments', orderId] })
    },
    onError: (err) => {
      const status = isAxiosError(err) ? err.response?.status : undefined
      const message = isAxiosError(err) ? (err.response?.data as { message?: string } | undefined)?.message : undefined
      if (status === 402) setError(message ?? 'Your card was declined. Try another card.')
      else if (status === 503) setError('Payments are temporarily unavailable. Your items stay reserved; try again shortly.')
      else if (status === 429) setError('Too many attempts. Wait a moment and try again.')
      else setError(message ?? 'Payment failed. Try again.')
      qc.invalidateQueries({ queryKey: ['orders', orderId] })
      qc.invalidateQueries({ queryKey: ['payments', orderId] })
    },
  })

  return (
    <div className="mb-6 rounded-lg border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center gap-2">
        <CreditCard size={16} className="text-gray-500" />
        <h2 className="text-sm font-semibold text-gray-900">Payment</h2>
      </div>
      <p className="mb-3 text-xs text-gray-500">
        Your items are reserved. Pay {formatCurrency(total)} to confirm the order. Test cards only; no real charge is made.
      </p>
      <fieldset className="mb-4 space-y-2" disabled={mutation.isPending}>
        <legend className="sr-only">Payment method</legend>
        {testCards.map((card) => (
          <label
            key={card.token}
            className={`flex cursor-pointer items-center justify-between rounded-md border px-3 py-2 text-sm transition-colors ${
              token === card.token ? 'border-gray-900 bg-gray-50' : 'border-gray-200 hover:border-gray-300'
            }`}
          >
            <span className="flex items-center gap-2">
              <input
                type="radio"
                name="payment-method"
                value={card.token}
                checked={token === card.token}
                onChange={() => setToken(card.token)}
                className="accent-gray-900"
              />
              <span className="text-gray-900">{card.label}</span>
            </span>
            <span className="text-xs text-gray-400">{card.hint}</span>
          </label>
        ))}
      </fieldset>
      {error && (
        <div role="alert" className="mb-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">
          {error}
        </div>
      )}
      <button
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="w-full rounded-md bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-gray-700 disabled:opacity-50 transition-colors"
      >
        {mutation.isPending ? 'Processing…' : `Pay ${formatCurrency(total)}`}
      </button>
    </div>
  )
}
