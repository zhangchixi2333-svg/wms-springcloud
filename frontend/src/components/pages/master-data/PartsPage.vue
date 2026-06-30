<!-- 本文件实现零件主数据页面，绑定分类、供应商、默认器具和默认包装容量。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import type { ConfigItem, PageModel, Part } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const createOpen = ref(false)
const deleteOpen = ref(false)
const deleteSubmitting = ref(false)
const deleteMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const categories = ref<ConfigItem[]>([])
const selectedPartIds = reactive<Record<number, boolean>>({})

const filters = reactive({
  keyword: '',
  categoryCode: '',
  supplierId: 0,
  equipmentCode: '',
})

const form = reactive({
  partCode: '',
  partName: '',
  unit: 'PCS',
  categoryCode: 'DEFAULT',
  supplierId: 0,
  defaultEquipmentCode: '',
  defaultUnitPerBox: 1,
})

const enabledCategories = computed(() => categories.value.filter((item) => item.status !== 'DISABLED'))
const enabledEquipment = computed(() => props.model.state.equipment.filter((item) => item.status !== 'DISABLED'))
const thirdPartyWarehouseZones = computed(() =>
  props.model.state.locations
    .filter((item) => item.warehouseType === 'THIRD_PARTY')
    .map((item) => `${item.warehouseName} / ${item.zoneName}`),
)

const rows = computed(() =>
  props.model.state.parts.filter((item) => {
    const keywordMatch = !filters.keyword || `${item.partCode} ${item.partName}`.toLowerCase().includes(filters.keyword.toLowerCase())
    const categoryMatch = !filters.categoryCode || (item.categoryCode || 'DEFAULT') === filters.categoryCode
    const supplierMatch = !filters.supplierId || item.supplierId === filters.supplierId
    const equipmentMatch = !filters.equipmentCode || item.defaultEquipmentCode === filters.equipmentCode
    return keywordMatch && categoryMatch && supplierMatch && equipmentMatch
  }),
)

const selectedRows = computed(() => rows.value.filter((item) => selectedPartIds[item.id]))
const selectedCount = computed(() => selectedRows.value.length)
const allSelected = computed(() => rows.value.length > 0 && rows.value.every((item) => selectedPartIds[item.id]))
const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / pageSize.value)))
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

watch(
  () => [filters.keyword, filters.categoryCode, filters.supplierId, filters.equipmentCode],
  () => {
    page.value = 1
  },
)

watch(rows, () => {
  if (page.value > totalPages.value) page.value = totalPages.value
})

function categoryName(categoryCode: string | null | undefined) {
  const code = categoryCode || 'DEFAULT'
  return categories.value.find((item) => item.itemCode === code)?.itemName || code
}

function supplierName(supplierId: number | null) {
  return props.model.state.suppliers.find((item) => item.id === supplierId)?.supplierName || '-'
}

function equipmentText(equipmentCode: string | null) {
  if (!equipmentCode) return '-'
  const equipment = props.model.state.equipment.find((item) => item.equipmentCode === equipmentCode)
  return equipment ? `${equipment.equipmentCode} | ${equipment.equipmentName}` : equipmentCode
}

function isThirdPartyEquipment(equipmentCode: string | null | undefined) {
  if (!equipmentCode) return false
  const equipment = props.model.state.equipment.find((item) => item.equipmentCode === equipmentCode)
  if (!equipment) return false
  return thirdPartyWarehouseZones.value.includes(`${equipment.warehouseName} / ${equipment.zoneName}`)
}

function firstEquipmentByWarehouseType(warehouseType: 'OWN' | 'THIRD_PARTY') {
  return enabledEquipment.value.find((equipment) => {
    const equipmentZone = `${equipment.warehouseName} / ${equipment.zoneName}`
    const thirdParty = thirdPartyWarehouseZones.value.includes(equipmentZone)
    return warehouseType === 'THIRD_PARTY' ? thirdParty : !thirdParty
  })?.equipmentCode ?? ''
}

function handleCategoryChange() {
  if (form.categoryCode === 'OUTSOURCED' && !isThirdPartyEquipment(form.defaultEquipmentCode)) {
    form.defaultEquipmentCode = firstEquipmentByWarehouseType('THIRD_PARTY') || form.defaultEquipmentCode
    return
  }
  if (form.categoryCode !== 'OUTSOURCED' && isThirdPartyEquipment(form.defaultEquipmentCode)) {
    form.defaultEquipmentCode = firstEquipmentByWarehouseType('OWN') || ''
  }
}

function handleDefaultEquipmentChange() {
  if (isThirdPartyEquipment(form.defaultEquipmentCode) && (!form.categoryCode || form.categoryCode === 'DEFAULT')) {
    form.categoryCode = 'OUTSOURCED'
  }
}

