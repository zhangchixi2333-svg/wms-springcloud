<!-- 本组件承载创建出库单浮窗：按可用库存选择零件，默认绑定零件器具，并生成交给后端 FIFO 分配的重包计划。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '../../../../api/wms'
import { compareKanbanFifo, formatDateTime } from '../../../../app/kanbanHelpers'
import { equipmentCodeOptions } from '../../../../app/optionHelpers'
import PageModal from '../../../shared/PageModal.vue'
import type { Kanban, OutboundDraftItem, PageModel, Part } from '../../../../types/app'

const props = defineProps<{
  open: boolean
  model: PageModel
}>()

const emit = defineEmits<{
  close: []
  created: []
}>()

type AvailableOutboundRow = {
  key: string
  partId: number
  partCode: string
  partName: string
  unit: string
  locationCode: string
  warehouseName: string
  zoneName: string
  availableBoxes: number
  availableQty: number
  oldestInboundTime: string | null
  inboundNos: string[]
  boxes: Kanban[]
}

type OutboundDraftRow = {
  plannedQty: number | null
  boxCount: number | null
  equipmentCode: string
  unitPerBox: number | null
}

const loadingOutboundBoxes = ref(false)
const createMessage = ref('')
const outboundDraft = reactive<Record<string, OutboundDraftRow>>({})
const candidateBoxes = ref<Kanban[]>([])

const form = reactive({
  customerId: 0,
})

const createFilters = reactive({
  partKeyword: '',
  warehouseName: '',
  zoneName: '',
})

const outboundEquipmentOptions = computed(() => equipmentCodeOptions(props.model.state.equipment))

const availableBoxes = computed(() =>
  candidateBoxes.value
    .filter((item) => ['INBOUND', 'PARTIAL_OUTBOUND'].includes(item.status))
    .filter((item) => item.locationCode && item.locationCode !== '-')
    .filter((item) => kanbanFreeQty(item) > 0)
    .sort(compareKanbanFifo),
)

const warehouseOptions = computed(() => Array.from(new Set(availableBoxes.value.map((item) => item.warehouseName).filter(Boolean))).sort())
const zoneOptions = computed(() =>
  Array.from(new Set(availableBoxes.value
    .filter((item) => !createFilters.warehouseName || item.warehouseName === createFilters.warehouseName)
    .map((item) => item.zoneName)
    .filter(Boolean))).sort(),
)

const availableRows = computed<AvailableOutboundRow[]>(() => {
  const map = new Map<string, AvailableOutboundRow>()
  availableBoxes.value.forEach((box) => {
    const key = `${box.partId}:${box.locationCode}`
    const row = map.get(key) ?? {
      key,
      partId: box.partId,
      partCode: box.partCode,
      partName: box.partName,
      unit: box.unit,
      locationCode: box.locationCode,
      warehouseName: box.warehouseName,
      zoneName: box.zoneName,
      availableBoxes: 0,
      availableQty: 0,
      oldestInboundTime: box.inboundTime,
      inboundNos: [],
      boxes: [],
    }
    row.availableBoxes += 1
    row.availableQty += kanbanFreeQty(box)
    row.oldestInboundTime = minTime(row.oldestInboundTime, box.inboundTime)
    if (box.inboundNo && !row.inboundNos.includes(box.inboundNo)) {
      row.inboundNos.push(box.inboundNo)
    }
    row.boxes.push(box)
    map.set(key, row)
  })
  return Array.from(map.values())
    .filter((row) => {
      const keyword = createFilters.partKeyword.trim().toLowerCase()
      const partMatch = !keyword || `${row.partCode} ${row.partName}`.toLowerCase().includes(keyword)
      const warehouseMatch = !createFilters.warehouseName || row.warehouseName === createFilters.warehouseName
      const zoneMatch = !createFilters.zoneName || row.zoneName === createFilters.zoneName
      return partMatch && warehouseMatch && zoneMatch
    })
    .sort((a, b) => `${a.partCode}-${a.locationCode}`.localeCompare(`${b.partCode}-${b.locationCode}`))
})

