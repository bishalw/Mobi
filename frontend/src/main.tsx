import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { queryClient } from './app/queryClient'
import { router } from './app/router'
import { refreshAccessToken } from './api/client'
import { useAuthStore } from './stores/authStore'
import './index.css'

// The access token is kept in memory only, so a reload starts without one. Trade the
// refresh cookie for a new token straight away; if the session has ended, sign out
// locally so the protected routes send the user to the login page.
if (useAuthStore.getState().isAuthenticated) {
  refreshAccessToken().then((token) => {
    if (!token) useAuthStore.getState().clearSession()
  })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>
)