function clearSelectedParts() {
  Object.keys(selectedPartIds).forEach((key) => delete selectedPartIds[Number(key)])
}

function resetFilters() {
  filters.keyword = ''
  filters.categoryCode = ''
  filters.supplierId = 0
  filters.equipmentCode = ''
  page.value = 1
  clearSelectedParts()
}

function resetForm() {
  form.partCode = ''
  form.partName = ''
  form.unit = 'PCS'
  form.categoryCode = enabledCategories.value.some((item) => item.itemCode === 'DEFAULT') ? 'DEFAULT' : enabledCategories.value[0]?.itemCode ?? 'DEFAULT'
  form.supplierId = 0
  form.defaultEquipmentCode = ''
  form.defaultUnitPerBox = 1
}

function openCreate() {
  resetForm()
  createOpen.value = true
}

function closeCreateModal() {
  createOpen.value = false
}

function togglePartSelected(part: Part) {
  selectedPartIds[part.id] = !selectedPartIds[part.id]
}

function setPartSelected(partId: number, checked: boolean) {
  selectedPartIds[partId] = checked
}

function toggleSelectAll(checked: boolean) {
  rows.value.forEach((item) => {
    selectedPartIds[item.id] = checked
  })
}

async function submit() {
  await props.model.actions.createPart({
    partCode: form.partCode,
    partName: form.partName,
    unit: form.unit,
    categoryCode: form.categoryCode || null,
    supplierId: form.supplierId || null,
    defaultEquipmentCode: form.defaultEquipmentCode || null,
    defaultUnitPerBox: form.defaultUnitPerBox || null,
  })
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
      await props.model.actions.deletePart(item.id)
    }
    deleteMessage.value = `删除成功：${targets.map((item) => item.partCode).join('、')}`
    clearSelectedParts()
    closeDeleteModal()
  } catch (error) {
    deleteMessage.value = error instanceof Error ? error.message : '零件删除失败，请确认没有业务单据引用。'
  } finally {
    deleteSubmitting.value = false
  }
}

async function loadCategories() {
  categories.value = await api.listConfigItems('categoryManagement')
}

onMounted(loadCategories)
</script>

