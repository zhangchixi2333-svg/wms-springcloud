<!-- 本文件实现仓库、库区和库位主数据页面，支持紧凑查询、新建和批量删除。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import type { Location, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const createOpen = ref(false)
const deleteOpen = ref(false)
const deleteSubmitting = ref(false)
const deleteMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const selectedIds = reactive<Record<number, boolean>>({})

const filters = reactive({
  warehouseName: '',
  zoneName: '',
  locationCode: '',
  warehouseType: '',
})

const form = reactive({
  locationCode: '',
  locationName: '',
  warehouseName: '',
  zoneName: '',
  warehouseType: 'OWN' as 'OWN' | 'THIRD_PARTY',
})

const rows = computed(() =>
  props.model.state.locations.filter((item) => {
    const warehouseMatch = !filters.warehouseName || item.warehouseName.toLowerCase().includes(filters.warehouseName.toLowerCase())
    const zoneMatch = !filters.zoneName || item.zoneName.toLowerCase().includes(filters.zoneName.toLowerCase())
    const codeMatch = !filters.locationCode || item.locationCode.toLowerCase().includes(filters.locationCode.toLowerCase())
    const typeMatch = !filters.warehouseType || item.warehouseType === filters.warehouseType
    return warehouseMatch && zoneMatch && codeMatch && typeMatch
  }),
)

const selectedRows = computed(() => rows.value.filter((item) => selectedIds[item.id]))
const selectedCount = computed(() => selectedRows.value.length)
const allSelected = computed(() => rows.value.length > 0 && rows.value.every((item) => selectedIds[item.id]))
const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / pageSize.value)))
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

watch(
  () => [filters.warehouseName, filters.zoneName, filters.locationCode, filters.warehouseType],
  () => {
    page.value = 1
  },
)

watch(rows, () => {
  if (page.value > totalPages.value) page.value = totalPages.value
})

function warehouseTypeText(type: Location['warehouseType']) {
  return type === 'THIRD_PARTY' ? '第三方仓库' : '自己仓库'
}

function clearSelected() {
  Object.keys(selectedIds).forEach((key) => delete selectedIds[Number(key)])
}

function resetFilters() {
  filters.warehouseName = ''
  filters.zoneName = ''
  filters.locationCode = ''
  filters.warehouseType = ''
  page.value = 1
  clearSelected()
}

function resetForm() {
  form.locationCode = ''
  form.locationName = ''
  form.warehouseName = ''
  form.zoneName = ''
  form.warehouseType = 'OWN'
}

function openCreate() {
  resetForm()
  createOpen.value = true
}

function closeCreateModal() {
  createOpen.value = false
}

function toggleSelected(id: number) {
  selectedIds[id] = !selectedIds[id]
}

function setSelected(id: number, checked: boolean) {
  selectedIds[id] = checked
}

function toggleSelectAll(checked: boolean) {
  rows.value.forEach((item) => {
    selectedIds[item.id] = checked
  })
}

async function submit() {
  await props.model.actions.createLocation(form)
  resetForm()
  closeCreateModal()
}

function openBatchDelete() {
  deleteMessage.value = ''
  deleteOpen.value = true
}

function closeDeleteModal() {
  deleteOpen.value = false
  deleteMessage.value = ''
  deleteSubmitting.value = false
}

async function submitBatchDelete() {
  const targets = selectedRows.value
  if (!targets.length || deleteSubmitting.value) return
  deleteSubmitting.value = true
  deleteMessage.value = ''
  try {
    for (const item of targets) {
      await props.model.actions.deleteLocation(item.id)
    }
    clearSelected()
    closeDeleteModal()
  } catch (error) {
    deleteMessage.value = error instanceof Error ? error.message : '仓库库区删除失败，请确认没有器具、库存或看板引用。'
  } finally {
    deleteSubmitting.value = false
  }
}
</script>

