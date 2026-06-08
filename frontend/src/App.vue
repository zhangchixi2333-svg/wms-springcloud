<script setup lang="ts">
/**
 * 根组件脚本部分
 * 使用Vue3的<script setup>语法，导入并使用应用外壳组件AppShell
 */
// 导入应用外壳组件，负责整个应用的布局和路由管理
import AppShell from './app/AppShell.vue'
</script>

<template>
  <!-- 渲染应用外壳组件作为根组件 -->
  <AppShell />
</template>