import fs from 'node:fs'
import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const pfxPath = path.resolve(__dirname, 'certs', 'wms-local-dev.pfx')
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
  },
})
