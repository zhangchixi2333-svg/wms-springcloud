<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MenuNode } from '../../types/app'

const props = defineProps<{
  menus: MenuNode[]
  activeMenuKey: string
}>()

const emit = defineEmits<{
  openLeaf: [menu: MenuNode]
}>()

const expandedParents = ref<string[]>([])

const rootMenus = computed(() => props.menus.filter((item) => item.menuType === 'LEAF' || item.children.length > 0))

function toggleParent(menuKey: string) {
  expandedParents.value = expandedParents.value.includes(menuKey)
    ? expandedParents.value.filter((item) => item !== menuKey)
    : [...expandedParents.value, menuKey]
}

function isExpanded(menuKey: string) {
  return expandedParents.value.includes(menuKey)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <p class="brand-mark">WMS Admin</p>
      <h1>仓储管理系统</h1>
      <p class="brand-subtitle">组件化菜单、标签页工作区、可配置导航。</p>
    </div>

    <nav class="menu-tree">
      <template v-for="menu in rootMenus" :key="menu.id">
        <button
          v-if="menu.menuType === 'LEAF'"
          class="leaf-button"
          :class="{ active: activeMenuKey === menu.menuKey }"
          @click="emit('openLeaf', menu)"
        >
          <span>{{ menu.menuName }}</span>
        </button>

        <section v-else class="group">
          <button class="group-button" @click="toggleParent(menu.menuKey)">
            <span>{{ menu.menuName }}</span>
            <strong>{{ isExpanded(menu.menuKey) ? '−' : '+' }}</strong>
          </button>
          <div v-if="isExpanded(menu.menuKey)" class="group-children">
            <button
              v-for="child in menu.children"
              :key="child.id"
              class="child-button"
              :class="{ active: activeMenuKey === child.menuKey }"
              @click="emit('openLeaf', child)"
            >
              {{ child.menuName }}
            </button>
          </div>
        </section>
      </template>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 18px;
  height: 100vh;
  min-height: 100vh;
  padding: 20px 16px;
  background: #13323f;
  color: #f6fbfd;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.brand-mark {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #88cfc2;
  text-transform: uppercase;
}

.brand h1 {
  margin: 0;
  font-size: 28px;
}

.brand-subtitle {
  margin: 10px 0 0;
  color: rgba(246, 251, 253, 0.72);
  font-size: 14px;
}

.menu-tree {
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 8px;
  padding-right: 4px;
  align-content: start;
}

.leaf-button,
.group-button,
.child-button {
  width: 100%;
  height: 40px;
  min-height: 40px;
  max-height: 40px;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  text-align: left;
  flex-shrink: 0;
  white-space: nowrap;
  overflow: hidden;
}

.leaf-button,
.group-button {
  background: rgba(255, 255, 255, 0.08);
  color: #f6fbfd;
  padding: 0 12px;
}

.leaf-button.active,
.child-button.active {
  background: #91d1c5;
  color: #14313c;
}

.group {
  display: grid;
  gap: 8px;
  align-content: start;
}

.group-button {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.group-button span,
.child-button,
.leaf-button span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-children {
  display: grid;
  gap: 6px;
  padding-left: 8px;
  align-content: start;
}

.child-button {
  display: block;
  background: rgba(255, 255, 255, 0.04);
  color: rgba(246, 251, 253, 0.9);
  padding: 0 12px;
}

@media (max-width: 760px) {
  .sidebar {
    height: auto;
    min-height: 0;
    max-height: 40vh;
    grid-template-rows: auto minmax(0, 1fr);
    gap: 10px;
    padding: 12px;
    border-right: 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  }

  .brand h1 {
    font-size: 20px;
  }

  .brand-mark,
  .brand-subtitle {
    display: none;
  }

  .menu-tree {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
    padding-right: 0;
  }

  .group {
    gap: 6px;
  }

  .group-children {
    padding-left: 0;
  }

  .leaf-button,
  .group-button,
  .child-button {
    height: 36px;
    min-height: 36px;
    max-height: 36px;
    padding: 0 10px;
    font-size: 13px;
  }
}

@media (max-width: 420px) {
  .sidebar {
    max-height: 36vh;
  }

  .menu-tree {
    grid-template-columns: 1fr;
  }
}
</style>
