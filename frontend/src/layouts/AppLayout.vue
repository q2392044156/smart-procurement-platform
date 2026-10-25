<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { DataAnalysis, Box, Collection, OfficeBuilding, Document, Stamp, ShoppingCart, Tickets, MagicStick, Setting, Fold, Expand } from '@element-plus/icons-vue'

const auth=useAuthStore(),route=useRoute(),router=useRouter(),collapsed=ref(false)
const menus=[
  ['/dashboard','经营看板','dashboard:view',DataAnalysis],['/materials','物资主数据','masterdata:view',Box],['/base-data','基础资料','masterdata:view',Collection],['/suppliers','供应商管理','supplier:view',OfficeBuilding],
  ['/requisitions','采购申请','requisition:view',Document],['/workflow','审批中心','workflow:approve',Stamp],['/orders','采购订单','purchaseorder:view',ShoppingCart],
  ['/inventory','收货与库存','inventory:view',Tickets],['/assistant','AI采购助手','agent:use',MagicStick],['/system','系统与审计',['system:user:view','audit:view'],Setting]
] as const
function allowed(permission:string|readonly string[]){return typeof permission==='string'?auth.can(permission):permission.some(p=>auth.can(p))}
const visible=computed(()=>menus.filter(m=>allowed(m[2])))
async function logout(){await auth.logout();router.replace('/login')}
</script>
<template>
  <div class="shell">
    <aside :class="['sidebar',{collapsed}]">
      <div class="brand"><div class="brand-mark">P</div><div v-if="!collapsed"><strong>ProcureMind</strong><small>智能采购平台</small></div></div>
      <el-menu :default-active="route.path" router :collapse="collapsed" :collapse-transition="false">
        <el-menu-item v-for="m in visible" :key="m[0]" :index="m[0]"><el-icon><component :is="m[3]"/></el-icon><template #title>{{m[1]}}</template></el-menu-item>
      </el-menu>
      <button class="collapse" @click="collapsed=!collapsed"><el-icon><component :is="collapsed?Expand:Fold"/></el-icon><span v-if="!collapsed">收起导航</span></button>
    </aside>
    <main :class="['main',{wide:collapsed}]">
      <header><div><span class="status-dot"></span>业务系统运行中</div><div class="user"><div class="avatar">{{auth.user?.displayName?.slice(0,1)}}</div><div><b>{{auth.user?.displayName}}</b><small>{{auth.user?.roles.join(' · ')}}</small></div><el-button text @click="logout">退出</el-button></div></header>
      <router-view/>
    </main>
  </div>
</template>
<style scoped>
.shell{min-height:100%;}.sidebar{position:fixed;inset:0 auto 0 0;width:232px;background:#0d1930;color:#fff;transition:.2s;z-index:10;display:flex;flex-direction:column}.sidebar.collapsed{width:68px}.brand{height:72px;display:flex;align-items:center;gap:11px;padding:0 17px}.brand-mark{width:36px;height:36px;border-radius:11px;background:linear-gradient(135deg,#3478ff,#43b7ff);display:grid;place-items:center;font-weight:800;font-size:20px}.brand strong,.brand small{display:block;white-space:nowrap}.brand small{font-size:11px;color:#91a1bd;margin-top:2px}.el-menu{border:0;background:transparent;--el-menu-text-color:#aab8d0;--el-menu-hover-bg-color:#152746;--el-menu-active-color:#fff}.el-menu-item.is-active{background:#0b5cff;border-radius:9px;margin:2px 9px;width:auto}.el-menu-item{margin:2px 9px;width:auto;border-radius:9px}.collapse{margin:auto 13px 16px;border:0;border-top:1px solid #24324b;background:transparent;color:#91a1bd;padding:16px 8px;display:flex;gap:12px;cursor:pointer}.main{margin-left:232px;transition:.2s;min-height:100%}.main.wide{margin-left:68px}header{height:64px;padding:0 24px;background:rgba(255,255,255,.88);backdrop-filter:blur(12px);border-bottom:1px solid #e8edf5;display:flex;align-items:center;justify-content:space-between;color:#536078;font-size:13px;position:sticky;top:0;z-index:9}.user{display:flex;align-items:center;gap:9px}.user small,.user b{display:block}.user small{color:#8a95a8;font-size:11px;margin-top:2px}.avatar{width:34px;height:34px;border-radius:50%;background:#eaf1ff;color:#0b5cff;display:grid;place-items:center;font-weight:700}
</style>
