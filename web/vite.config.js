import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const target = process.env.VITE_API_TARGET || 'http://localhost:8082'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