<template>
  <section class="stack warehouse-page">
    <section class="panel warehouse-filter-panel">
      <div class="warehouse-filter-line">
        <h3>仓库/库区</h3>
        <div class="warehouse-filter-row">
          <input v-model="filters.warehouseName" placeholder="仓库" />
          <input v-model="filters.zoneName" placeholder="库区" />
          <input v-model="filters.locationCode" placeholder="库位编码" />
          <select v-model="filters.warehouseType">
            <option value="">全部性质</option>
            <option value="OWN">自己仓库</option>
            <option value="THIRD_PARTY">第三方仓库</option>
          </select>
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row warehouse-actions">
          <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="rows.length" :selected="selectedCount" />
          <button @click="openCreate">新建库位</button>
          <button class="secondary-button danger-button" :disabled="!selectedCount" @click="openBatchDelete">批量删除</button>
        </div>
      </div>
    </section>

    <section class="panel table-scroll">
      <table class="table warehouse-table">
        <thead>
          <tr>
            <th class="select-col">
              <input class="compact-check" type="checkbox" :checked="allSelected" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
            </th>
            <th>仓库</th>
            <th>库区</th>
            <th>性质</th>
            <th>库位编码</th>
            <th>库位名称</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in pageRows"
            :key="item.id"
            class="warehouse-row"
            :class="{ selected: selectedIds[item.id], 'tone-a': Math.floor(index / 2) % 2 === 0, 'tone-b': Math.floor(index / 2) % 2 === 1 }"
            @click="toggleSelected(item.id)"
          >
            <td class="select-cell">
              <input class="compact-check" type="checkbox" :checked="!!selectedIds[item.id]" @click.stop @change.stop="setSelected(item.id, ($event.target as HTMLInputElement).checked)" />
            </td>
            <td>{{ item.warehouseName }}</td>
            <td>{{ item.zoneName }}</td>
            <td><span class="type-badge" :class="{ third: item.warehouseType === 'THIRD_PARTY' }">{{ warehouseTypeText(item.warehouseType) }}</span></td>
            <td class="mono">{{ item.locationCode }}</td>
            <td>{{ item.locationName }}</td>
          </tr>
          <tr v-if="!pageRows.length">
            <td colspan="6" class="empty-cell">没有匹配的仓库库区</td>
          </tr>
        </tbody>
      </table>
    </section>

    <PageModal :open="createOpen" wide @close="closeCreateModal">
      <section class="panel warehouse-create-panel">
        <div class="section-head compact-head">
          <div>
            <h3>新建仓库/库区</h3>
          </div>
          <div class="action-row">
            <button @click="submit">保存仓库/库区</button>
          </div>
        </div>
        <div class="create-table-wrap">
          <div class="warehouse-create-row warehouse-create-header">
            <span>仓库</span>
            <span>库区</span>
            <span>性质</span>
            <span>库位编码</span>
            <span>库位名称</span>
          </div>
          <div class="warehouse-create-row warehouse-create-data">
            <input v-model="form.warehouseName" placeholder="如 总仓" />
            <input v-model="form.zoneName" placeholder="如 A区" />
            <select v-model="form.warehouseType">
              <option value="OWN">自己仓库</option>
              <option value="THIRD_PARTY">第三方仓库</option>
            </select>
            <input v-model="form.locationCode" placeholder="如 LOC-A-01" />
            <input v-model="form.locationName" placeholder="库位名称" />
          </div>
        </div>
      </section>
    </PageModal>

    <PageModal :open="deleteOpen" wide @close="closeDeleteModal">
      <section class="panel warehouse-delete-panel">
        <div class="section-head compact-head">
          <div>
            <h3>批量删除仓库/库区</h3>
          </div>
          <div class="action-row">
            <button class="danger-button" :disabled="!selectedCount || deleteSubmitting" @click="submitBatchDelete">
              {{ deleteSubmitting ? '正在删除...' : `确认删除 ${selectedCount} 个` }}
            </button>
          </div>
        </div>
        <div class="table-scroll delete-table-wrap">
          <table class="table warehouse-delete-table">
            <thead>
              <tr>
                <th>仓库</th>
                <th>库区</th>
                <th>性质</th>
                <th>库位编码</th>
                <th>库位名称</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedRows" :key="item.id">
                <td>{{ item.warehouseName }}</td>
                <td>{{ item.zoneName }}</td>
                <td>{{ warehouseTypeText(item.warehouseType) }}</td>
                <td class="mono">{{ item.locationCode }}</td>
                <td>{{ item.locationName }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="deleteMessage" class="form-error">{{ deleteMessage }}</p>
      </section>
    </PageModal>
  </section>
</template>

<style scoped>
.warehouse-filter-panel {
  padding: 8px 10px;
}

.warehouse-filter-line {
  display: grid;
  grid-template-columns: auto minmax(640px, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.warehouse-filter-line h3,
.compact-head h3 {
  margin: 0;
  white-space: nowrap;
  font-size: 16px;
}

.warehouse-filter-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(120px, 1fr) minmax(140px, 1.1fr) 132px 52px;
  gap: 5px;
  align-items: center;
}

.warehouse-filter-row input,
.warehouse-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.warehouse-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.warehouse-actions button {
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
}

.table-scroll,
.create-table-wrap {
  overflow-x: auto;
}

.warehouse-table {
  min-width: 980px;
  table-layout: fixed;
}

.warehouse-table th,
.warehouse-table td,
.warehouse-delete-table th,
.warehouse-delete-table td {
  padding: 7px 9px;
  vertical-align: middle;
}

.warehouse-table .select-col,
.warehouse-table .select-cell {
  width: 38px;
  padding-inline: 6px;
  text-align: center;
}

.warehouse-table th:nth-child(2),
.warehouse-table td:nth-child(2),
.warehouse-table th:nth-child(3),
.warehouse-table td:nth-child(3) {
  width: 150px;
}

.warehouse-table th:nth-child(4),
.warehouse-table td:nth-child(4) {
  width: 120px;
}

.warehouse-table th:nth-child(5),
.warehouse-table td:nth-child(5) {
  width: 150px;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
}

.warehouse-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.warehouse-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.warehouse-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.warehouse-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.warehouse-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.type-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.13);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.type-badge.third {
  background: rgba(245, 158, 11, 0.14);
  color: #92400e;
}

.warehouse-create-panel,
.warehouse-delete-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.compact-head {
  align-items: center;
  margin-bottom: 0;
}

.warehouse-create-row {
  display: grid;
  grid-template-columns: 170px 150px 132px 170px minmax(220px, 1fr);
  gap: 6px;
  align-items: center;
  min-width: 900px;
}

.warehouse-create-header {
  padding: 0 2px 2px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.warehouse-create-data {
  padding: 4px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.warehouse-create-data input,
.warehouse-create-data select {
  min-width: 0;
  min-height: 32px;
}

.delete-table-wrap {
  max-height: min(480px, 56vh);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.warehouse-delete-table {
  min-width: 900px;
  table-layout: fixed;
}

.form-error {
  margin: 0;
  color: #dc2626;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

@media (max-width: 1180px) {
  .warehouse-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .warehouse-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .warehouse-filter-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