const plannedRows = computed(() =>
  availableRows.value
    .map((row) => ({
      row,
      plannedQty: normalizedDraftQty(row),
      boxCount: normalizedDraftBoxCount(row),
      unitPerBox: draftUnitPerBox(row),
      equipmentCode: ensureDraft(row).equipmentCode || null,
    }))
    .filter((item) => item.plannedQty > 0 && item.boxCount > 0),
)

const plannedBoxCount = computed(() => plannedRows.value.reduce((sum, item) => sum + item.boxCount, 0))
const plannedQty = computed(() => plannedRows.value.reduce((sum, item) => sum + item.plannedQty, 0))
const availableSourceBoxes = computed(() => availableRows.value.reduce((sum, row) => sum + row.availableBoxes, 0))

function partForRow(row: AvailableOutboundRow): Part | undefined {
  return props.model.state.parts.find((item) => item.id === row.partId)
}

function defaultEquipmentCodeForRow(row: AvailableOutboundRow) {
  const code = partForRow(row)?.defaultEquipmentCode || ''
  if (!code) return ''
  return props.model.state.equipment.some((item) => item.equipmentCode === code && item.status !== 'DISABLED') ? code : ''
}

function defaultUnitPerBoxForRow(row: AvailableOutboundRow, equipmentCode: string) {
  const equipmentUnit = equipmentCapacity(equipmentCode)
  if (equipmentUnit > 0) return equipmentUnit
  if (!equipmentCode) return null
  const partUnit = Number(partForRow(row)?.defaultUnitPerBox ?? 0)
  return Number.isFinite(partUnit) && partUnit > 0 ? partUnit : null
}

function kanbanFreeQty(kanban: Kanban) {
  const available = Number(kanban.availableQty ?? kanban.qty ?? 0)
  const reserved = Number(kanban.reservedQty ?? 0)
  return Math.max(0, Number((available - reserved).toFixed(3)))
}

function minTime(left: string | null, right: string | null) {
  if (!left) return right
  if (!right) return left
  return left <= right ? left : right
}

function ensureDraft(row: AvailableOutboundRow) {
  if (!outboundDraft[row.key]) {
    const equipmentCode = defaultEquipmentCodeForRow(row)
    outboundDraft[row.key] = {
      plannedQty: null,
      boxCount: null,
      equipmentCode,
      unitPerBox: defaultUnitPerBoxForRow(row, equipmentCode),
    }
  }
  return outboundDraft[row.key]
}

function equipmentCapacity(equipmentCode?: string | null) {
  if (!equipmentCode) return 0
  const equipment = props.model.state.equipment.find((item) => item.equipmentCode === equipmentCode)
  const capacity = Number(equipment?.capacity ?? 0)
  return Number.isFinite(capacity) && capacity > 0 ? capacity : 0
}

function ceilBoxCount(plannedQty: number, unitPerBox: number) {
  if (!plannedQty || !unitPerBox || plannedQty <= 0 || unitPerBox <= 0) return 0
  return Math.max(1, Math.ceil(plannedQty / unitPerBox))
}

function syncOutboundDraftByEquipment(row: AvailableOutboundRow) {
  const draft = ensureDraft(row)
  const capacity = equipmentCapacity(draft.equipmentCode)
  draft.unitPerBox = capacity > 0 ? capacity : defaultUnitPerBoxForRow(row, draft.equipmentCode)
  if (draft.unitPerBox && draft.unitPerBox > 0) {
    draft.boxCount = ceilBoxCount(Number(draft.plannedQty), draft.unitPerBox) || null
  }
}

function normalizedDraftQty(row: AvailableOutboundRow) {
  const value = Number(ensureDraft(row).plannedQty ?? 0)
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.min(Number(value.toFixed(3)), row.availableQty)
}

function normalizedDraftBoxCount(row: AvailableOutboundRow) {
  const value = Number(ensureDraft(row).boxCount ?? 0)
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.floor(value)
}

