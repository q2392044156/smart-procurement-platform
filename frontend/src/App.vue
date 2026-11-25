<!--
  应用根组件：只放路由出口；侧边导航与顶栏在 layouts/AppLayout.vue，
  登录态与 meta.permission 的判定在 router/index.ts 的全局守卫里，故此处无逻辑。
-->
<template><router-view /></template>
