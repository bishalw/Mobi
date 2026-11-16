import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { authToken } from '@/lib/authToken'
import { refreshSession } from './session'
import { useAuthStore } from '@/stores/authStore'

const client = axios.create()

client.interceptors.request.use((config) => {
  const token = authToken.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// One refresh at a time. When several requests fail together with 401 (the token
// expired while the page was idle), they all wait on the same refresh instead of each
// rotating the refresh token, which the server would treat as token reuse.
let refreshing: Promise<string | null> | null = null

export function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = refreshSession()
      .then((session) => {
        useAuthStore.getState().login(session.userId, session.username, session.role, session.token)
        return session.token
      })
      .catch(() => null)
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

client.interceptors.response.use(
  (res) => res,
  async (err: AxiosError) => {
    const original = err.config as RetriableConfig | undefined
    const isAuthCall = original?.url?.startsWith('/auth/')
    if (err.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true
      const token = await refreshAccessToken()
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return client(original)
      }
      // The session is over. Services reject any expired token, even on public
      // endpoints, so retry once without one: browsing the catalogue keeps working,
      // and anything that really needs sign-in fails again and lands on /login.
      useAuthStore.getState().clearSession()
      delete original.headers.Authorization
      return client(original).catch((retryErr: AxiosError) => {
        if (retryErr.response?.status === 401) {
          window.location.href = '/login'
        }
        return Promise.reject(retryErr)
      })
    }
    return Promise.reject(err)
  }
)

export default client