function draftUnitPerBox(row: AvailableOutboundRow) {
  const draft = ensureDraft(row)
  const equipmentUnit = equipmentCapacity(draft.equipmentCode)
  if (equipmentUnit > 0) return equipmentUnit
  const defaultUnit = defaultUnitPerBoxForRow(row, draft.equipmentCode)
  if (defaultUnit && defaultUnit > 0) return defaultUnit
  const qty = normalizedDraftQty(row)
  const boxes = normalizedDraftBoxCount(row)
  if (!qty || !boxes) return 0
  return Number((qty / boxes).toFixed(3))
}

function isBoxCountReadonly(row: AvailableOutboundRow) {
  const unitPerBox = ensureDraft(row).unitPerBox
  return typeof unitPerBox === 'number' && unitPerBox > 0
}

function fifoPreviewBoxes(row: AvailableOutboundRow) {
  let remaining = normalizedDraftQty(row)
  if (remaining <= 0) return row.boxes.slice(0, 1)
  const selected: Kanban[] = []
  for (const box of row.boxes) {
    if (remaining <= 0) break
    const freeQty = kanbanFreeQty(box)
    if (freeQty <= 0) continue
    selected.push(box)
    remaining -= freeQty
  }
  return selected
}

function setDraftQty(row: AvailableOutboundRow, value: number) {
  ensureDraft(row).plannedQty = Math.max(0, Math.min(Number(value) || 0, row.availableQty))
  syncOutboundDraftByEquipment(row)
}

function setDraftBoxes(row: AvailableOutboundRow, value: number) {
  const draft = ensureDraft(row)
  if (draft.unitPerBox && draft.unitPerBox > 0) {
    syncOutboundDraftByEquipment(row)
    return
  }
  draft.boxCount = Math.max(0, Math.floor(Number(value) || 0))
}

function setDraftEquipment(row: AvailableOutboundRow, value: string) {
  const draft = ensureDraft(row)
  draft.equipmentCode = value
  syncOutboundDraftByEquipment(row)
}

function toggleDraftRow(row: AvailableOutboundRow) {
  if (normalizedDraftQty(row) > 0 || normalizedDraftBoxCount(row) > 0) {
    setDraftQty(row, 0)
    setDraftBoxes(row, 0)
    return
  }
  setDraftQty(row, row.availableQty)
  setDraftBoxes(row, 1)
}

function resetCreateFilters() {
  createFilters.partKeyword = ''
  createFilters.warehouseName = ''
  createFilters.zoneName = ''
}

function resetCreateDraft() {
  Object.keys(outboundDraft).forEach((key) => delete outboundDraft[key])
  createMessage.value = ''
}

function resetCreateForm() {
  form.customerId = 0
  resetCreateDraft()
  resetCreateFilters()
}

function formatTime(value: string | null) {
  return formatDateTime(value)
}

async function loadOutboundCandidateBoxes() {
  loadingOutboundBoxes.value = true
  try {
    const records: Kanban[] = []
    const statuses = ['INBOUND', 'PARTIAL_OUTBOUND']
    for (const status of statuses) {
      let nextPage = 1
      let totalPages = 1
      do {
        const result = await api.listKanbansPage({
          status,
          includeChildren: false,
          page: nextPage,
          size: 200,
        })
        records.push(...result.records)
        totalPages = result.totalPages
        nextPage += 1
      } while (nextPage <= totalPages)
    }
    candidateBoxes.value = records
  } finally {
    loadingOutboundBoxes.value = false
  }
}

async function submitCreate() {
  createMessage.value = ''
  const items: OutboundDraftItem[] = plannedRows.value.map(({ row, plannedQty, boxCount, equipmentCode, unitPerBox }) => ({
    partId: row.partId,
    plannedQty,
    boxCount,
    equipmentCode,
    unitPerBox,
    locationCode: row.locationCode,
  }))
  if (!items.length) {
    createMessage.value = '请至少在一条库存行填写本次出库数量和出库箱数。'
    return
  }
  try {
    await props.model.actions.createOutboundOrder({
      customerId: form.customerId || null,
      items,
    })
    resetCreateForm()
    emit('created')
  } catch (error) {
    createMessage.value = error instanceof Error ? error.message : '出库单创建失败'
  }
}

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    resetCreateForm()
    candidateBoxes.value = []
    await loadOutboundCandidateBoxes()
  },
)
</script>

