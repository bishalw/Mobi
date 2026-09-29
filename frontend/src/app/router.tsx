import { createBrowserRouter, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import Layout from '@/components/layout/Layout'
import LoginPage from '@/features/auth/LoginPage'
import RegisterPage from '@/features/auth/RegisterPage'
import DashboardPage from '@/features/dashboard/DashboardPage'
import ProductsPage from '@/features/products/ProductsPage'
import InventoryPage from '@/features/inventory/InventoryPage'
import OrdersPage from '@/features/orders/OrdersPage'
import OrderDetailPage from '@/features/orders/OrderDetailPage'
import UsersPage from '@/features/users/UsersPage'
import CustomersPage from '@/features/customers/CustomersPage'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function RequireAdmin({ children }: { children: React.ReactNode }) {
  const role = useAuthStore((s) => s.role)
  return role === 'ADMIN' ? <>{children}</> : <Navigate to="/products" replace />
}

function HomeRedirect() {
  const role = useAuthStore((s) => s.role)
  return <Navigate to={role === 'ADMIN' ? '/dashboard' : '/products'} replace />
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
      { index: true, element: <HomeRedirect /> },
      { path: 'dashboard', element: <RequireAdmin><DashboardPage /></RequireAdmin> },
      { path: 'products', element: <ProductsPage /> },
      { path: 'inventory', element: <RequireAdmin><InventoryPage /></RequireAdmin> },
      { path: 'users', element: <RequireAdmin><UsersPage /></RequireAdmin> },
      { path: 'customers', element: <RequireAdmin><CustomersPage /></RequireAdmin> },
      { path: 'orders', element: <OrdersPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
    ],
  },
])
