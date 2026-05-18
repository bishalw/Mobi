import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import toast from 'react-hot-toast'
import { createProduct, updateProduct } from '@/api/products'
import type { ProductResponse } from '@/types'

const schema = z.object({
  sku: z.string().min(3).max(64),
  name: z.string().min(3).max(50),
  description: z.string().max(1000).optional(),
  price: z.number({ coerce: true }).positive(),
  active: z.boolean(),
})

type FormValues = z.infer<typeof schema>

interface Props {
  product: ProductResponse | null
  onClose: () => void
}

export default function ProductForm({ product, onClose }: Props) {
  const qc = useQueryClient()

  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: product
      ? { ...product, price: Number(product.price) }
      : { active: true },
  })

  const mutation = useMutation({
    mutationFn: (data: FormValues) =>
      product ? updateProduct(product.id, data) : createProduct(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] })
      toast.success(product ? 'Product updated' : 'Product created')
      onClose()
    },
    onError: () => toast.error('Failed to save product'),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="w-full max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-lg">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900">
            {product ? 'Edit product' : 'New product'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-700">
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-3">
          {(['sku', 'name', 'description'] as const).map((field) => (
            <div key={field}>
              <label className="mb-1 block text-xs font-medium capitalize text-gray-700">{field}</label>
              <input
                {...register(field)}
                className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-gray-500 focus:ring-1 focus:ring-gray-500"
              />
              {errors[field] && <p className="mt-1 text-xs text-red-500">{errors[field]?.message}</p>}
            </div>
          ))}

          <div>
            <label className="mb-1 block text-xs font-medium text-gray-700">Price</label>
            <input
              {...register('price')}
              type="number"
              step="0.01"
              className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-gray-500 focus:ring-1 focus:ring-gray-500"
            />
            {errors.price && <p className="mt-1 text-xs text-red-500">{errors.price.message}</p>}
          </div>

          <label className="flex items-center gap-2 text-xs text-gray-700">
            <input {...register('active')} type="checkbox" className="rounded" />
            Active
          </label>

          {mutation.isError && (
            <p className="text-xs text-red-500">Save failed. Check the values and try again.</p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded px-3 py-1.5 text-xs text-gray-500 hover:text-gray-700"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="rounded bg-gray-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-gray-700 disabled:opacity-50 transition-colors"
            >
              {mutation.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
