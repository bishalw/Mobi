import { NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { LogOut, ShoppingCart, MonitorX } from 'lucide-react'
import toast from 'react-hot-toast'
import { logoutAllSessions } from '@/api/auth'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { asRole, navByRole, roleLabel } from '@/lib/roles'
import DevUserSwitcher from '@/components/DevUserSwitcher'

interface Props {
  onCartOpen: () => void
}

export default function Sidebar({ onCartOpen }: Props) {
  const { username, role, logout, clearSession } = useAuthStore()
  const [signingOutEverywhere, setSigningOutEverywhere] = useState(false)
  const cartCount = useCartStore((s) => s.count())
  const currentRole = asRole(role)
  const nav = currentRole ? navByRole[currentRole] : []
  // Only customers shop.
  const showCart = currentRole === 'USER'
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  // For a lost phone or a shared computer: ends every session, not just this one.
  async function handleLogoutEverywhere() {
    if (!window.confirm('Sign out on every device, including this one?')) return
    setSigningOutEverywhere(true)
    try {
      await logoutAllSessions()
      clearSession()
      toast.success('Signed out on all devices')
      navigate('/login')
    } catch {
      toast.error('Could not sign out other devices. Try again.')
    } finally {
      setSigningOutEverywhere(false)
    }
  }

  return (
    <aside className="flex h-screen w-56 flex-col border-r border-gray-200 bg-white">
      <div className="border-b border-gray-200 px-5 py-4">
        <span className="text-sm font-semibold tracking-tight text-gray-900">Mobi</span>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-0.5">
        {nav.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors ${
                isActive
                  ? 'bg-gray-100 font-medium text-gray-900'
                  : 'text-gray-500 hover:bg-gray-50 hover:text-gray-900'
              }`
            }
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}

        {showCart && (
          <button
            onClick={onCartOpen}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-gray-500 hover:bg-gray-50 hover:text-gray-900 transition-colors"
          >
            <div className="relative">
              <ShoppingCart size={16} />
              {cartCount > 0 && (
                <span className="absolute -right-2 -top-2 flex h-4 w-4 items-center justify-center rounded-full bg-gray-900 text-[10px] font-bold text-white">
                  {cartCount}
                </span>
              )}
            </div>
            Cart
            {cartCount > 0 && (
              <span className="ml-auto text-xs font-medium text-gray-900">{cartCount}</span>
            )}
          </button>
        )}
      </nav>

      <div className="border-t border-gray-200 px-4 py-3">
        <div className="mb-3">
          <DevUserSwitcher />
        </div>
        <div className="mb-2">
          <p className="text-xs font-medium text-gray-900">{username}</p>
          <p className="text-xs text-gray-400">{currentRole ? roleLabel[currentRole] : role}</p>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 text-xs text-gray-500 hover:text-gray-900 transition-colors"
        >
          <LogOut size={14} />
          Sign out
        </button>
        <button
          onClick={handleLogoutEverywhere}
          disabled={signingOutEverywhere}
          className="mt-1.5 flex items-center gap-2 text-xs text-gray-400 hover:text-gray-900 disabled:opacity-50 transition-colors"
        >
          <MonitorX size={14} />
          {signingOutEverywhere ? 'Signing out…' : 'Sign out everywhere'}
        </button>
      </div>
    </aside>
  )
}
