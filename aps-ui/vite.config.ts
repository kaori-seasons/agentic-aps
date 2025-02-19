import { defineConfig } from 'vite'
import { resolve } from "path"
import vue from '@vitejs/plugin-vue'
import viteConfitExtend from './vite.config.extend'

const url = 'http://localhost:31010'
const path = "jvs-aps-ui" // fixme 请修改为自己的前端项目名称

export default defineConfig({
  define: {
    'process.env': process.env
  },
  plugins: [
    vue(),
    viteConfitExtend({
      indexPath: `${path}/index.html`,// 相对于outDir的路径
    })
  ],
  server:{
    hmr: true,
    host: '0.0.0.0',
    open: true,
    port: 9998,
    proxy: {
      '/mgr': {
        target: url,
        rewrite: (path: any) => path.replace(/^\/mgr/,'/mgr')
      },
      '/jvs-public': {
        target: url,
        changeOrigin: true,
        rewrite: (path: any) => path.replace(/^\/jvs-public/, '/jvs-public')
      },
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, "./src"),
      'vue-i18n': 'vue-i18n/dist/vue-i18n.cjs.js'
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        api:'modern-compiler',
        additionalData: '@use "@/styles/mixin.scss" as *;'
      },
    }
  },
  build: {
    chunkSizeWarningLimit: 1500,
    emptyOutDir: true,
    outDir: 'docker/dist',
    assetsDir: path + '/static',
    rollupOptions: {
      output: {
        // 分包
        manualChunks (id:any) {
          if(id.includes("node_modules")) {
            return id.toString().split("node_modules/")[1].split("/")[0].toString();
          }
        },
      },
    },
  }
})
