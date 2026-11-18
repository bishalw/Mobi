import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '@/api/auth'
import DevUserSwitcher from '@/components/DevUserSwitcher'
import { useAuthStore } from '@/stores/authStore'

const schema = z.object({
  username: z.string().min(1, 'Required'),
  password: z.string().min(1, 'Required'),
})

type FormValues = z.infer<typeof schema>

// The server's message is shown for lockout, suspension and rate limiting, because
// each tells the user what to do next. A plain wrong password stays generic.
function loginErrorMessage(err: unknown): string {
  if (isAxiosError(err)) {
    const status = err.response?.status
    const message = (err.response?.data as { message?: string } | undefined)?.message
    if (status === 423 && message) return message
    if (status === 403) return 'This account has been suspended. Contact support.'
    if (status === 429) return 'Too many sign-in attempts from this network. Wait a minute and try again.'
  }
  return 'Invalid username or password. Please try again.'
}

export default function LoginPage() {
  const navigate = useNavigate()
  const { login: storeLogin } = useAuthStore()

  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  })

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      storeLogin(data.userId, data.username, data.role, data.token)
      navigate('/')
    },
  })

  return (
    <div className="flex min-h-screen">
      <div className="hidden lg:flex lg:w-1/2 flex-col justify-between bg-gray-950 p-12">
        <div className="text-white text-2xl font-bold tracking-tight">mobi</div>
        <div>
          <p className="text-4xl font-bold text-white leading-snug">
            The premium<br />phone store.
          </p>
          <p className="mt-4 text-gray-400 text-sm">
            Browse the latest devices, manage orders,<br />and track your inventory — all in one place.
          </p>
        </div>
        <p className="text-gray-600 text-xs">© 2025 Mobi</p>
      </div>

      <div className="flex w-full lg:w-1/2 items-center justify-center bg-white px-8 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8">
            <div className="lg:hidden text-2xl font-bold tracking-tight text-gray-900 mb-6">mobi</div>
            <h1 className="text-2xl font-bold text-gray-900">Welcome back</h1>
            <p className="mt-1 text-sm text-gray-500">Sign in to your account to continue</p>
          </div>

          <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Username</label>
              <input
                {...register('username')}
                autoComplete="username"
                className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm text-gray-900 placeholder-gray-400 outline-none transition focus:border-gray-900 focus:ring-2 focus:ring-gray-900/10"
                placeholder="Enter your username"
              />
              {errors.username && <p className="mt-1.5 text-xs text-red-500">{errors.username.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Password</label>
              <input
                {...register('password')}
                type="password"
                autoComplete="current-password"
                className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm text-gray-900 placeholder-gray-400 outline-none transition focus:border-gray-900 focus:ring-2 focus:ring-gray-900/10"
                placeholder="Enter your password"
              />
              {errors.password && <p className="mt-1.5 text-xs text-red-500">{errors.password.message}</p>}
            </div>

            {mutation.isError && (
              <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3">
                <p className="text-xs text-red-600">{loginErrorMessage(mutation.error)}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={mutation.isPending}
              className="w-full rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-gray-700 disabled:opacity-50"
            >
              {mutation.isPending ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            Don't have an account?{' '}
            <Link to="/register" className="font-medium text-gray-900 hover:underline">
              Create one
            </Link>
          </p>

          <div className="mt-6">
            <DevUserSwitcher />
          </div>
        </div>
      </div>
    </div>
  )
}
