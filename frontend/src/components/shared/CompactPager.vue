<!-- 本文件提供列表页紧凑分页条，统一展示总数、已选数量、页码、每页数量和翻页按钮。 -->
<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    total: number
    page: number
    pageSize: number
    selected?: number
    pageSizeOptions?: number[]
  }>(),
  {
    selected: 0,
    pageSizeOptions: () => [10, 20, 50, 100],
  },
)

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
}>()

function totalPages() {
  return Math.max(1, Math.ceil(props.total / props.pageSize))
}

function changePage(nextPage: number) {
  emit('update:page', Math.min(Math.max(1, nextPage), totalPages()))
}

function changePageSize(value: string) {
  emit('update:pageSize', Number(value))
  emit('update:page', 1)
}
</script>

<template>
  <div class="compact-pager">
    <span class="pager-summary">共 {{ total }} 条<span v-if="selected"> / 已选 {{ selected }} 条</span></span>
    <button class="secondary-button pager-button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
    <span class="pager-page">第 {{ page }} / {{ totalPages() }} 页</span>
    <button class="secondary-button pager-button" :disabled="page >= totalPages()" @click="changePage(page + 1)">下一页</button>
    <select class="pager-size" :value="pageSize" @change="changePageSize(($event.target as HTMLSelectElement).value)">
      <option v-for="item in pageSizeOptions" :key="item" :value="item">每页 {{ item }}</option>
    </select>
  </div>
</template>

<style scoped>
.compact-pager {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.pager-summary,
.pager-page {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.92);
}

.pager-button {
  min-height: 30px;
  padding: 0 8px;
}

.pager-size {
  width: 88px;
  min-height: 30px;
  padding: 0 8px;
}

@media (max-width: 760px) {
  .compact-pager {
    flex-wrap: wrap;
  }
}
</style>
