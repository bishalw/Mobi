import { createBrowserRouter, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import Layout from '@/components/layout/Layout'
import LoginPage from '@/features/auth/LoginPage'
import RegisterPage from '@/features/auth/RegisterPage'
import ProductsPage from '@/features/products/ProductsPage'
import InventoryPage from '@/features/inventory/InventoryPage'
import OrdersPage from '@/features/orders/OrdersPage'
import OrderDetailPage from '@/features/orders/OrderDetailPage'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function RequireAdmin({ children }: { children: React.ReactNode }) {
  const role = useAuthStore((s) => s.role)
  return role === 'ADMIN' ? <>{children}</> : <Navigate to="/products" replace />
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <Layout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/products" replace /> },
      { path: 'products', element: <ProductsPage /> },
      { path: 'inventory', element: <RequireAdmin><InventoryPage /></RequireAdmin> },
      { path: 'orders', element: <OrdersPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
    ],
  },
])