<template>
  <section class="stack">
    <section class="panel part-filter-panel">
      <div class="part-filter-line">
        <h3>零件信息</h3>
        <div class="part-filter-row">
          <input v-model="filters.keyword" placeholder="编码 / 名称" />
          <select v-model="filters.categoryCode">
            <option value="">全部分类</option>
            <option v-for="item in enabledCategories" :key="item.id" :value="item.itemCode">
              {{ item.itemCode }} | {{ item.itemName }}
            </option>
          </select>
          <select v-model.number="filters.supplierId">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <select v-model="filters.equipmentCode">
            <option value="">全部器具</option>
            <option v-for="item in enabledEquipment" :key="item.id" :value="item.equipmentCode">
              {{ item.equipmentCode }} | {{ item.equipmentName }}
            </option>
          </select>
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row part-actions">
          <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="rows.length" :selected="selectedCount" />
          <button @click="openCreate">新建零件</button>
          <button class="secondary-button danger-button" :disabled="!selectedCount" @click="openBatchDelete">批量删除</button>
        </div>
      </div>
    </section>

    <section class="panel table-scroll">
      <table class="table part-table">
        <thead>
          <tr>
            <th class="select-col">
              <input class="compact-check" type="checkbox" :checked="allSelected" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
            </th>
            <th>编码</th>
            <th>名称</th>
            <th>分类</th>
            <th>单位</th>
            <th>供应商</th>
            <th>默认器具</th>
            <th>默认容量</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in pageRows"
            :key="item.id"
            class="part-row"
            :class="{ selected: selectedPartIds[item.id], 'tone-a': Math.floor(index / 2) % 2 === 0, 'tone-b': Math.floor(index / 2) % 2 === 1 }"
            @click="togglePartSelected(item)"
          >
            <td class="select-cell">
              <input class="compact-check" type="checkbox" :checked="!!selectedPartIds[item.id]" @click.stop @change.stop="setPartSelected(item.id, ($event.target as HTMLInputElement).checked)" />
            </td>
            <td class="mono">{{ item.partCode }}</td>
            <td>{{ item.partName }}</td>
            <td>{{ categoryName(item.categoryCode) }}</td>
            <td>{{ item.unit }}</td>
            <td>{{ supplierName(item.supplierId) }}</td>
            <td>{{ equipmentText(item.defaultEquipmentCode) }}</td>
            <td>{{ item.defaultUnitPerBox || '-' }}</td>
          </tr>
          <tr v-if="!pageRows.length">
            <td colspan="8" class="empty-cell">没有匹配的零件</td>
          </tr>
        </tbody>
      </table>
    </section>

    <PageModal :open="createOpen" wide @close="closeCreateModal">
      <section class="panel part-create-panel">
        <div class="section-head compact-head">
          <div>
            <h3>新建零件</h3>
          </div>
          <div class="action-row">
            <button @click="submit">保存零件</button>
          </div>
        </div>
        <div class="create-table-wrap">
          <div class="part-create-row part-create-header">
            <span>零件编码</span>
            <span>零件名称</span>
            <span>单位</span>
            <span>分类</span>
            <span>供应商</span>
            <span>默认器具</span>
            <span>默认容量</span>
          </div>
          <div class="part-create-row part-create-data">
            <input v-model="form.partCode" placeholder="如 P-001" />
            <input v-model="form.partName" placeholder="零件名称" />
            <input v-model="form.unit" placeholder="PCS" />
            <select v-model="form.categoryCode" @change="handleCategoryChange">
              <option value="">不绑定分类</option>
              <option v-for="item in enabledCategories" :key="item.id" :value="item.itemCode">
                {{ item.itemCode }} | {{ item.itemName }}
              </option>
            </select>
            <select v-model.number="form.supplierId">
              <option :value="0">不绑定供应商</option>
              <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
                {{ item.supplierCode }} | {{ item.supplierName }}
              </option>
            </select>
            <select v-model="form.defaultEquipmentCode" @change="handleDefaultEquipmentChange">
              <option value="">不绑定默认器具</option>
              <option v-for="item in enabledEquipment" :key="item.id" :value="item.equipmentCode">
                {{ item.equipmentCode }} | {{ item.equipmentName }}
              </option>
            </select>
            <input v-model.number="form.defaultUnitPerBox" type="number" min="0.001" step="0.001" placeholder="每箱数量" />
          </div>
        </div>
      </section>
    </PageModal>

    <PageModal :open="deleteOpen" wide @close="closeDeleteModal">
      <section class="panel part-delete-panel">
        <div class="section-head compact-head">
          <div>
            <h3>批量删除零件</h3>
          </div>
          <div class="action-row">
            <button class="danger-button" :disabled="!selectedCount || deleteSubmitting" @click="submitBatchDelete">
              {{ deleteSubmitting ? '正在删除...' : `确认删除 ${selectedCount} 个` }}
            </button>
          </div>
        </div>
        <div class="table-scroll delete-table-wrap">
          <table class="table part-delete-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>分类</th>
                <th>供应商</th>
                <th>默认器具</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedRows" :key="item.id">
                <td class="mono">{{ item.partCode }}</td>
                <td>{{ item.partName }}</td>
                <td>{{ categoryName(item.categoryCode) }}</td>
                <td>{{ supplierName(item.supplierId) }}</td>
                <td>{{ equipmentText(item.defaultEquipmentCode) }}</td>
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
.part-filter-panel {
  padding: 8px 10px;
}

.part-filter-line {
  display: grid;
  grid-template-columns: auto minmax(640px, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.part-filter-line h3,
.compact-head h3 {
  margin: 0;
  white-space: nowrap;
  font-size: 16px;
}

.part-filter-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) minmax(150px, 1fr) minmax(180px, 1.2fr) minmax(170px, 1.1fr) 52px;
  gap: 5px;
  align-items: center;
}

.part-filter-row input,
.part-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.part-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.part-actions button {
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
}

.table-scroll,
.create-table-wrap {
  overflow-x: auto;
}

.part-table {
  min-width: 1120px;
  table-layout: fixed;
}

.part-table th,
.part-table td,
.part-delete-table th,
.part-delete-table td {
  padding: 7px 9px;
  vertical-align: middle;
}

.part-table .select-col,
.part-table .select-cell {
  width: 38px;
  padding-inline: 6px;
  text-align: center;
}

.part-table th:nth-child(2),
.part-table td:nth-child(2) {
  width: 118px;
}

.part-table th:nth-child(5),
.part-table td:nth-child(5),
.part-table th:nth-child(8),
.part-table td:nth-child(8) {
  width: 108px;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
}

.part-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.part-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.part-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.part-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.part-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.part-create-panel,
.part-delete-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.compact-head {
  align-items: center;
  margin-bottom: 0;
}

.part-create-row {
  display: grid;
  grid-template-columns: 132px minmax(160px, 1fr) 82px 180px 210px 220px 112px;
  gap: 6px;
  align-items: center;
  min-width: 1110px;
}

.part-create-header {
  padding: 0 2px 2px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.part-create-data {
  padding: 4px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.part-create-data input,
.part-create-data select {
  min-width: 0;
  min-height: 32px;
}

.delete-table-wrap {
  max-height: min(480px, 56vh);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.part-delete-table {
  min-width: 860px;
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
  .part-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .part-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .part-filter-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
