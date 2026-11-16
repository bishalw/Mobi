import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

interface DevUser {
  username: string
  password: string
  role: string
}

// Accounts for the dev-only "Switch user" control: the staff users auth-service
// creates from the repo's .env.local, plus the customer seed-phones.sh creates.
// Only `npm run dev` gets them; a production build embeds an empty list, so no
// password ever reaches a deployed bundle.
function devUsers(mode: string): DevUser[] {
  // loadEnv reads .env and .env.local (plus the mode files) from the repo root.
  const env = loadEnv(mode, resolve(__dirname, '..'), '')
  const staff = [
    ['ADMIN', 'admin', 'adminpass'],
    ['WAREHOUSE', 'warehouse', 'warehousepass'],
    ['SUPPLIER', 'supplier', 'supplierpass'],
  ] as const
  const users: DevUser[] = staff
    .filter(([role]) => env[`${role}_BOOTSTRAP_ENABLED`] !== 'false')
    .map(([role, user, pass]) => ({
      role,
      username: env[`${role}_BOOTSTRAP_USERNAME`] || user,
      password: env[`${role}_BOOTSTRAP_PASSWORD`] || pass,
    }))
  users.push({ role: 'USER', username: 'testuser', password: 'testpass123' })
  return users
}

export default defineConfig(({ mode, command }) => {
  // Where API calls are proxied in `npm run dev`. Default: the kind cluster's Envoy
  // Gateway on http://localhost. Set VITE_API_TARGET in .env.local to point elsewhere,
  // e.g. http://localhost:8080 for an api-gateway started from the IDE.
  const target = loadEnv(mode, process.cwd(), '').VITE_API_TARGET || 'http://localhost'

  return {
    plugins: [react()],
    define: {
      __DEV_USERS__: JSON.stringify(command === 'serve' ? devUsers(mode) : []),
    },
    resolve: {
      alias: {
        '@': resolve(__dirname, './src'),
      },
    },
    server: {
      proxy: {
        '/auth': target,
        '/productapi': target,
        '/inventoryapi': target,
        '/orderapi': target,
        '/paymentapi': target,
        '/procurementapi': target,
      },
    },
  }
})
