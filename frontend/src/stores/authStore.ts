import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authToken } from '@/lib/authToken'
import { logoutSession } from '@/api/session'
import { queryClient } from '@/app/queryClient'

interface AuthState {
  userId: number | null
  username: string | null
  role: string | null
  isAuthenticated: boolean
  login: (userId: number, username: string, role: string, token: string) => void
  // Ends the session on the server (revokes the refresh token) and locally.
  logout: () => Promise<void>
  // Local only, for when the server has already said the session is gone.
  clearSession: () => void
}

const signedOut = { userId: null, username: null, role: null, isAuthenticated: false }

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      ...signedOut,
      login: (userId, username, role, token) => {
        authToken.set(token)
        // Cached responses are shaped for whoever fetched them (a supplier never sees
        // drafts, warehouse never sees costs), so a different user starts with none.
        // A silent token refresh for the same user keeps the cache.
        if (get().userId !== userId) {
          queryClient.clear()
        }
        set({ userId, username, role, isAuthenticated: true })
      },
      logout: async () => {
        authToken.clear()
        queryClient.clear()
        set(signedOut)
        await logoutSession()
      },
      clearSession: () => {
        authToken.clear()
        queryClient.clear()
        set(signedOut)
      },
    }),
    {
      name: 'auth',
      // Who is signed in is remembered across reloads; the token itself is not. The
      // first API call after a reload gets a 401 and the client refreshes silently.
      partialize: (state) => ({
        userId: state.userId,
        username: state.username,
        role: state.role,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
