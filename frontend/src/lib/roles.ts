import { ShoppingBag, Warehouse, ClipboardList, LayoutDashboard, Users, UserSearch, CreditCard, Truck, FileText, Building2, PackageCheck, type LucideIcon } from 'lucide-react'

export const ROLES = ['USER', 'ADMIN', 'WAREHOUSE', 'SUPPLIER'] as const
export type Role = (typeof ROLES)[number]

export const roleLabel: Record<Role, string> = {
  USER: 'Customer',
  ADMIN: 'Admin',
  WAREHOUSE: 'Warehouse',
  SUPPLIER: 'Supplier',
}

export interface NavItem {
  to: string
  label: string
  Icon: LucideIcon
}

// One place decides what each role sees. The router guards use the same table, so a
// page is reachable exactly when it is in that role's sidebar.
export const navByRole: Record<Role, NavItem[]> = {
  ADMIN: [
    { to: '/dashboard', label: 'Dashboard', Icon: LayoutDashboard },
    { to: '/products', label: 'Products', Icon: ShoppingBag },
    { to: '/inventory', label: 'Inventory', Icon: Warehouse },
    { to: '/purchase-orders', label: 'Purchase orders', Icon: FileText },
    { to: '/receiving', label: 'Receiving', Icon: PackageCheck },
    { to: '/suppliers', label: 'Suppliers', Icon: Building2 },
    { to: '/orders', label: 'Orders', Icon: ClipboardList },
    { to: '/payments', label: 'Payments', Icon: CreditCard },
    { to: '/users', label: 'Users', Icon: Users },
    { to: '/customers', label: 'Customers', Icon: UserSearch },
  ],
  USER: [
    { to: '/products', label: 'Products', Icon: ShoppingBag },
    { to: '/orders', label: 'Orders', Icon: ClipboardList },
  ],
  WAREHOUSE: [
    { to: '/inventory', label: 'Inventory', Icon: Warehouse },
    { to: '/receiving', label: 'Receiving', Icon: PackageCheck },
    { to: '/purchase-orders', label: 'Purchase orders', Icon: FileText },
  ],
  SUPPLIER: [{ to: '/supplier', label: 'My purchase orders', Icon: Truck }],
}

export function asRole(role: string | null | undefined): Role | null {
  return ROLES.includes(role as Role) ? (role as Role) : null
}

export function homePath(role: string | null | undefined): string {
  const r = asRole(role)
  return r ? navByRole[r][0].to : '/login'
}

export function canVisit(role: string | null | undefined, path: string): boolean {
  const r = asRole(role)
  return !!r && navByRole[r].some((item) => item.to === path)
}
