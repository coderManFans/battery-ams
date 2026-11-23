import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'

const proxy = (target:string) => ({
  target,
  changeOrigin:true,
  rewrite:(path:string)=>path.replace(/^\/(asset|warehouse|workorder|preservation|telemetry)-api/,'/api')
})

export default defineConfig({
  plugins:[vue()],
  server:{
    host:'0.0.0.0',
    port:5173,
    proxy:{
      '/api':process.env.ASSET_SERVICE_URL || 'http://localhost:8081',
      '/warehouse-api':proxy(process.env.WAREHOUSE_SERVICE_URL || 'http://localhost:8082'),
      '/workorder-api':proxy(process.env.WORKORDER_SERVICE_URL || 'http://localhost:8083'),
      '/preservation-api':proxy(process.env.PRESERVATION_SERVICE_URL || 'http://localhost:8084'),
      '/telemetry-api':proxy(process.env.TELEMETRY_SERVICE_URL || 'http://localhost:8085')
    }
  }
})
