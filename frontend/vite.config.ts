import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    proxy: { '/api': { target: process.env.VITE_API_PROXY || 'http://localhost:8080', changeOrigin: true } },
    // Windows 上编辑器/工具做原子写时会生成临时目录，chokidar 监视到会抛 EBUSY 并让 dev server 退出
    watch: { ignored: ['**/.*.tmpdir/**', '**/*.tmp', '**/*.swp'] }
  }
})
