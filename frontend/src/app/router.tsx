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
import PaymentsPage from '@/features/payments/PaymentsPage'
import PurchaseOrdersPage from '@/features/procurement/PurchaseOrdersPage'
import PurchaseOrderDetailPage from '@/features/procurement/PurchaseOrderDetailPage'
import SuppliersPage from '@/features/procurement/SuppliersPage'
import ReceivingPage from '@/features/procurement/ReceivingPage'
import ReceivePage from '@/features/procurement/ReceivePage'
import { canVisit, homePath } from '@/lib/roles'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

// A page is open to a role exactly when it is in that role's sidebar (lib/roles.ts).
function RequirePage({ path, children }: { path: string; children: React.ReactNode }) {
  const role = useAuthStore((s) => s.role)
  return canVisit(role, path) ? <>{children}</> : <Navigate to={homePath(role)} replace />
}

function HomeRedirect() {
  const role = useAuthStore((s) => s.role)
  return <Navigate to={homePath(role)} replace />
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
      { path: 'dashboard', element: <RequirePage path="/dashboard"><DashboardPage /></RequirePage> },
      { path: 'products', element: <RequirePage path="/products"><ProductsPage /></RequirePage> },
      { path: 'inventory', element: <RequirePage path="/inventory"><InventoryPage /></RequirePage> },
      { path: 'users', element: <RequirePage path="/users"><UsersPage /></RequirePage> },
      { path: 'customers', element: <RequirePage path="/customers"><CustomersPage /></RequirePage> },
      { path: 'payments', element: <RequirePage path="/payments"><PaymentsPage /></RequirePage> },
      { path: 'orders', element: <RequirePage path="/orders"><OrdersPage /></RequirePage> },
      { path: 'orders/:id', element: <RequirePage path="/orders"><OrderDetailPage /></RequirePage> },
      { path: 'purchase-orders', element: <RequirePage path="/purchase-orders"><PurchaseOrdersPage /></RequirePage> },
      { path: 'purchase-orders/:id', element: <RequirePage path="/purchase-orders"><PurchaseOrderDetailPage /></RequirePage> },
      { path: 'suppliers', element: <RequirePage path="/suppliers"><SuppliersPage /></RequirePage> },
      { path: 'receiving', element: <RequirePage path="/receiving"><ReceivingPage /></RequirePage> },
      { path: 'receiving/:id', element: <RequirePage path="/receiving"><ReceivePage /></RequirePage> },
      { path: 'supplier', element: <RequirePage path="/supplier"><PurchaseOrdersPage /></RequirePage> },
      { path: 'supplier/orders/:id', element: <RequirePage path="/supplier"><PurchaseOrderDetailPage /></RequirePage> },
    ],
  },
])
