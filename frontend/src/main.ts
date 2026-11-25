/**
 * 应用入口：创建 Vue 实例并依次装配 Pinia（登录态）、Router（含全局守卫）、Element Plus。
 * 样式顺序固定为组件库在前、业务 main.css 在后，保证业务变量能覆盖默认主题。
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 全量引入：当前项目未做按需构建，换取零配置的稳定首屏
import ElementPlus from 'element-plus'
// 中文语言包：只影响日期、分页等内置文案，业务字典仍在各页面的 name 映射里
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// 业务全局样式与 CSS 变量，必须排在组件库样式之后才能覆盖默认值
import './assets/main.css'
// 根组件只承载 router-view，页面布局与菜单在 layouts/AppLayout.vue
import App from './App.vue'
// 路由实例内含全局守卫，必须先于 mount 注册，否则首屏不会触发鉴权
import router from './router'

// 链式注册后一次性挂载；ElementPlus 传 zhCn 让内置文案走中文
createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
