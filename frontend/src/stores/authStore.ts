import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authToken } from '@/lib/authToken'

interface AuthState {
  userId: number | null
  username: string | null
  role: string | null
  token: string | null
  isAuthenticated: boolean
  login: (userId: number, username: string, role: string, token: string) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      username: null,
      role: null,
      token: null,
      isAuthenticated: false,
      login: (userId, username, role, token) => {
        authToken.set(token)
        set({ userId, username, role, token, isAuthenticated: true })
      },
      logout: () => {
        authToken.clear()
        set({ userId: null, username: null, role: null, token: null, isAuthenticated: false })
      },
    }),
    {
      name: 'auth',
      onRehydrateStorage: () => (state) => {
        if (state?.token) authToken.set(state.token)
      },
    }
  )
)
