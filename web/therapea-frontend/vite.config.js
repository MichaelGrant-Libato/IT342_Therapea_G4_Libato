import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  
  // Local development proxy settings (runs when using npm run dev)
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8083', 
        changeOrigin: true,
        secure: false,
      }
    }
  },

  preview: {
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'https://therapea-backend.onrender.com',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})