import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由表：登录页 meta.public 免鉴权；其余页面挂在 AppLayout 下并声明 meta.permission。
 * permission 支持单个权限码或数组（数组表示任一满足即可），由全局守卫统一判定。
 * meta.title 同时用于浏览器标题（后缀 ProcureMind）与无权时的跳转回退。
 */
const routes = [
  { path:'/login', component:()=>import('@/views/auth/LoginView.vue'), meta:{ public:true, title:'登录' } },
  { path:'/', component:()=>import('@/layouts/AppLayout.vue'), redirect:'/dashboard', children:[
    { path:'dashboard', component:()=>import('@/views/dashboard/DashboardView.vue'), meta:{title:'经营看板',permission:'dashboard:view'} },
    { path:'materials', component:()=>import('@/views/masterdata/MaterialsView.vue'), meta:{title:'物资主数据',permission:'masterdata:view'} },
    { path:'base-data', component:()=>import('@/views/masterdata/BaseDataView.vue'), meta:{title:'基础资料',permission:'masterdata:view'} },
    { path:'suppliers', component:()=>import('@/views/supplier/SuppliersView.vue'), meta:{title:'供应商管理',permission:'supplier:view'} },
    { path:'requisitions', component:()=>import('@/views/requisition/RequisitionsView.vue'), meta:{title:'采购申请',permission:'requisition:view'} },
    { path:'workflow', component:()=>import('@/views/workflow/TasksView.vue'), meta:{title:'审批中心',permission:'workflow:approve'} },
    { path:'orders', component:()=>import('@/views/purchaseorder/OrdersView.vue'), meta:{title:'采购订单',permission:'purchaseorder:view'} },
    { path:'inventory', component:()=>import('@/views/inventory/InventoryView.vue'), meta:{title:'收货与库存',permission:'inventory:view'} },
    { path:'budgets', component:()=>import('@/views/budget/BudgetView.vue'), meta:{title:'部门预算',permission:'budget:view'} },
    { path:'contracts', component:()=>import('@/views/contract/ContractView.vue'), meta:{title:'合同管理',permission:'contract:view'} },
    { path:'knowledge', component:()=>import('@/views/knowledge/KnowledgeView.vue'), meta:{title:'知识库',permission:'kb:view'} },
    { path:'assistant', component:()=>import('@/views/agent/AssistantView.vue'), meta:{title:'AI采购助手',permission:'agent:use'} },
    { path:'system', component:()=>import('@/views/system/SystemView.vue'), meta:{title:'系统与审计',permission:['system:user:view','audit:view']} }
  ]},
  { path:'/:pathMatch(.*)*', redirect:'/' }
]
// createWebHistory：去掉 hash，刷新任意子路径由后端 history fallback 回 index.html
const router=createRouter({history:createWebHistory(),routes})
/**
 * 全局前置守卫按顺序做四件事：
 * 1) 非 public 页面未登录 → 跳 /login 并带上 redirect，登录成功后可回跳；
 * 2) 有 token 但 store 无用户（整页刷新）→ 先 loadMe 补齐，失败回登录页；
 * 3) 声明了 meta.permission 时，数组取「任一满足」、单值直接判定，无权回看板；
 * 4) 最后写 document.title 并放行，返回 true。
 */
router.beforeEach(async to=>{const auth=useAuthStore();if(!to.meta.public&&!auth.loggedIn)return{path:'/login',query:{redirect:to.fullPath}};if(!to.meta.public&&localStorage.getItem('pm_access_token')&&!auth.user)try{await auth.loadMe()}catch{return'/login'};const permission=to.meta.permission as string|string[]|undefined;if(permission&&!(Array.isArray(permission)?permission.some(p=>auth.can(p)):auth.can(permission)))return'/dashboard';document.title=`${to.meta.title||''} - ProcureMind`;return true})
// 全局唯一 router 实例，main.ts 注册后由 App.vue 的 router-view 渲染
export default router
