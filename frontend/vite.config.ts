import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
const proxy = (target:string) => ({target, changeOrigin:true, rewrite:(path:string)=>path.replace(/^\/(asset|warehouse|workorder|preservation|telemetry)-api/,'/api')})
export default defineConfig({plugins:[vue()],server:{port:5173,proxy:{
  '/api':'http://localhost:8081',
  '/warehouse-api':proxy('http://localhost:8082'),
  '/workorder-api':proxy('http://localhost:8083'),
  '/preservation-api':proxy('http://localhost:8084'),
  '/telemetry-api':proxy('http://localhost:8085')
}}})
