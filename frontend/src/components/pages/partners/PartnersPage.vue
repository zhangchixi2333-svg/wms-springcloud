<!-- 本文件实现供应商和客户主数据页面，支持紧凑查询、新建和批量删除。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import type { Customer, PageModel, Supplier } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode: 'supplier' | 'customer' }>()

type PartnerRow = {
  id: number
  code: string
  name: string
  source: Supplier | Customer
}

const createOpen = ref(false)
const deleteOpen = ref(false)
const deleteSubmitting = ref(false)
const deleteMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const selectedIds = reactive<Record<number, boolean>>({})

const filters = reactive({
  keyword: '',
})

const supplierForm = reactive({ supplierCode: '', supplierName: '' })
const customerForm = reactive({ customerCode: '', customerName: '' })

const pageTitle = computed(() => (props.mode === 'supplier' ? '供应商' : '客户'))
const codeLabel = computed(() => `${pageTitle.value}编码`)
const nameLabel = computed(() => `${pageTitle.value}名称`)

const rows = computed<PartnerRow[]>(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  const sourceRows =
    props.mode === 'supplier'
      ? props.model.state.suppliers.map((item) => ({
          id: item.id,
          code: item.supplierCode,
          name: item.supplierName,
          source: item,
        }))
      : props.model.state.customers.map((item) => ({
          id: item.id,
          code: item.customerCode,
          name: item.customerName,
          source: item,
        }))
  return sourceRows.filter((item) => !keyword || `${item.code} ${item.name}`.toLowerCase().includes(keyword))
})

const selectedRows = computed(() => rows.value.filter((item) => selectedIds[item.id]))
const selectedCount = computed(() => selectedRows.value.length)
const allSelected = computed(() => rows.value.length > 0 && rows.value.every((item) => selectedIds[item.id]))
const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / pageSize.value)))
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

watch(
  () => filters.keyword,
  () => {
    page.value = 1
  },
)

watch(rows, () => {
  if (page.value > totalPages.value) page.value = totalPages.value
})

function clearSelected() {
  Object.keys(selectedIds).forEach((key) => delete selectedIds[Number(key)])
}

function resetFilters() {
  filters.keyword = ''
  page.value = 1
  clearSelected()
}

function resetForms() {
  supplierForm.supplierCode = ''
  supplierForm.supplierName = ''
  customerForm.customerCode = ''
  customerForm.customerName = ''
}

function openCreate() {
  resetForms()
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

async function submitCreate() {
  if (props.mode === 'supplier') {
    await props.model.actions.createSupplier(supplierForm)
  } else {
    await props.model.actions.createCustomer(customerForm)
  }
  resetForms()
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
      if (props.mode === 'supplier') {
        await props.model.actions.deleteSupplier(item.id)
      } else {
        await props.model.actions.deleteCustomer(item.id)
      }
    }
    clearSelected()
    closeDeleteModal()
  } catch (error) {
    deleteMessage.value = error instanceof Error ? error.message : `${pageTitle.value}删除失败，请确认没有业务数据引用。`
  } finally {
    deleteSubmitting.value = false
  }
}
</script>

