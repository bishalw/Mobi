import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/productapi': 'http://localhost:8080',
      '/inventoryapi': 'http://localhost:8080',
      '/orderapi': 'http://localhost:8080',
    },
  },
})
