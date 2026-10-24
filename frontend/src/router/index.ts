import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path:'/login', component:()=>import('@/views/auth/LoginView.vue'), meta:{ public:true, title:'登录' } },
  { path:'/', component:()=>import('@/layouts/AppLayout.vue'), redirect:'/dashboard', children:[
    { path:'dashboard', component:()=>import('@/views/dashboard/DashboardView.vue'), meta:{title:'经营看板',permission:'dashboard:view'} },
    { path:'materials', component:()=>import('@/views/masterdata/MaterialsView.vue'), meta:{title:'物资主数据',permission:'masterdata:view'} },
    { path:'suppliers', component:()=>import('@/views/supplier/SuppliersView.vue'), meta:{title:'供应商管理',permission:'supplier:view'} },
    { path:'requisitions', component:()=>import('@/views/requisition/RequisitionsView.vue'), meta:{title:'采购申请',permission:'requisition:view'} },
    { path:'workflow', component:()=>import('@/views/workflow/TasksView.vue'), meta:{title:'审批中心',permission:'workflow:approve'} },
    { path:'orders', component:()=>import('@/views/purchaseorder/OrdersView.vue'), meta:{title:'采购订单',permission:'purchaseorder:view'} },
    { path:'inventory', component:()=>import('@/views/inventory/InventoryView.vue'), meta:{title:'收货与库存',permission:'inventory:view'} },
    { path:'assistant', component:()=>import('@/views/agent/AssistantView.vue'), meta:{title:'AI采购助手',permission:'agent:use'} },
    { path:'system', component:()=>import('@/views/system/SystemView.vue'), meta:{title:'系统与审计',permission:['system:user:view','audit:view']} }
  ]},
  { path:'/:pathMatch(.*)*', redirect:'/' }
]
const router=createRouter({history:createWebHistory(),routes})
router.beforeEach(async to=>{const auth=useAuthStore();if(!to.meta.public&&!auth.loggedIn)return{path:'/login',query:{redirect:to.fullPath}};if(!to.meta.public&&localStorage.getItem('pm_access_token')&&!auth.user)try{await auth.loadMe()}catch{return'/login'};const permission=to.meta.permission as string|string[]|undefined;if(permission&&!(Array.isArray(permission)?permission.some(p=>auth.can(p)):auth.can(permission)))return'/dashboard';document.title=`${to.meta.title||''} - ProcureMind`;return true})
export default router
