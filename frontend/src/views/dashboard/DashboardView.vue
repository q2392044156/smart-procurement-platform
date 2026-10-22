<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import http from '@/api/http'
import type { Dict } from '@/types'
echarts.use([LineChart,PieChart,GridComponent,LegendComponent,TooltipComponent,CanvasRenderer])
const data=ref<Dict>({}),trend=ref<HTMLElement>(),status=ref<HTMLElement>();let charts:EChartsType[]=[]
async function load(){data.value=await http.get('/dashboard/summary');await nextTick();draw()}
function draw(){charts.forEach(c=>c.dispose());charts=[];if(trend.value){const c=echarts.init(trend.value);c.setOption({grid:{left:42,right:20,top:30,bottom:30},tooltip:{trigger:'axis'},xAxis:{type:'category',data:(data.value.monthlyTrend||[]).map((x:Dict)=>x.month)},yAxis:{type:'value'},series:[{type:'line',smooth:true,symbolSize:8,data:(data.value.monthlyTrend||[]).map((x:Dict)=>x.amount),areaStyle:{color:'rgba(11,92,255,.1)'},lineStyle:{color:'#0b5cff',width:3},itemStyle:{color:'#0b5cff'}}]});charts.push(c)}if(status.value){const c=echarts.init(status.value);c.setOption({tooltip:{trigger:'item'},legend:{bottom:0},series:[{type:'pie',radius:['48%','72%'],center:['50%','43%'],label:{show:false},data:data.value.orderStatus||[]} ]});charts.push(c)}}
function resize(){charts.forEach(c=>c.resize())}onMounted(()=>{load();window.addEventListener('resize',resize)});onBeforeUnmount(()=>window.removeEventListener('resize',resize))
const money=(v:any)=>`¥ ${Number(v||0).toLocaleString('zh-CN',{minimumFractionDigits:2})}`
</script>
<template><div class="page"><div class="page-head"><div><h1>经营看板</h1><p>采购业务、履约与库存风险的实时概览</p></div><el-button @click="load">刷新数据</el-button></div><div class="metric-grid"><div class="metric"><div class="label">我的待审批</div><div class="value accent">{{data.pendingApprovals||0}}</div></div><div class="metric"><div class="label">本年采购金额</div><div class="value">{{money(data.yearPurchaseAmount)}}</div></div><div class="metric"><div class="label">执行中订单</div><div class="value">{{data.activeOrders||0}}</div></div><div class="metric"><div class="label">低库存预警</div><div class="value" style="color:#d94b4b">{{data.lowStockCount||0}}</div></div></div><div class="charts"><div class="panel panel-pad"><h3>近12个月采购趋势</h3><div ref="trend" class="chart"></div></div><div class="panel panel-pad"><h3>订单状态分布</h3><div ref="status" class="chart"></div></div></div><div class="tables"><div class="panel panel-pad"><h3>供应商采购排名</h3><el-table :data="data.supplierRanking"><el-table-column prop="name" label="供应商"/><el-table-column prop="amount" label="采购金额"><template #default="s">{{money(s.row.amount)}}</template></el-table-column><el-table-column prop="score" label="评分" width="90"/></el-table></div><div class="panel panel-pad"><h3>低库存物资</h3><el-table :data="data.lowStockMaterials"><el-table-column prop="materialName" label="物资"/><el-table-column prop="warehouse" label="仓库"/><el-table-column prop="quantity" label="现存" width="80"/><el-table-column prop="minStock" label="下限" width="80"/></el-table></div></div></div></template>
<style scoped>.charts,.tables{display:grid;grid-template-columns:2fr 1fr;gap:16px;margin-top:16px}.tables{grid-template-columns:1fr 1fr}.panel h3{margin:0;font-size:16px}.chart{height:310px}@media(max-width:1100px){.charts,.tables{grid-template-columns:1fr}}</style>