<template>
  <section class="stack partner-page">
    <section class="panel partner-filter-panel">
      <div class="partner-filter-line">
        <h3>{{ pageTitle }}管理</h3>
        <div class="partner-filter-row">
          <input v-model="filters.keyword" :placeholder="`${codeLabel} / ${nameLabel}`" />
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row partner-actions">
          <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="rows.length" :selected="selectedCount" />
          <button @click="openCreate">新建{{ pageTitle }}</button>
          <button class="secondary-button danger-button" :disabled="!selectedCount" @click="openBatchDelete">批量删除</button>
        </div>
      </div>
    </section>

    <section class="panel table-scroll">
      <table class="table partner-table">
        <thead>
          <tr>
            <th class="select-col">
              <input class="compact-check" type="checkbox" :checked="allSelected" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
            </th>
            <th>{{ codeLabel }}</th>
            <th>{{ nameLabel }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in pageRows"
            :key="item.id"
            class="partner-row"
            :class="{ selected: selectedIds[item.id], 'tone-a': Math.floor(index / 2) % 2 === 0, 'tone-b': Math.floor(index / 2) % 2 === 1 }"
            @click="toggleSelected(item.id)"
          >
            <td class="select-cell">
              <input class="compact-check" type="checkbox" :checked="!!selectedIds[item.id]" @click.stop @change.stop="setSelected(item.id, ($event.target as HTMLInputElement).checked)" />
            </td>
            <td class="mono">{{ item.code }}</td>
            <td>{{ item.name }}</td>
          </tr>
          <tr v-if="!pageRows.length">
            <td colspan="3" class="empty-cell">没有匹配的{{ pageTitle }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <PageModal :open="createOpen" @close="closeCreateModal">
      <section class="panel partner-create-panel">
        <div class="section-head compact-head">
          <div>
            <h3>新建{{ pageTitle }}</h3>
          </div>
          <div class="action-row">
            <button @click="submitCreate">保存{{ pageTitle }}</button>
          </div>
        </div>
        <div class="create-table-wrap">
          <div class="partner-create-row partner-create-header">
            <span>{{ codeLabel }}</span>
            <span>{{ nameLabel }}</span>
          </div>
          <div v-if="mode === 'supplier'" class="partner-create-row partner-create-data">
            <input v-model="supplierForm.supplierCode" placeholder="如 SUP-001" />
            <input v-model="supplierForm.supplierName" placeholder="供应商名称" />
          </div>
          <div v-else class="partner-create-row partner-create-data">
            <input v-model="customerForm.customerCode" placeholder="如 CUS-001" />
            <input v-model="customerForm.customerName" placeholder="客户名称" />
          </div>
        </div>
      </section>
    </PageModal>

    <PageModal :open="deleteOpen" wide @close="closeDeleteModal">
      <section class="panel partner-delete-panel">
        <div class="section-head compact-head">
          <div>
            <h3>批量删除{{ pageTitle }}</h3>
          </div>
          <div class="action-row">
            <button class="danger-button" :disabled="!selectedCount || deleteSubmitting" @click="submitBatchDelete">
              {{ deleteSubmitting ? '正在删除...' : `确认删除 ${selectedCount} 个` }}
            </button>
          </div>
        </div>
        <div class="table-scroll delete-table-wrap">
          <table class="table partner-delete-table">
            <thead>
              <tr>
                <th>{{ codeLabel }}</th>
                <th>{{ nameLabel }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedRows" :key="item.id">
                <td class="mono">{{ item.code }}</td>
                <td>{{ item.name }}</td>
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
.partner-filter-panel {
  padding: 8px 10px;
}

.partner-filter-line {
  display: grid;
  grid-template-columns: auto minmax(320px, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.partner-filter-line h3,
.compact-head h3 {
  margin: 0;
  white-space: nowrap;
  font-size: 16px;
}

.partner-filter-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 52px;
  gap: 5px;
  align-items: center;
}

.partner-filter-row input,
.compact-filter-button {
  min-height: 34px;
}

.partner-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.partner-actions button {
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
}

.table-scroll,
.create-table-wrap {
  overflow-x: auto;
}

.partner-table {
  min-width: 760px;
  table-layout: fixed;
}

.partner-table th,
.partner-table td,
.partner-delete-table th,
.partner-delete-table td {
  padding: 7px 9px;
  vertical-align: middle;
}

.partner-table .select-col,
.partner-table .select-cell {
  width: 38px;
  padding-inline: 6px;
  text-align: center;
}

.partner-table th:nth-child(2),
.partner-table td:nth-child(2) {
  width: 180px;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
}

.partner-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.partner-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.partner-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.partner-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.partner-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.partner-create-panel,
.partner-delete-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.compact-head {
  align-items: center;
  margin-bottom: 0;
}

.partner-create-row {
  display: grid;
  grid-template-columns: 180px minmax(260px, 1fr);
  gap: 6px;
  align-items: center;
  min-width: 520px;
}

.partner-create-header {
  padding: 0 2px 2px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.partner-create-data {
  padding: 4px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.partner-create-data input {
  min-width: 0;
  min-height: 32px;
}

.delete-table-wrap {
  max-height: min(460px, 56vh);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.partner-delete-table {
  min-width: 620px;
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

@media (max-width: 980px) {
  .partner-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .partner-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .partner-filter-row,
  .partner-create-row {
    grid-template-columns: 1fr;
  }
}
</style>
