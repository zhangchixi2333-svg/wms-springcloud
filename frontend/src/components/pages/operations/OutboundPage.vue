<!-- 本文件实现出库工作台：按零件和箱数创建出库单，后端按 FIFO 自动分配箱级看板，并支持打印后扫码出库。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { formatStatus } from '../../../app/displayText'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import type { Kanban, OutboundDraftItem, OutboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

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

const viewMode = ref<'query' | 'create' | 'manual' | 'print' | 'scan'>('query')
const printOrder = ref<OutboundOrder | null>(null)
const printOrders = ref<OutboundOrder[]>([])
const scanInputRef = ref<HTMLInputElement | null>(null)
const loadingOutboundBoxes = ref(false)
const expandedParents = reactive<Record<number, boolean>>({})
const expandedOrders = reactive<Record<number, boolean>>({})
const selectedOrderIds = reactive<Record<number, boolean>>({})
const outboundDraft = reactive<Record<string, number>>({})
const lastScanMessage = ref('')
const createMessage = ref('')

const filters = reactive({
  status: '',
  customerId: 0,
  outboundNo: '',
})

const createFilters = reactive({
  partKeyword: '',
  warehouseName: '',
  zoneName: '',
})

const scanForm = reactive({
  barcode: '',
  outboundOrderNo: '',
})

const manualForm = reactive({
  partId: 0,
  locationId: 0,
  qty: 1,
  remark: '',
})

const printedOutboundScanForm = reactive({
  barcode: '',
})

const form = reactive({
  customerId: 0,
})

const rows = computed(() =>
  props.model.state.outboundOrders.filter((order) => {
    const statusMatch = !filters.status || order.status === filters.status
    const customerMatch = !filters.customerId || order.customerId === filters.customerId
    const outboundMatch = !filters.outboundNo || order.outboundNo.toLowerCase().includes(filters.outboundNo.toLowerCase())
    return statusMatch && customerMatch && outboundMatch
  }),
)

const outboundOrderOptions = computed(() => props.model.state.outboundOrders.filter((order) => order.status !== 'COMPLETED'))
const activeScanOrder = computed(() => props.model.state.outboundOrders.find((order) => order.outboundNo === scanForm.outboundOrderNo) ?? null)
const selectedOrderCount = computed(() => rows.value.filter((item) => selectedOrderIds[item.id]).length)
const canBatchPrint = computed(() => selectedOrderCount.value > 0)

const availableBoxes = computed(() =>
  props.model.state.kanbans
    .filter((item) => !item.parentKanban)
    .filter((item) => item.status === 'INBOUND')
    .filter((item) => !item.outboundNo || item.outboundNo === '-')
    .filter((item) => item.locationCode && item.locationCode !== '-')
    .filter((item) => isParentReadyForOutbound(item))
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
    row.availableQty += Number(box.qty ?? 0)
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
    .map((row) => ({ row, boxCount: normalizedDraftBoxCount(row) }))
    .filter((item) => item.boxCount > 0),
)

const plannedBoxCount = computed(() => plannedRows.value.reduce((sum, item) => sum + item.boxCount, 0))
const plannedQty = computed(() =>
  plannedRows.value.reduce((sum, item) => sum + item.row.boxes.slice(0, item.boxCount).reduce((boxSum, box) => boxSum + Number(box.qty ?? 0), 0), 0),
)

function compareKanbanFifo(left: Kanban, right: Kanban) {
  return `${left.inboundTime ?? left.createdAt}-${left.parentKanbanId ?? 0}-${left.boxIndex}-${left.id}`
    .localeCompare(`${right.inboundTime ?? right.createdAt}-${right.parentKanbanId ?? 0}-${right.boxIndex}-${right.id}`)
}

function minTime(left: string | null, right: string | null) {
  if (!left) return right
  if (!right) return left
  return left <= right ? left : right
}

function findParentKanban(child: Kanban) {
  if (!child.parentKanbanId) return null
  return props.model.state.kanbans.find((item) => item.id === child.parentKanbanId) ?? null
}

function isParentReadyForOutbound(child: Kanban) {
  const parent = findParentKanban(child)
  if (!parent) return false
  const children = parent.children ?? []
  return children.length > 0 && children.every((item) => !['WAIT_SCAN', 'CREATED', 'PARTIAL', 'PARTIAL_INBOUND'].includes(item.status))
}

async function loadChildrenForParents(parents: Kanban[]) {
  await Promise.all(parents.map((kanban) => props.model.actions.loadKanbanChildren(kanban.id)))
}

async function loadOutboundCandidateBoxes() {
  loadingOutboundBoxes.value = true
  try {
    const parents = props.model.state.kanbans.filter(
      (item) => item.parentKanban && ['INBOUND', 'PARTIAL_OUTBOUND'].includes(item.status),
    )
    await loadChildrenForParents(parents)
  } finally {
    loadingOutboundBoxes.value = false
  }
}

function isPendingOutbound(kanban: Kanban) {
  return ['ALLOCATED', 'INBOUND'].includes(kanban.status)
}

function normalizedDraftBoxCount(row: AvailableOutboundRow) {
  const value = Number(outboundDraft[row.key] ?? 0)
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.min(Math.floor(value), row.availableBoxes)
}

function estimateDraftQty(row: AvailableOutboundRow) {
  return row.boxes.slice(0, normalizedDraftBoxCount(row)).reduce((sum, box) => sum + Number(box.qty ?? 0), 0)
}

function setDraftBoxes(row: AvailableOutboundRow, value: number) {
  outboundDraft[row.key] = Math.max(0, Math.min(Math.floor(Number(value) || 0), row.availableBoxes))
}

function resetCreateDraft() {
  Object.keys(outboundDraft).forEach((key) => delete outboundDraft[key])
  createMessage.value = ''
}

function resetFilters() {
  filters.status = ''
  filters.customerId = 0
  filters.outboundNo = ''
}

function toggleSelectAllOrders(checked: boolean) {
  rows.value.forEach((item) => {
    selectedOrderIds[item.id] = checked
  })
}

function resetCreateFilters() {
  createFilters.partKeyword = ''
  createFilters.warehouseName = ''
  createFilters.zoneName = ''
}

async function openCreate() {
  form.customerId = 0
  resetCreateDraft()
  resetCreateFilters()
  await loadOutboundCandidateBoxes()
  viewMode.value = 'create'
}

async function ensureOutboundOrderChildren(order: OutboundOrder) {
  const parentIds = new Set(
    order.items
      .map((item) => props.model.state.kanbans.find((kanban) => kanban.id === item.kanbanId)?.parentKanbanId)
      .filter((id): id is number => typeof id === 'number'),
  )
  const missingBoxIds = new Set(order.items.map((item) => item.kanbanId).filter((id): id is number => typeof id === 'number'))
  props.model.state.kanbans
    .filter((item) => missingBoxIds.has(item.id) && item.parentKanbanId)
    .forEach((item) => parentIds.add(item.parentKanbanId as number))
  const directParents = props.model.state.kanbans.filter((item) => parentIds.has(item.id))
  if (directParents.length) {
    await loadChildrenForParents(directParents)
  }
}

async function openScan(order: OutboundOrder) {
  await ensureOutboundOrderChildren(order)
  scanForm.outboundOrderNo = order.outboundNo
  scanForm.barcode = ''
  lastScanMessage.value = ''
  viewMode.value = 'scan'
}

async function openPrint(order: OutboundOrder) {
  await ensureOutboundOrderChildren(order)
  printOrder.value = order
  printOrders.value = [order]
  scanForm.outboundOrderNo = order.outboundNo
  printedOutboundScanForm.barcode = ''
  lastScanMessage.value = ''
  viewMode.value = 'print'
}

async function openBatchPrint() {
  printOrders.value = rows.value.filter((item) => selectedOrderIds[item.id])
  await Promise.all(printOrders.value.map(ensureOutboundOrderChildren))
  printOrder.value = printOrders.value[0] ?? null
  scanForm.outboundOrderNo = printOrder.value?.outboundNo ?? ''
  printedOutboundScanForm.barcode = ''
  lastScanMessage.value = ''
  viewMode.value = 'print'
}

function browserPrint() {
  window.print()
}

function sourceText(order: OutboundOrder) {
  return order.inboundOrderNos.length ? order.inboundOrderNos.join('，') : '系统按 FIFO 自动分配'
}

function outboundNoList(value: string | null | undefined) {
  return Array.from(new Set((value ?? '')
    .split(/[,\uFF0C;\uFF1B\s]+/)
    .map((item) => item.trim())
    .filter((item) => item && item !== '-')))
}

function boundBoxesForOrder(order: OutboundOrder) {
  const ids = new Set(order.items.map((item) => item.kanbanId).filter((id): id is number => typeof id === 'number'))
  return props.model.state.kanbans
    .filter((item) => ids.has(item.id))
    .sort(compareKanbanFifo)
}

function parentKanbansForOutbound(order: OutboundOrder) {
  const boxes = boundBoxesForOrder(order)
  const parentIds = new Set(boxes.map((item) => item.parentKanbanId).filter((id): id is number => typeof id === 'number'))
  return props.model.state.kanbans
    .filter((item) => parentIds.has(item.id))
    .map((parent) => ({
      ...parent,
      children: (parent.children ?? []).filter((child) => boxes.some((box) => box.id === child.id)),
    }))
    .sort((a, b) => a.kanbanNo.localeCompare(b.kanbanNo))
}

function outboundScanOptionsForOrder(order: OutboundOrder) {
  const parentOptions = parentKanbansForOutbound(order)
    .filter((parent) => (parent.children ?? []).some(isPendingOutbound))
    .map((item) => ({
      label: `父看板 ${item.kanbanNo} | 待出库 ${(item.children ?? []).filter(isPendingOutbound).length} 箱`,
      value: item.qrContent || item.barcode,
    }))
  const childOptions = boundBoxesForOrder(order)
    .filter(isPendingOutbound)
    .map((item) => ({
      label: `箱 ${item.kanbanNo} | ${item.partCode} | ${item.qty}`,
      value: item.qrContent || item.barcode,
    }))
  return [...parentOptions, ...childOptions]
}

function groupedOrderItems(order: OutboundOrder) {
  const groups = new Map<string, { key: string; partText: string; locationText: string; boxes: number; qty: number; scannedQty: number; kanbanNos: string[] }>()
  order.items.forEach((item) => {
    const key = `${item.partId}:${item.warehouseName}:${item.zoneName}`
    const group = groups.get(key) ?? {
      key,
      partText: `${item.partCode} | ${item.partName}`,
      locationText: `${item.warehouseName} / ${item.zoneName}`,
      boxes: 0,
      qty: 0,
      scannedQty: 0,
      kanbanNos: [],
    }
    group.boxes += 1
    group.qty += Number(item.plannedQty ?? 0)
    group.scannedQty += Number(item.scannedQty ?? 0)
    if (item.kanbanNo) group.kanbanNos.push(item.kanbanNo)
    groups.set(key, group)
  })
  return Array.from(groups.values())
}

function handleScanOrderChange() {
  scanForm.barcode = ''
  printedOutboundScanForm.barcode = ''
  lastScanMessage.value = ''
}

async function focusScanInput() {
  await nextTick()
  scanInputRef.value?.focus()
  scanInputRef.value?.select()
}

function fillFirstOutboundScanCode() {
  const first = activeScanOrder.value ? outboundScanOptionsForOrder(activeScanOrder.value)[0]?.value : ''
  if (first) {
    scanForm.barcode = first
  }
}

async function submitScan() {
  const code = scanForm.barcode
  const result = await props.model.actions.scanOutbound(scanForm)
  lastScanMessage.value = result?.message || `扫码成功，已执行出库：${code}`
  scanForm.barcode = ''
  await focusScanInput()
}

async function submitScanByEnter() {
  if (!scanForm.barcode || !scanForm.outboundOrderNo) return
  await submitScan()
}

async function submitCreate() {
  createMessage.value = ''
  const items: OutboundDraftItem[] = plannedRows.value.map(({ row, boxCount }) => ({
    partId: row.partId,
    boxCount,
    locationCode: row.locationCode,
  }))
  if (!items.length) {
    createMessage.value = '请至少在一条库存行填写本次出库箱数。'
    return
  }
  await props.model.actions.createOutboundOrder({
    customerId: form.customerId || null,
    items,
  })
  resetCreateDraft()
  viewMode.value = 'query'
}

async function submitManual() {
  await props.model.actions.manualInventoryEntry(manualForm)
  manualForm.partId = 0
  manualForm.locationId = 0
  manualForm.qty = 1
  manualForm.remark = ''
  viewMode.value = 'query'
}

async function submitPrintedOutboundScan() {
  const activePrintOrder = props.model.state.outboundOrders.find((order) => order.outboundNo === scanForm.outboundOrderNo) ?? printOrder.value
  if (!activePrintOrder) return
  const code = printedOutboundScanForm.barcode
  const result = await props.model.actions.scanOutbound({
    barcode: code,
    outboundOrderNo: activePrintOrder.outboundNo,
  })
  lastScanMessage.value = result?.message || `扫码成功，已执行出库：${code}`
  printedOutboundScanForm.barcode = ''
  await focusScanInput()
}

async function submitPrintedOutboundScanByEnter() {
  if (!printedOutboundScanForm.barcode || !scanForm.outboundOrderNo) return
  await submitPrintedOutboundScan()
}

async function toggleExpanded(kanbanId: number) {
  const nextExpanded = !expandedParents[kanbanId]
  expandedParents[kanbanId] = nextExpanded
  if (nextExpanded) {
    await props.model.actions.loadKanbanChildren(kanbanId)
  }
}

function toggleOrderExpanded(orderId: number) {
  expandedOrders[orderId] = !expandedOrders[orderId]
}

async function simulatePrintScan(kanban: Kanban) {
  if (!printOrder.value && !scanForm.outboundOrderNo) return
  printedOutboundScanForm.barcode = kanban.qrContent || kanban.barcode
  await submitPrintedOutboundScan()
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

watch(viewMode, async (value) => {
  if (value === 'scan' || value === 'print') {
    await focusScanInput()
  }
})
</script>

<template>
  <section class="stack">
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>出库筛选</h3>
            <p>出库单按零件和箱数创建，系统按先进先出自动分配可出库箱级看板。</p>
          </div>
          <div class="action-row">
            <button @click="openCreate">创建出库单</button>
            <button :disabled="!canBatchPrint" @click="openBatchPrint">批量打印</button>
            <button class="secondary-button" @click="viewMode = 'manual'">手工入账</button>
          </div>
        </div>
        <div class="form-grid four">
          <select v-model="filters.status">
            <option value="">全部状态</option>
            <option value="CREATED">{{ formatStatus('CREATED') }}</option>
            <option value="PARTIAL">{{ formatStatus('PARTIAL') }}</option>
            <option value="COMPLETED">{{ formatStatus('COMPLETED') }}</option>
          </select>
          <select v-model.number="filters.customerId">
            <option :value="0">全部客户</option>
            <option v-for="item in model.state.customers" :key="item.id" :value="item.id">
              {{ item.customerCode }} | {{ item.customerName }}
            </option>
          </select>
          <input v-model="filters.outboundNo" placeholder="出库单号" />
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel table-scroll">
        <table class="table order-table">
          <thead>
            <tr>
              <th>
                <input
                  type="checkbox"
                  :checked="selectedOrderCount === rows.length && rows.length > 0"
                  @change="toggleSelectAllOrders(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th>出库单号</th>
              <th>客户</th>
              <th>FIFO 来源</th>
              <th>状态</th>
              <th>箱数</th>
              <th>数量</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="order in rows" :key="order.id">
              <tr>
                <td><input v-model="selectedOrderIds[order.id]" type="checkbox" /></td>
                <td class="mono">{{ order.outboundNo }}</td>
                <td>{{ order.customerName }}</td>
                <td class="source-cell">{{ sourceText(order) }}</td>
                <td>{{ formatStatus(order.status) }}</td>
                <td>{{ order.items.length }}</td>
                <td>{{ order.items.reduce((sum, item) => sum + Number(item.plannedQty), 0).toFixed(3) }}</td>
                <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                <td class="action-row">
                  <button class="secondary-button" @click="toggleOrderExpanded(order.id)">
                    {{ expandedOrders[order.id] ? '收起' : '明细' }}
                  </button>
                  <button class="secondary-button" @click="openPrint(order)">打印</button>
                  <button class="secondary-button" @click="openScan(order)">扫码</button>
                </td>
              </tr>
              <tr v-if="expandedOrders[order.id]" class="order-detail-row">
                <td colspan="9">
                  <table class="table detail-table">
                    <thead>
                      <tr>
                        <th>零件</th>
                        <th>仓库 / 库区</th>
                        <th>箱数</th>
                        <th>计划数量</th>
                        <th>已扫数量</th>
                        <th>分配看板</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="group in groupedOrderItems(order)" :key="group.key">
                        <td>{{ group.partText }}</td>
                        <td>{{ group.locationText }}</td>
                        <td>{{ group.boxes }}</td>
                        <td>{{ group.qty.toFixed(3) }}</td>
                        <td>{{ group.scannedQty.toFixed(3) }}</td>
                        <td class="kanban-chip-cell">
                          <span v-for="no in group.kanbanNos" :key="no" class="tag-pill mono">{{ no }}</span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="viewMode === 'create'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>创建出库单</h3>
            <p>只填写要出库的零件和箱数；保存后系统按入库时间、父看板、箱号顺序自动锁定具体箱级看板。</p>
          </div>
          <div class="action-row">
            <button :disabled="!plannedBoxCount" @click="submitCreate">保存出库单</button>
            <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
          </div>
        </div>
        <div class="form-grid four">
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
        </div>
        <div class="summary-strip">
          <span>可出库 {{ availableRows.reduce((sum, row) => sum + row.availableBoxes, 0) }} 箱</span>
          <span>本次计划 {{ plannedBoxCount }} 箱</span>
          <span>预计数量 {{ plannedQty.toFixed(3) }}</span>
          <button class="secondary-button" @click="resetCreateDraft">清空计划</button>
        </div>
        <p v-if="loadingOutboundBoxes" class="scan-hint">正在加载可出库箱级看板，请稍候...</p>
        <p v-if="createMessage" class="form-error">{{ createMessage }}</p>
      </section>

      <section class="panel table-scroll">
        <table class="table available-table">
          <colgroup>
            <col class="part-col" />
            <col class="location-col" />
            <col class="small-col" />
            <col class="qty-col" />
            <col class="time-col" />
            <col class="source-col" />
            <col class="box-input-col" />
            <col class="qty-col" />
            <col class="fifo-col" />
            <col class="action-col" />
          </colgroup>
          <thead>
            <tr>
              <th>零件</th>
              <th>库位</th>
              <th>可出库箱数</th>
              <th>可出库数量</th>
              <th>最早入库</th>
              <th>追溯入库单</th>
              <th>本次出库箱数</th>
              <th>预计数量</th>
              <th>FIFO 预览</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in availableRows" :key="row.key">
              <td>{{ row.partCode }} | {{ row.partName }}</td>
              <td>{{ row.locationCode }} | {{ row.warehouseName }} / {{ row.zoneName }}</td>
              <td>{{ row.availableBoxes }}</td>
              <td>{{ row.availableQty.toFixed(3) }} {{ row.unit }}</td>
              <td>{{ formatTime(row.oldestInboundTime) }}</td>
              <td class="source-cell">{{ row.inboundNos.slice(0, 3).join('，') }}{{ row.inboundNos.length > 3 ? '…' : '' }}</td>
              <td class="box-count-cell">
                <input
                  class="box-count-input"
                  :value="outboundDraft[row.key] ?? ''"
                  type="number"
                  min="0"
                  :max="row.availableBoxes"
                  step="1"
                  placeholder="箱数"
                  @input="setDraftBoxes(row, Number(($event.target as HTMLInputElement).value))"
                />
              </td>
              <td>{{ estimateDraftQty(row).toFixed(3) }}</td>
              <td>
                <div class="kanban-chip-cell fifo-preview-cell">
                  <span v-for="box in row.boxes.slice(0, Math.max(1, normalizedDraftBoxCount(row)))" :key="box.id" class="tag-pill mono">
                    {{ box.kanbanNo }}
                  </span>
                </div>
              </td>
              <td class="action-row">
                <button class="secondary-button" @click="setDraftBoxes(row, row.availableBoxes)">全选</button>
                <button class="secondary-button" @click="setDraftBoxes(row, 0)">清零</button>
              </td>
            </tr>
            <tr v-if="!availableRows.length">
              <td colspan="10" class="empty-cell">没有符合条件的可出库库存。请确认零件已完整入库，且未被其它出库单锁定。</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="viewMode === 'scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码出库：{{ scanForm.outboundOrderNo }}</h3>
          <p>可扫父看板或箱级子看板；父看板只处理当前出库单已分配且未出库的箱子。</p>
        </div>
        <div class="action-row">
          <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
        </div>
      </div>
      <div class="form-grid three">
        <select v-model="scanForm.outboundOrderNo" @change="handleScanOrderChange">
          <option value="">选择出库单</option>
          <option v-for="order in outboundOrderOptions" :key="order.id" :value="order.outboundNo">
            {{ order.outboundNo }} | {{ order.items.length }} 箱 | {{ formatStatus(order.status) }}
          </option>
        </select>
        <input
          ref="scanInputRef"
          v-model="scanForm.barcode"
          placeholder="扫描二维码内容或条码"
          :disabled="!scanForm.outboundOrderNo"
          @keydown.enter.prevent="submitScanByEnter"
        />
        <button :disabled="!scanForm.outboundOrderNo || !scanForm.barcode" @click="submitScan">确认出库</button>
      </div>
      <div class="scan-assist-row two-col">
        <select v-model="scanForm.barcode" :disabled="!scanForm.outboundOrderNo">
          <option value="">辅助选择待出库看板</option>
          <option v-for="item in activeScanOrder ? outboundScanOptionsForOrder(activeScanOrder) : []" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <button class="secondary-button" :disabled="!scanForm.outboundOrderNo" @click="fillFirstOutboundScanCode">填充首个待出库码</button>
      </div>
      <p v-if="lastScanMessage" class="scan-hint success-text">{{ lastScanMessage }}</p>
    </section>

    <section v-else-if="viewMode === 'manual'" class="panel">
      <div class="section-head">
        <div>
          <h3>手工入账</h3>
          <p>用于补录库存调整，不替代正常扫码出入库流程。</p>
        </div>
      </div>
      <div class="form-grid four">
        <select v-model.number="manualForm.partId">
          <option :value="0">选择零件</option>
          <option v-for="part in model.state.parts" :key="part.id" :value="part.id">
            {{ part.partCode }} | {{ part.partName }}
          </option>
        </select>
        <select v-model.number="manualForm.locationId">
          <option :value="0">选择库位</option>
          <option v-for="item in model.state.locations" :key="item.id" :value="item.id">
            {{ item.locationCode }} | {{ item.warehouseName }} / {{ item.zoneName }}
          </option>
        </select>
        <input v-model.number="manualForm.qty" type="number" step="0.001" placeholder="数量" />
        <input v-model="manualForm.remark" placeholder="备注" />
      </div>
      <div class="footer-actions">
        <button @click="submitManual">保存手工入账</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>

    <section v-else-if="printOrders.length" class="panel print-panel">
      <div class="section-head print-toolbar">
        <div>
          <h3>出库打印</h3>
          <p>打印页展示出库单和系统分配的父子看板，打印后可直接扫码执行出库。</p>
        </div>
        <div class="action-row">
          <button @click="browserPrint">浏览器打印</button>
          <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
        </div>
      </div>

      <section class="panel compact-scan-panel">
        <div class="form-grid three">
          <select v-model="scanForm.outboundOrderNo" @change="handleScanOrderChange">
            <option value="">选择当前扫码出库单</option>
            <option v-for="order in printOrders" :key="order.id" :value="order.outboundNo">
              {{ order.outboundNo }} | {{ order.items.length }} 箱 | {{ formatStatus(order.status) }}
            </option>
          </select>
          <input
            ref="scanInputRef"
            v-model="printedOutboundScanForm.barcode"
            placeholder="打印后可直接模拟扫码出库"
            :disabled="!scanForm.outboundOrderNo"
            @keydown.enter.prevent="submitPrintedOutboundScanByEnter"
          />
          <button :disabled="!scanForm.outboundOrderNo || !printedOutboundScanForm.barcode" @click="submitPrintedOutboundScan">执行出库</button>
        </div>
        <p v-if="lastScanMessage" class="scan-hint success-text">{{ lastScanMessage }}</p>
      </section>

      <div class="print-grid">
        <article v-for="order in printOrders" :key="order.id" class="print-order-card">
          <header class="order-header">
            <div>
              <h4>{{ order.outboundNo }}</h4>
              <p>客户：{{ order.customerName || '未绑定客户' }} | 状态：{{ formatStatus(order.status) }}</p>
              <p>FIFO 来源：{{ sourceText(order) }}</p>
            </div>
          </header>

          <table class="table print-summary-table">
            <thead>
              <tr>
                <th>零件</th>
                <th>仓库 / 库区</th>
                <th>箱数</th>
                <th>数量</th>
                <th>已扫</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="group in groupedOrderItems(order)" :key="group.key">
                <td>{{ group.partText }}</td>
                <td>{{ group.locationText }}</td>
                <td>{{ group.boxes }}</td>
                <td>{{ group.qty.toFixed(3) }}</td>
                <td>{{ group.scannedQty.toFixed(3) }}</td>
              </tr>
            </tbody>
          </table>

          <div v-for="kanban in parentKanbansForOutbound(order)" :key="kanban.id" class="kanban-card">
            <div class="kanban-main">
              <div class="kanban-meta">
                <strong>{{ kanban.kanbanNo }}</strong>
                <span>{{ kanban.partCode }} | {{ kanban.partName }}</span>
                <span>本单分配 {{ (kanban.children ?? []).length }} 箱</span>
                <span>
                  出库单
                  <span v-if="outboundNoList(kanban.outboundNo).length" class="tag-list inline-tags">
                    <span v-for="no in outboundNoList(kanban.outboundNo)" :key="no" class="tag-pill">{{ no }}</span>
                  </span>
                  <span v-else>-</span>
                </span>
                <span>{{ kanban.warehouseName }} / {{ kanban.zoneName }}</span>
                <span>状态 {{ formatStatus(kanban.status) }}</span>
              </div>
              <div class="kanban-code">
                <QrCodeImage :text="kanban.qrContent || kanban.barcode" :size="118" />
                <p class="mono">{{ kanban.barcode }}</p>
              </div>
            </div>
            <div class="kanban-actions">
              <button class="secondary-button" @click="toggleExpanded(kanban.id)">
                {{ expandedParents[kanban.id] ? '收起子看板' : `展开子看板(${(kanban.children ?? []).length})` }}
              </button>
              <button :disabled="!(kanban.children ?? []).some(isPendingOutbound)" @click="scanForm.outboundOrderNo = order.outboundNo; simulatePrintScan(kanban)">
                模拟扫父看板出库
              </button>
            </div>
            <div v-if="expandedParents[kanban.id]" class="table-scroll child-table-wrap">
              <table class="table child-kanban-table">
                <thead>
                  <tr>
                    <th>二维码</th>
                    <th>子看板</th>
                    <th>箱号</th>
                    <th>数量</th>
                    <th>状态</th>
                    <th>入库时间</th>
                    <th>出库时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="child in kanban.children ?? []" :key="child.id">
                    <td class="qr-mini"><QrCodeImage :text="child.qrContent || child.barcode" :size="64" /></td>
                    <td class="mono">{{ child.kanbanNo }}</td>
                    <td>第 {{ child.boxIndex }} 箱</td>
                    <td>{{ child.qty }}</td>
                    <td>{{ formatStatus(child.status) }}</td>
                    <td>{{ formatTime(child.inboundTime) }}</td>
                    <td>{{ formatTime(child.outboundTime) }}</td>
                    <td>
                      <button class="secondary-button" :disabled="!isPendingOutbound(child)" @click="scanForm.outboundOrderNo = order.outboundNo; simulatePrintScan(child)">
                        扫本箱
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.summary-strip,
.scan-assist-row,
.kanban-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-strip {
  margin-top: 12px;
}

.print-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.print-order-card,
.kanban-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
}

.two-col {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  margin-top: 12px;
}

.scan-hint {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.success-text {
  color: #15803d;
}

.table-scroll {
  overflow-x: auto;
}

.order-table,
.available-table,
.detail-table,
.child-kanban-table,
.print-summary-table {
  min-width: 980px;
}

.available-table {
  min-width: 1280px;
  table-layout: fixed;
}

.available-table .part-col {
  width: 190px;
}

.available-table .location-col {
  width: 210px;
}

.available-table .small-col {
  width: 92px;
}

.available-table .qty-col {
  width: 118px;
}

.available-table .time-col {
  width: 160px;
}

.available-table .source-col {
  width: 180px;
}

.available-table .box-input-col {
  width: 136px;
}

.available-table .fifo-col {
  width: 260px;
}

.available-table .action-col {
  width: 118px;
}

.box-count-cell {
  min-width: 136px;
}

.box-count-input {
  width: 100%;
  min-width: 96px;
  box-sizing: border-box;
  font-variant-numeric: tabular-nums;
}

.order-detail-row > td {
  background: rgba(15, 23, 42, 0.03);
  padding: 12px;
}

.kanban-chip-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 180px;
}

.fifo-preview-cell {
  max-height: 76px;
  min-width: 0;
  overflow: auto;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

.form-error {
  margin: 10px 0 0;
  color: #dc2626;
}

.tag-list {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
}

.inline-tags {
  margin-left: 4px;
  vertical-align: middle;
}

.tag-pill {
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 2px 8px;
  background: rgba(148, 163, 184, 0.12);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.kanban-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 12px;
}

.kanban-meta,
.order-header {
  display: grid;
  gap: 4px;
}

.kanban-code,
.qr-mini {
  justify-items: center;
}

.child-table-wrap {
  margin-top: 12px;
}

.qr-mini {
  text-align: center;
}

@media (max-width: 1100px) {
  .kanban-main,
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
