import axios from 'axios'
import type { AuthResponse } from '@/types'

// Plain axios, not the shared client: these must never pass through its 401-refresh
// interceptor, or a failed refresh would try to refresh itself. Kept apart from
// api/auth.ts so the client and the auth store can import them without a cycle.
export const refreshSession = () =>
  axios.post<AuthResponse>('/auth/refresh', null, { withCredentials: true }).then((r) => r.data)

export const logoutSession = () =>
  axios.post('/auth/logout', null, { withCredentials: true }).catch(() => undefined)
