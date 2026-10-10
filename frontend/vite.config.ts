import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // The API runs on 8080. Proxying it here means the browser sees one origin,
    // so there is no CORS configuration to add on the Spring side during development.
    // By number, not "localhost": the API listens on this address only, and "localhost"
    // can mean the IPv6 one first.
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
})
