<script setup lang="ts">
import type { WorkspaceTab } from '../../types/app'

defineProps<{
  tabs: WorkspaceTab[]
  activeMenuKey: string
}>()

const emit = defineEmits<{
  select: [menuKey: string]
  close: [menuKey: string]
}>()
</script>

<template>
  <div class="tabs">
    <button
      v-for="tab in tabs"
      :key="tab.menuKey"
      class="tab"
      :class="{ active: activeMenuKey === tab.menuKey }"
      @click="emit('select', tab.menuKey)"
    >
      <span>{{ tab.title }}</span>
      <strong
        v-if="tab.menuKey !== 'home'"
        class="close"
        @click.stop="emit('close', tab.menuKey)"
      >
        ×
      </strong>
    </button>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 8px;
  overflow: auto;
  padding: 0 22px 12px;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 8px 8px 0 0;
  background: #eef4f6;
  color: var(--muted);
}

.tab.active {
  background: #ffffff;
  color: var(--text);
  border-bottom-color: #ffffff;
}

.close {
  font-size: 18px;
  line-height: 1;
}

@media (max-width: 760px) {
  .tabs {
    gap: 6px;
    padding: 0 12px 8px;
  }

  .tab {
    min-height: 34px;
    padding: 0 10px;
    font-size: 13px;
  }
}
</style>
