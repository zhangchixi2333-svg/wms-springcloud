<script setup lang="ts">
import AuthPage from '../components/auth/AuthPage.vue'
import SidebarMenu from '../components/layout/SidebarMenu.vue'
import TopBar from '../components/layout/TopBar.vue'
import WorkspaceTabs from '../components/layout/WorkspaceTabs.vue'
import { useWorkspaceApp } from './useWorkspaceApp'

const {
  state,
  tabs,
  activeMenuKey,
  title,
  statusText,
  currentPage,
  actions,
  openLeaf,
  closeTab,
} = useWorkspaceApp()
</script>

<template>
  <AuthPage v-if="!state.authenticated" :actions="actions" :message="statusText" />

  <div v-else class="app-shell">
    <SidebarMenu :menus="state.menuTree" :active-menu-key="activeMenuKey" @open-leaf="openLeaf" />

    <section class="workspace">
      <TopBar :title="title" :user="state.user" :status-text="statusText" @logout="actions.logout" />
      <WorkspaceTabs :tabs="tabs" :active-menu-key="activeMenuKey" @select="activeMenuKey = $event" @close="closeTab" />
      <main class="workspace-body">
        <component :is="currentPage.component" v-bind="currentPage.props" />
      </main>
    </section>
  </div>
</template>
