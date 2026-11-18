import client from './client'
import type {
  AdminOrder,
  AdminUser,
  CustomerSummary,
  OrderStats,
  PageResponse,
} from '@/types'

export interface PageParams {
  page?: number
  size?: number
  sort?: string
  search?: string
}

// The user directory, owned by auth-service.

export const getUsers = (params: PageParams = {}) =>
  client
    .get<PageResponse<AdminUser>>('/auth/admin/users', { params })
    .then((r) => r.data)

export const getUser = (userId: number) =>
  client.get<AdminUser>(`/auth/admin/users/${userId}`).then((r) => r.data)

// supplierId is required for SUPPLIER and ignored for other roles.
export const updateUserRole = (userId: number, role: string, supplierId?: number) =>
  client
    .patch<AdminUser>(`/auth/admin/users/${userId}/role`, { role, supplierId })
    .then((r) => r.data)

export const updateUserStatus = (userId: number, enabled: boolean) =>
  client
    .patch<AdminUser>(`/auth/admin/users/${userId}/status`, { enabled })
    .then((r) => r.data)

// Several users by id in one request (max 100), for pages that only know user ids.
export const getUsersByIds = (ids: number[]) =>
  client
    .get<AdminUser[]>('/auth/admin/users/by-ids', { params: { ids: ids.join(',') } })
    .then((r) => r.data)

// Lifts a password lockout before it expires.
export const unlockUser = (userId: number) =>
  client.post<AdminUser>(`/auth/admin/users/${userId}/unlock`).then((r) => r.data)

export const replayUserSnapshots = () =>
  client.post<{ published: number }>('/auth/admin/users/replay').then((r) => r.data)

// Order-side views, owned by order-service. Customers are joined to their orders in the
// database there, so this stays one request per page no matter how many customers exist.

export const getOrderStats = () =>
  client.get<OrderStats>('/orderapi/admin/stats').then((r) => r.data)

export const getCustomers = (params: PageParams = {}) =>
  client
    .get<PageResponse<CustomerSummary>>('/orderapi/admin/customers', { params })
    .then((r) => r.data)

export const getAdminOrders = (params: PageParams = {}) =>
  client
    .get<PageResponse<AdminOrder>>('/orderapi/admin/orders', { params })
    .then((r) => r.data)
