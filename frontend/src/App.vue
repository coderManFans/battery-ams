<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
type Row = Record<string, any>
type ModuleKey = 'overview'|'assets'|'warehouse'|'workorders'|'preservation'|'telemetry'
const active = ref<ModuleKey>('overview')
const loading = ref(false)
const error = ref('')
const assets = ref<Row[]>([])
const warehouses = ref<Row[]>([])
const inventory = ref<Row[]>([])
const workorders = ref<Row[]>([])
const risks = ref<Row[]>([])
const alarms = ref<Row[]>([])
const readings = ref<Row[]>([])
const menu: {key:ModuleKey,label:string,endpoint:string}[] = [
 {key:'overview',label:'运营总览',endpoint:'多业务服务汇总'},
 {key:'assets',label:'电池资产',endpoint:'Asset Service · 8081'},
 {key:'warehouse',label:'仓储管理',endpoint:'Warehouse Service · 8082'},
 {key:'workorders',label:'工单中心',endpoint:'Workorder Service · 8083'},
 {key:'preservation',label:'资产保全',endpoint:'Preservation Service · 8084'},
 {key:'telemetry',label:'设备监控',endpoint:'Telemetry Service · 8085'}
]
const current = computed(()=>menu.find(x=>x.key===active.value)!)
const apiMap:Record<string,string>={assets:'/api/v1/assets',warehouses:'/warehouse-api/v1/warehouses',inventory:'/warehouse-api/v1/warehouses/inventory',workorders:'/workorder-api/v1/work-orders',risks:'/preservation-api/v1/risk-events',alarms:'/telemetry-api/v1/alarms',readings:'/telemetry-api/v1/readings/latest'}
async function get(path:string){const r=await fetch(path);if(!r.ok)throw new Error(path+' 请求失败：HTTP '+r.status);return (await r.json()).data??[]}
async function loadAll(){
 loading.value=true;error.value=''
 try{
  const results=await Promise.allSettled([get(apiMap.assets+'?size=200'),get(apiMap.warehouses),get(apiMap.inventory),get(apiMap.workorders+'?size=200'),get(apiMap.risks+'?size=200'),get(apiMap.alarms+'?size=200'),get(apiMap.readings)])
  const setters=[assets,warehouses,inventory,workorders,risks,alarms,readings]
  const failures:string[]=[]
  results.forEach((r,i)=>{if(r.status==='fulfilled')setters[i].value=r.value;else failures.push(menu[i+1]?.label+' 服务暂不可用')})
  if(failures.length)error.value=failures.join('；')+'。请检查对应服务是否启动。'
 } finally {loading.value=false}
}
function activate(key:ModuleKey){active.value=key;loadAll()}
const counts=computed(()=>({assets:assets.value.length,available:assets.value.filter(x=>x.status==='AVAILABLE').length,warehouses:warehouses.value.length,inventory:inventory.value.length,workorders:workorders.value.length,openRisks:risks.value.filter(x=>['OPEN','FROZEN'].includes(x.status)).length,alarms:alarms.value.filter(x=>x.status!=='RESOLVED').length}))
const statusLabel:Record<string,string>={REGISTERED:'已建档',AVAILABLE:'可用',IN_SERVICE:'运营中',FROZEN:'已冻结',UNDER_REPAIR:'维修中',RETIRED:'已退役',PENDING:'待分派',ASSIGNED:'已分派',ACCEPTED:'已接单',IN_PROGRESS:'处理中',WAITING_REVIEW:'待审核',COMPLETED:'已完成',OPEN:'待处理',ACKNOWLEDGED:'已确认',RESOLVED:'已关闭'}
onMounted(loadAll)
</script>
<template>
<el-container class="shell">
 <el-aside width="224px" class="side">
  <div class="brand"><b>⚡</b> Battery AMS</div><div class="section">资产运营平台</div>
  <div v-for="m in menu" :key="m.key" class="nav" :class="{active:active===m.key}" @click="activate(m.key)">{{m.label}}</div>
  <div class="foot">BATTERY ASSET PLATFORM<br/>DEMO ENVIRONMENT</div>
 </el-aside>
 <el-container>
  <el-header class="top"><span>新能源换电运营平台</span><div><el-tag type="success">开发演示环境</el-tag><el-button text @click="loadAll" :loading="loading">刷新</el-button></div></el-header>
  <el-main class="main">
   <div class="heading"><div><h1>{{current.label}}</h1><p>{{current.endpoint}}</p></div><el-button type="primary" :loading="loading" @click="loadAll">刷新数据</el-button></div>
   <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" style="margin-bottom:16px"/>
   <template v-if="active==='overview'">
    <div class="kpis">
     <div class="kpi"><span>电池资产</span><strong>{{counts.assets}}</strong><small>可用 {{counts.available}}</small></div>
     <div class="kpi"><span>仓库数量</span><strong>{{counts.warehouses}}</strong><small>库存记录 {{counts.inventory}}</small></div>
     <div class="kpi"><span>工单数量</span><strong>{{counts.workorders}}</strong><small>全部业务工单</small></div>
     <div class="kpi"><span>待处理风险/告警</span><strong class="warn">{{counts.openRisks+counts.alarms}}</strong><small>风险 {{counts.openRisks}} · 告警 {{counts.alarms}}</small></div>
    </div>
    <section class="panel"><div class="panel-head"><div><h2>业务服务状态</h2><p>通过各服务业务接口读取数据；接口可用不等同于生产健康检查</p></div></div>
     <el-table :data="menu.slice(1)" stripe><el-table-column prop="label" label="业务模块"/><el-table-column prop="endpoint" label="服务入口"/><el-table-column label="记录数"><template #default="{row}">{{({assets:assets.length,warehouse:inventory.length,workorders:workorders.length,preservation:risks.length,telemetry:alarms.length} as any)[row.key]??'—'}}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button link type="primary" @click="activate(row.key)">进入模块</el-button></template></el-table-column></el-table>
    </section>
   </template>
   <template v-else-if="active==='assets'">
    <div class="kpis"><div class="kpi"><span>资产总量</span><strong>{{assets.length}}</strong></div><div class="kpi"><span>可用</span><strong>{{counts.available}}</strong></div><div class="kpi"><span>运营中</span><strong>{{assets.filter(x=>x.status==='IN_SERVICE').length}}</strong></div><div class="kpi"><span>冻结/维修</span><strong class="warn">{{assets.filter(x=>['FROZEN','UNDER_REPAIR'].includes(x.status)).length}}</strong></div></div>
    <section class="panel"><div class="panel-head"><div><h2>电池资产档案</h2><p>Asset Service · MySQL</p></div></div><el-table :data="assets" v-loading="loading" stripe><el-table-column prop="batteryCode" label="电池编号" min-width="150"/><el-table-column prop="modelCode" label="型号"/><el-table-column prop="soc" label="SOC"/><el-table-column prop="soh" label="SOH"/><el-table-column label="状态"><template #default="{row}"><el-tag>{{statusLabel[row.status]||row.status}}</el-tag></template></el-table-column><el-table-column label="位置"><template #default="{row}">{{row.locationType||'未分配'}} / {{row.locationId??'-'}}</template></el-table-column></el-table></section>
   </template>
   <template v-else-if="active==='warehouse'">
    <div class="kpis"><div class="kpi"><span>仓库</span><strong>{{warehouses.length}}</strong></div><div class="kpi"><span>库存记录</span><strong>{{inventory.length}}</strong></div></div>
    <section class="panel"><div class="panel-head"><div><h2>仓库列表</h2><p>Warehouse Service</p></div></div><el-table :data="warehouses" stripe><el-table-column prop="warehouseCode" label="仓库编码"/><el-table-column prop="name" label="仓库名称"/><el-table-column prop="status" label="状态"/></el-table></section>
    <section class="panel" style="margin-top:16px"><div class="panel-head"><div><h2>库存明细</h2><p>最多展示最近 500 条</p></div></div><el-table :data="inventory" stripe><el-table-column prop="batteryCode" label="电池编号"/><el-table-column prop="modelCode" label="型号"/><el-table-column prop="warehouseCode" label="仓库"/><el-table-column prop="locationCode" label="库位"/><el-table-column prop="status" label="库存状态"/></el-table></section>
   </template>
   <template v-else-if="active==='workorders'">
    <section class="panel"><div class="panel-head"><div><h2>工单列表</h2><p>Workorder Service</p></div></div><el-table :data="workorders" v-loading="loading" stripe><el-table-column prop="workOrderNo" label="工单编号" min-width="180"/><el-table-column prop="workOrderType" label="类型"/><el-table-column prop="batteryCode" label="电池编号"/><el-table-column prop="title" label="标题" min-width="180"/><el-table-column prop="priority" label="优先级"/><el-table-column label="状态"><template #default="{row}"><el-tag>{{statusLabel[row.status]||row.status}}</el-tag></template></el-table-column></el-table></section>
   </template>
   <template v-else-if="active==='preservation'">
    <section class="panel"><div class="panel-head"><div><h2>风险事件</h2><p>Preservation Service</p></div></div><el-table :data="risks" v-loading="loading" stripe><el-table-column prop="riskNo" label="风险编号"/><el-table-column prop="batteryCode" label="电池编号"/><el-table-column prop="riskType" label="风险类型"/><el-table-column prop="severity" label="等级"/><el-table-column prop="description" label="描述" min-width="200"/><el-table-column label="状态"><template #default="{row}"><el-tag>{{statusLabel[row.status]||row.status}}</el-tag></template></el-table-column></el-table></section>
   </template>
   <template v-else-if="active==='telemetry'">
    <section class="panel"><div class="panel-head"><div><h2>最新遥测</h2><p>Telemetry Service · BMS 模拟数据</p></div></div><el-table :data="readings" stripe><el-table-column prop="batteryCode" label="电池编号"/><el-table-column prop="soc" label="SOC"/><el-table-column prop="soh" label="SOH"/><el-table-column prop="voltage" label="电压"/><el-table-column prop="currentAmp" label="电流"/><el-table-column prop="temperatureC" label="温度"/><el-table-column prop="reportedAt" label="采集时间" min-width="170"/></el-table></section>
    <section class="panel" style="margin-top:16px"><div class="panel-head"><div><h2>BMS 告警</h2><p>告警列表</p></div></div><el-table :data="alarms" stripe><el-table-column prop="alarmNo" label="告警编号"/><el-table-column prop="batteryCode" label="电池编号"/><el-table-column prop="alarmType" label="类型"/><el-table-column prop="severity" label="等级"/><el-table-column prop="description" label="描述" min-width="200"/><el-table-column label="状态"><template #default="{row}"><el-tag>{{statusLabel[row.status]||row.status}}</el-tag></template></el-table-column></el-table></section>
   </template>
   <p class="disclaimer">演示环境使用模拟或测试数据。页面只展示已接入的查询接口，未实现的写操作不会伪装成已可用功能。</p>
  </el-main>
 </el-container>
</el-container>
</template>