<template>
  <PageModal :open="open" xl @close="emit('close')">
    <section class="panel create-outbound-panel">
      <div class="section-head create-outbound-head">
        <div>
          <h3>创建出库单</h3>
        </div>
        <div class="action-row">
          <button :disabled="!plannedBoxCount" @click="submitCreate">保存出库单</button>
        </div>
      </div>
      <div class="create-outbound-filter-row">
        <select v-model.number="form.customerId">
          <option :value="0">未绑定客户</option>
          <option v-for="item in model.state.customers" :key="item.id" :value="item.id">
            {{ item.customerCode }} | {{ item.customerName }}
          </option>
        </select>
        <input v-model="createFilters.partKeyword" placeholder="搜索零件号 / 名称" />
        <select v-model="createFilters.warehouseName" @change="createFilters.zoneName = ''">
          <option value="">全部仓库</option>
          <option v-for="item in warehouseOptions" :key="item" :value="item">{{ item }}</option>
        </select>
        <select v-model="createFilters.zoneName">
          <option value="">全部库区</option>
          <option v-for="item in zoneOptions" :key="item" :value="item">{{ item }}</option>
        </select>
        <span class="create-inline-summary">可用源箱 {{ availableSourceBoxes }} 箱，本次重包 {{ plannedBoxCount }} 箱，计划 {{ plannedQty.toFixed(3) }}</span>
        <button class="secondary-button" @click="resetCreateDraft">清空计划</button>
      </div>
      <p v-if="loadingOutboundBoxes" class="scan-hint">正在加载可出库箱级看板，请稍候...</p>
      <p v-if="createMessage" class="form-error">{{ createMessage }}</p>

      <div class="table-scroll create-outbound-table-wrap">
        <table class="table create-outbound-table">
          <colgroup>
            <col class="part-col" />
            <col class="location-col" />
            <col class="small-col" />
            <col class="time-col" />
            <col class="source-col" />
            <col class="qty-col" />
            <col class="qty-col" />
            <col class="equipment-col" />
            <col class="box-input-col" />
            <col class="unit-col" />
            <col class="fifo-col" />
            <col class="action-col" />
          </colgroup>
          <thead>
            <tr>
              <th>零件</th>
              <th>库位</th>
              <th>源箱</th>
              <th>最早入库</th>
              <th>追溯入库单</th>
              <th>可选数量</th>
              <th>出库数量</th>
              <th>重包器具</th>
              <th>出库箱数</th>
              <th>每箱数量</th>
              <th>FIFO 预览</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, index) in availableRows"
              :key="row.key"
              class="available-row"
              :class="{ selected: normalizedDraftBoxCount(row) > 0, 'tone-a': index % 2 === 0, 'tone-b': index % 2 === 1 }"
              @click="toggleDraftRow(row)"
            >
              <td>{{ row.partCode }} | {{ row.partName }}</td>
              <td>{{ row.locationCode }} | {{ row.warehouseName }} / {{ row.zoneName }}</td>
              <td>{{ row.availableBoxes }}</td>
              <td>{{ formatTime(row.oldestInboundTime) }}</td>
              <td class="source-cell">{{ row.inboundNos.slice(0, 3).join('，') }}{{ row.inboundNos.length > 3 ? '…' : '' }}</td>
              <td>{{ row.availableQty.toFixed(3) }} {{ row.unit }}</td>
              <td class="box-count-cell">
                <input
                  class="box-count-input"
                  :value="ensureDraft(row).plannedQty ?? ''"
                  type="number"
                  min="0"
                  :max="row.availableQty"
                  step="0.001"
                  placeholder="数量"
                  @click.stop
                  @input="setDraftQty(row, Number(($event.target as HTMLInputElement).value))"
                />
              </td>
              <td class="equipment-cell">
                <select
                  :value="ensureDraft(row).equipmentCode"
                  @click.stop
                  @change="setDraftEquipment(row, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="">手动箱数</option>
                  <option v-for="item in outboundEquipmentOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </option>
                </select>
              </td>
              <td class="box-count-cell">
                <input
                  class="box-count-input"
                  :value="ensureDraft(row).boxCount ?? ''"
                  type="number"
                  min="0"
                  step="1"
                  :readonly="isBoxCountReadonly(row)"
                  placeholder="自动"
                  @click.stop
                  @input="setDraftBoxes(row, Number(($event.target as HTMLInputElement).value))"
                />
              </td>
              <td>{{ draftUnitPerBox(row).toFixed(3) }}</td>
              <td>
                <div class="kanban-chip-cell fifo-preview-cell">
                  <span v-for="box in fifoPreviewBoxes(row)" :key="box.id" class="tag-pill mono">
                    {{ box.kanbanNo }} / 可用 {{ kanbanFreeQty(box).toFixed(3) }}
                  </span>
                </div>
              </td>
              <td class="action-row row-actions" @click.stop>
                <button class="secondary-button" @click="setDraftQty(row, row.availableQty); setDraftBoxes(row, 1)">全量</button>
                <button class="secondary-button" @click="setDraftQty(row, 0); setDraftBoxes(row, 0)">清零</button>
              </td>
            </tr>
            <tr v-if="!availableRows.length">
              <td colspan="12" class="empty-cell">没有符合条件的可出库库存。请确认零件已完整入库，且未被其它出库单锁定。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </PageModal>
