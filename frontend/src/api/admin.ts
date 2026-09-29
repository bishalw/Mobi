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

export const updateUserRole = (userId: number, role: string) =>
  client
    .patch<AdminUser>(`/auth/admin/users/${userId}/role`, { role })
    .then((r) => r.data)

export const updateUserStatus = (userId: number, enabled: boolean) =>
  client
    .patch<AdminUser>(`/auth/admin/users/${userId}/status`, { enabled })
    .then((r) => r.data)

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
