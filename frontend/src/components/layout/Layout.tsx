import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import CartDrawer from '@/components/cart/CartDrawer'

export default function Layout() {
  const [cartOpen, setCartOpen] = useState(false)

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar onCartOpen={() => setCartOpen(true)} />
      <main className="flex-1 overflow-y-auto p-8">
        <Outlet />
      </main>
      <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} />
    </div>
  )
}