</template>

<style scoped>
.create-outbound-panel {
  display: grid;
  gap: 8px;
  padding: 14px;
  min-width: 0;
  max-width: 100%;
}

.create-outbound-head {
  margin-bottom: 0;
}

.create-outbound-head h3 {
  margin: 0;
  white-space: nowrap;
}

.create-outbound-filter-row {
  display: grid;
  grid-template-columns: minmax(170px, 0.9fr) minmax(220px, 1.1fr) minmax(150px, 0.72fr) minmax(150px, 0.72fr) minmax(260px, 1fr) 82px;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.create-outbound-filter-row input,
.create-outbound-filter-row select,
.create-outbound-filter-row button {
  min-width: 0;
  min-height: 34px;
}

.create-inline-summary {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.create-outbound-table-wrap {
  max-height: min(620px, calc(94vh - 150px));
  overflow: auto;
}

.create-outbound-table {
  min-width: 1460px;
  table-layout: fixed;
}

.create-outbound-table th,
.create-outbound-table td {
  padding: 6px 7px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.create-outbound-table .part-col {
  width: 190px;
}

.create-outbound-table .location-col {
  width: 210px;
}

.create-outbound-table .small-col {
  width: 70px;
}

.create-outbound-table .qty-col {
  width: 124px;
}

.create-outbound-table .time-col {
  width: 150px;
}

.create-outbound-table .source-col {
  width: 168px;
}

.create-outbound-table .equipment-col {
  width: 220px;
}

.create-outbound-table .box-input-col {
  width: 128px;
}

.create-outbound-table .unit-col {
  width: 110px;
}

.create-outbound-table .fifo-col {
  width: 230px;
}

.create-outbound-table .action-col {
  width: 112px;
}

.available-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.available-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.available-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.available-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.available-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.box-count-cell {
  min-width: 0;
}

.box-count-input,
.create-outbound-table input,
.create-outbound-table select {
  width: 100%;
  min-width: 0;
  min-height: 30px;
  box-sizing: border-box;
  font-variant-numeric: tabular-nums;
  padding-inline: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kanban-chip-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
}

.fifo-preview-cell {
  max-height: 76px;
  min-width: 0;
  overflow: auto;
}

.fifo-preview-cell .tag-pill {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.row-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.row-actions button {
  min-height: 30px;
  padding: 0 8px;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .create-outbound-filter-row {
    grid-template-columns: 1fr;
  }

  .create-outbound-table-wrap {
    max-height: calc(100vh - 190px);
  }
}
</style>
