import fs from 'node:fs'
import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const pfxPath = path.resolve(__dirname, 'certs', 'wms-local-dev.pfx')
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8080'
const httpsConfig = fs.existsSync(pfxPath)
  ? {
      pfx: fs.readFileSync(pfxPath),
      passphrase: 'wms-dev-cert',
    }
  : undefined

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    https: httpsConfig,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
