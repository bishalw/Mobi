import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Repeat } from 'lucide-react'
import toast from 'react-hot-toast'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/authStore'
import { asRole, homePath, roleLabel } from '@/lib/roles'

declare const __DEV_USERS__: { username: string; password: string; role: string }[]

// Dev-only: one click to sign in as each demo account. The list comes from
// vite.config.ts and is empty outside `npm run dev`, so this renders nothing in a
// production build.
const users = __DEV_USERS__

export default function DevUserSwitcher({ dark = false }: { dark?: boolean }) {
  const navigate = useNavigate()
  const { username: current, isAuthenticated, logout, login: storeLogin } = useAuthStore()
  const [busy, setBusy] = useState<string | null>(null)

  if (users.length === 0) return null

  async function switchTo(username: string, password: string) {
    setBusy(username)
    try {
      // End the current session first so its refresh token is revoked, not orphaned.
      if (isAuthenticated) await logout()
      const data = await login({ username, password })
      storeLogin(data.userId, data.username, data.role, data.token)
      navigate(homePath(data.role))
    } catch {
      toast.error(`Could not sign in as ${username}`)
      navigate('/login')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className={`rounded-md border border-dashed p-2 ${dark ? 'border-gray-700' : 'border-amber-300 bg-amber-50'}`}>
      <p className={`mb-1.5 flex items-center gap-1.5 text-[11px] font-medium ${dark ? 'text-gray-400' : 'text-amber-800'}`}>
        <Repeat size={12} />
        Switch user (dev)
      </p>
      <div className="grid grid-cols-2 gap-1">
        {users.map((u) => {
          const role = asRole(u.role)
          const active = isAuthenticated && current === u.username
          return (
            <button
              key={u.username}
              type="button"
              onClick={() => switchTo(u.username, u.password)}
              disabled={busy !== null || active}
              title={`Sign in as ${u.username}`}
              className={`rounded px-2 py-1 text-[11px] transition-colors disabled:cursor-default ${
                active
                  ? 'bg-gray-900 font-medium text-white'
                  : dark
                    ? 'bg-gray-800 text-gray-200 hover:bg-gray-700 disabled:opacity-50'
                    : 'bg-white text-gray-700 hover:bg-amber-100 disabled:opacity-50'
              }`}
            >
              {busy === u.username ? '…' : role ? roleLabel[role] : u.username}
            </button>
          )
        })}
      </div>
    </div>
  )
}
