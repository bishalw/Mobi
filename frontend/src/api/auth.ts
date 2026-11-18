import type { AxiosError } from 'axios'
import client, { refreshAccessToken } from './client'
import type { LoginRequest, AuthResponse, RegisterRequest, RegisterResponse } from '@/types'

export const login = (data: LoginRequest) =>
  client.post<AuthResponse>('/auth/login', data).then((r) => r.data)

export const register = (data: RegisterRequest) =>
  client.post<RegisterResponse>('/auth/register', data).then((r) => r.data)

// Signs the user out on every device: revokes all their refresh tokens server-side.
// /auth/ calls skip the client's automatic refresh-and-retry, so an expired access
// token is refreshed here explicitly, once.
export const logoutAllSessions = async () => {
  try {
    await client.post('/auth/logout-all')
  } catch (err) {
    if ((err as AxiosError).response?.status !== 401) throw err
    const token = await refreshAccessToken()
    if (!token) return // Already signed out everywhere that matters.
    await client.post('/auth/logout-all', null, { headers: { Authorization: `Bearer ${token}` } })
  }
}
