import { NavLink, useNavigate } from 'react-router-dom'
import { ShoppingBag, Warehouse, ClipboardList, LogOut, ShoppingCart } from 'lucide-react'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'

const adminNav = [
  { to: '/inventory', label: 'Inventory', Icon: Warehouse },
]

const sharedNav = [
  { to: '/products', label: 'Products', Icon: ShoppingBag },
  { to: '/orders', label: 'Orders', Icon: ClipboardList },
]

interface Props {
  onCartOpen: () => void
}

export default function Sidebar({ onCartOpen }: Props) {
  const { username, role, logout } = useAuthStore()
  const cartCount = useCartStore((s) => s.count())
  const isAdmin = role === 'ADMIN'
  const nav = isAdmin ? [sharedNav[0], ...adminNav, sharedNav[1]] : sharedNav
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <aside className="flex h-screen w-56 flex-col border-r border-gray-200 bg-white">
      <div className="border-b border-gray-200 px-5 py-4">
        <span className="text-sm font-semibold tracking-tight text-gray-900">ScalableRest</span>
        <span className="ml-2 text-xs text-gray-400">console</span>
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

        {!isAdmin && (
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
        <div className="mb-2">
          <p className="text-xs font-medium text-gray-900">{username}</p>
          <p className="text-xs text-gray-400">{role}</p>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 text-xs text-gray-500 hover:text-gray-900 transition-colors"
        >
          <LogOut size={14} />
          Sign out
        </button>
      </div>
    </aside>
  )
}
