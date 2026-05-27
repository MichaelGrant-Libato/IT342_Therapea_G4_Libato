import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8083', // This redirects /api/ calls to your docker backend
        changeOrigin: true,
        secure: false,
      }
    }
  },

  // Ensure this is clean and doesn't point to Render
  preview: {
    proxy: {
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        secure: false,
      }
    }
  }
})