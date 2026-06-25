<!-- 本文件实现箱级出库工作台，出库单只能绑定已完整入库父看板下的箱级子看板。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { formatStatus } from '../../../app/displayText'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import type { Kanban, OutboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const viewMode = ref<'query' | 'create' | 'manual' | 'print' | 'scan'>('query')
const printOrder = ref<OutboundOrder | null>(null)
const printOrders = ref<OutboundOrder[]>([])
const scanInputRef = ref<HTMLInputElement | null>(null)
const expandedParents = reactive<Record<number, boolean>>({})
const selectedKanbans = reactive<Record<number, boolean>>({})
const selectedOrderIds = reactive<Record<number, boolean>>({})
const lastScanMessage = ref('')
const createMessage = ref('')

const filters = reactive({
  status: '',
  customerId: 0,
  outboundNo: '',
})

const createFilters = reactive({
  partCode: '',
  inboundNo: '',
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
const selectedKanbanIds = computed(() => Object.entries(selectedKanbans).filter(([, checked]) => checked).map(([id]) => Number(id)))
const selectedBoxes = computed(() => outboundSelectableBoxes.value.filter((item) => selectedKanbans[item.id]))
const selectedQty = computed(() => selectedBoxes.value.reduce((sum, item) => sum + Number(item.qty ?? 0), 0))
const selectedParentGroups = computed(() => {
  const parentMap = new Map<number, { parent: Kanban; children: Kanban[] }>()
  selectedBoxes.value.forEach((child) => {
    const parent = findParentKanban(child)
    if (!parent) return
    const group = parentMap.get(parent.id) ?? { parent, children: [] }
    group.children.push(child)
    parentMap.set(parent.id, group)
  })
  return Array.from(parentMap.values())
})

const warehouseOptions = computed(() => Array.from(new Set(outboundSelectableBoxes.value.map((item) => item.warehouseName).filter(Boolean))).sort())
const zoneOptions = computed(() =>
  Array.from(new Set(outboundSelectableBoxes.value
    .filter((item) => !createFilters.warehouseName || item.warehouseName === createFilters.warehouseName)
    .map((item) => item.zoneName)
    .filter(Boolean))).sort(),
)

const outboundSelectableBoxes = computed(() =>
  props.model.state.kanbans
    .filter((item) => !item.parentKanban)
    .filter((item) => item.status === 'INBOUND')
    .filter((item) => !item.outboundNo || item.outboundNo === '-')
    .filter((item) => isParentFullyInbound(item))
    .sort((a, b) => `${a.inboundTime ?? a.createdAt}-${a.boxIndex}`.localeCompare(`${b.inboundTime ?? b.createdAt}-${b.boxIndex}`)),
)

const filteredSelectableBoxes = computed(() =>
  outboundSelectableBoxes.value.filter((item) => {
    const partMatch = !createFilters.partCode || `${item.partCode} ${item.partName}`.toLowerCase().includes(createFilters.partCode.toLowerCase())
    const inboundMatch = !createFilters.inboundNo || item.inboundNo.toLowerCase().includes(createFilters.inboundNo.toLowerCase())
    const warehouseMatch = !createFilters.warehouseName || item.warehouseName === createFilters.warehouseName
    const zoneMatch = !createFilters.zoneName || item.zoneName === createFilters.zoneName
    return partMatch && inboundMatch && warehouseMatch && zoneMatch
  }),
)

const groupedSelectableParents = computed(() => {
  const parentMap = new Map<number, { parent: Kanban; children: Kanban[] }>()
  filteredSelectableBoxes.value.forEach((child) => {
    const parent = findParentKanban(child)
    if (!parent) return
    const group = parentMap.get(parent.id) ?? { parent, children: [] }
    group.children.push(child)
    parentMap.set(parent.id, group)
  })
  return Array.from(parentMap.values())
})

function isParentFullyInbound(child: Kanban) {
  const parent = findParentKanban(child)
  if (!parent || parent.status !== 'INBOUND') return false
  const children = parent.children ?? []
  return children.length > 0 && children.every((item) => item.status === 'INBOUND')
}

function findParentKanban(child: Kanban) {
  if (!child.parentKanbanId) return null
  return props.model.state.kanbans.find((item) => item.id === child.parentKanbanId) ?? null
}

function selectedCountForChildren(children: Kanban[]) {
  return children.filter((child) => selectedKanbans[child.id]).length
}

function allListedChildrenSelected(children: Kanban[]) {
  return children.length > 0 && children.every((child) => selectedKanbans[child.id])
}

function toggleListedChildrenSelection(children: Kanban[], checked: boolean) {
  children.forEach((child) => {
    selectedKanbans[child.id] = checked
  })
}

function toggleFilteredSelection(checked: boolean) {
  filteredSelectableBoxes.value.forEach((item) => {
    selectedKanbans[item.id] = checked
  })
}

function resetSelection() {
  Object.keys(selectedKanbans).forEach((key) => delete selectedKanbans[Number(key)])
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
  createFilters.partCode = ''
  createFilters.inboundNo = ''
  createFilters.warehouseName = ''
  createFilters.zoneName = ''
}

function openCreate() {
  form.customerId = 0
  resetSelection()
  resetCreateFilters()
  createMessage.value = ''
  viewMode.value = 'create'
}

function openScan(order: OutboundOrder) {
  scanForm.outboundOrderNo = order.outboundNo
  scanForm.barcode = ''
  lastScanMessage.value = ''
  viewMode.value = 'scan'
}

function openPrint(order: OutboundOrder) {
  printOrder.value = order
  printOrders.value = [order]
  scanForm.outboundOrderNo = order.outboundNo
  printedOutboundScanForm.barcode = ''
  lastScanMessage.value = ''
  viewMode.value = 'print'
}

function openBatchPrint() {
  printOrders.value = rows.value.filter((item) => selectedOrderIds[item.id])
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
  return order.inboundOrderNos.length ? order.inboundOrderNos.join('，') : '-'
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
    .sort((a, b) => a.kanbanNo.localeCompare(b.kanbanNo))
}

function parentKanbansForOutbound(order: OutboundOrder) {
  const parentIds = new Set(boundBoxesForOrder(order).map((item) => item.parentKanbanId).filter((id): id is number => typeof id === 'number'))
  return props.model.state.kanbans
    .filter((item) => parentIds.has(item.id))
    .map((parent) => ({
      ...parent,
      children: (parent.children ?? []).filter((child) => boundBoxesForOrder(order).some((box) => box.id === child.id)),
    }))
    .sort((a, b) => a.kanbanNo.localeCompare(b.kanbanNo))
}

function outboundScanOptionsForOrder(order: OutboundOrder) {
  const parentOptions = parentKanbansForOutbound(order)
    .filter((parent) => (parent.children ?? []).some((child) => child.status === 'INBOUND'))
    .map((item) => ({
      label: `父看板 ${item.kanbanNo} | 待出库 ${(item.children ?? []).filter((child) => child.status === 'INBOUND').length} 箱`,
      value: item.qrContent || item.barcode,
    }))
  const childOptions = boundBoxesForOrder(order)
    .filter((item) => item.status === 'INBOUND')
    .map((item) => ({
      label: `箱 ${item.kanbanNo} | ${item.partCode} | ${item.qty}`,
      value: item.qrContent || item.barcode,
    }))
  return [...parentOptions, ...childOptions]
}

function handleScanOrderChange() {
  scanForm.barcode = ''
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
  await props.model.actions.scanOutbound(scanForm)
  lastScanMessage.value = `扫码成功，已执行出库：${code}`
  scanForm.barcode = ''
  await focusScanInput()
}

async function submitScanByEnter() {
  if (!scanForm.barcode || !scanForm.outboundOrderNo) return
  await submitScan()
}

async function submitCreate() {
  createMessage.value = ''
  if (!selectedKanbanIds.value.length) {
    createMessage.value = '请先在下方表格勾选要绑定到出库单的箱级看板。'
    return
  }
  await props.model.actions.createOutboundOrder({
    customerId: form.customerId || null,
    kanbanIds: selectedKanbanIds.value,
  })
  resetSelection()
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
  await props.model.actions.scanOutbound({
    barcode: printedOutboundScanForm.barcode,
    outboundOrderNo: activePrintOrder.outboundNo,
  })
  lastScanMessage.value = `扫码成功，已执行出库：${code}`
  printedOutboundScanForm.barcode = ''
  await focusScanInput()
}

async function submitPrintedOutboundScanByEnter() {
  if (!printedOutboundScanForm.barcode || !scanForm.outboundOrderNo) return
  await submitPrintedOutboundScan()
}

function toggleExpanded(kanbanId: number) {
  expandedParents[kanbanId] = !expandedParents[kanbanId]
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
            <p>先创建出库单并绑定箱级看板，再打印或扫码执行出库。</p>
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

      <section class="panel">
        <table class="table">
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
              <th>来源入库单</th>
              <th>状态</th>
              <th>箱数</th>
              <th>数量</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in rows" :key="order.id">
              <td><input v-model="selectedOrderIds[order.id]" type="checkbox" /></td>
              <td>{{ order.outboundNo }}</td>
              <td>{{ order.customerName }}</td>
              <td class="source-cell">{{ sourceText(order) }}</td>
              <td>{{ formatStatus(order.status) }}</td>
              <td>{{ order.items.length }}</td>
              <td>{{ order.items.reduce((sum, item) => sum + Number(item.plannedQty), 0) }}</td>
              <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
              <td class="action-row">
                <button class="secondary-button" @click="openPrint(order)">打印</button>
                <button class="secondary-button" @click="openScan(order)">扫码</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="viewMode === 'create'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>创建出库单</h3>
            <p>只能选择已完整入库父看板下的箱级子看板，系统会按箱自动生成出库明细。</p>
          </div>
          <div class="action-row">
            <button :disabled="!selectedKanbanIds.length" @click="submitCreate">保存出库单</button>
            <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
          </div>
        </div>
        <div class="form-grid five">
          <select v-model.number="form.customerId">
            <option :value="0">未绑定客户</option>
            <option v-for="item in model.state.customers" :key="item.id" :value="item.id">
              {{ item.customerCode }} | {{ item.customerName }}
            </option>
          </select>
          <input v-model="createFilters.partCode" placeholder="零件号 / 名称" />
          <input v-model="createFilters.inboundNo" placeholder="入库单号" />
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
          <span>可选 {{ filteredSelectableBoxes.length }} 箱</span>
          <span>已选 {{ selectedKanbanIds.length }} 箱</span>
          <span>合计数量 {{ selectedQty.toFixed(3) }}</span>
          <button class="secondary-button" @click="toggleFilteredSelection(true)">选择筛选结果</button>
          <button class="secondary-button" @click="resetSelection">清空选择</button>
        </div>
        <p v-if="createMessage" class="form-error">{{ createMessage }}</p>
      </section>

      <section v-if="selectedBoxes.length" class="panel table-scroll">
        <div class="section-head">
          <div>
            <h3>已绑定看板明细</h3>
            <p>保存出库单时，下面这些箱级子看板会写入出库单明细，并锁定到本次出库。</p>
          </div>
        </div>
        <table class="table selected-kanban-table">
          <thead>
            <tr>
              <th>父看板</th>
              <th>子看板</th>
              <th>箱号</th>
              <th>零件</th>
              <th>数量</th>
              <th>来源入库单</th>
              <th>仓库 / 库区</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in selectedParentGroups" :key="group.parent.id">
              <tr class="parent-group-row">
                <td colspan="8">
                  <strong>{{ group.parent.kanbanNo }}</strong>
                  <span class="subtle-text"> | {{ group.parent.partCode }} | {{ group.parent.partName }} | 本次绑定 {{ group.children.length }} 箱</span>
                </td>
              </tr>
              <tr v-for="child in group.children" :key="child.id">
                <td>{{ group.parent.kanbanNo }}</td>
                <td class="mono">{{ child.kanbanNo }}</td>
                <td>第 {{ child.boxIndex }} 箱</td>
                <td>{{ child.partCode }} | {{ child.partName }}</td>
                <td>{{ child.qty }}</td>
                <td>{{ child.inboundNo }}</td>
                <td>{{ child.warehouseName }} / {{ child.zoneName }}</td>
                <td>
                  <button class="secondary-button" @click="selectedKanbans[child.id] = false">取消绑定</button>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </section>

      <section class="panel table-scroll">
        <div class="section-head">
          <div>
            <h3>可绑定箱级看板</h3>
            <p>只能绑定状态为“已入库”、未被其它出库单占用，并且父看板已完整入库的箱级子看板。</p>
          </div>
        </div>
        <table class="table selectable-box-table">
          <thead>
            <tr>
              <th>选择</th>
              <th>父看板</th>
              <th>子看板</th>
              <th>箱号</th>
              <th>零件</th>
              <th>数量</th>
              <th>来源入库单</th>
              <th>仓库 / 库区</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="group in groupedSelectableParents" :key="group.parent.id">
              <tr class="parent-group-row">
                <td>
                  <input
                    type="checkbox"
                    :checked="allListedChildrenSelected(group.children)"
                    @change="toggleListedChildrenSelection(group.children, ($event.target as HTMLInputElement).checked)"
                  />
                </td>
                <td colspan="2">
                  <strong>{{ group.parent.kanbanNo }}</strong>
                </td>
                <td colspan="2">{{ group.parent.partCode }} | {{ group.parent.partName }}</td>
                <td>已选 {{ selectedCountForChildren(group.children) }} / {{ group.children.length }} 箱</td>
                <td>{{ group.parent.inboundNo }}</td>
                <td>{{ group.parent.warehouseName }} / {{ group.parent.zoneName }}</td>
                <td>{{ formatStatus(group.parent.status) }}</td>
              </tr>
              <tr v-for="child in group.children" :key="child.id" :class="{ selected: selectedKanbans[child.id] }">
                <td>
                  <input v-model="selectedKanbans[child.id]" type="checkbox" />
                </td>
                <td>{{ group.parent.kanbanNo }}</td>
                <td class="mono">{{ child.kanbanNo }}</td>
                <td>第 {{ child.boxIndex }} 箱</td>
                <td>{{ child.partCode }} | {{ child.partName }}</td>
                <td>{{ child.qty }}</td>
                <td>{{ child.inboundNo }}</td>
                <td>{{ child.warehouseName }} / {{ child.zoneName }}</td>
                <td>{{ formatStatus(child.status) }}</td>
              </tr>
            </template>
            <tr v-if="!groupedSelectableParents.length">
              <td colspan="9" class="empty-cell">没有符合条件且可出库的箱级看板</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="viewMode === 'scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码出库：{{ scanForm.outboundOrderNo }}</h3>
          <p>先选出库单，再扫绑定的父看板或箱级子看板；父看板只处理该出库单绑定且未出库的箱子。</p>
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
      </div>
    </section>

    <section v-else-if="printOrders.length" class="panel print-panel">
      <div class="section-head print-toolbar">
        <div>
          <h3>出库打印</h3>
          <p>打印页展示已选择的出库单和绑定箱级看板，可选择当前出库单后模拟扫码出库。</p>
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
              <p>来源入库单：{{ sourceText(order) }}</p>
              <p>客户：{{ order.customerName || '未绑定客户' }} | 状态：{{ formatStatus(order.status) }}</p>
            </div>
          </header>

          <div v-for="kanban in parentKanbansForOutbound(order)" :key="kanban.id" class="kanban-card">
            <div class="kanban-main">
              <div class="kanban-meta">
                <strong>{{ kanban.kanbanNo }}</strong>
                <span>{{ kanban.partCode }} | {{ kanban.partName }}</span>
                <span>本单绑定 {{ (kanban.children ?? []).length }} 箱</span>
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
                <QrCodeImage :text="kanban.qrContent || kanban.barcode" :size="120" />
                <p class="mono">{{ kanban.barcode }}</p>
              </div>
            </div>
            <div class="kanban-actions">
              <button class="secondary-button" @click="toggleExpanded(kanban.id)">
                {{ expandedParents[kanban.id] ? '收起子看板' : `展开子看板(${(kanban.children ?? []).length})` }}
              </button>
              <button @click="scanForm.outboundOrderNo = order.outboundNo; simulatePrintScan(kanban)">模拟扫父看板出库</button>
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
                      <button class="secondary-button" :disabled="child.status !== 'INBOUND'" @click="scanForm.outboundOrderNo = order.outboundNo; simulatePrintScan(child)">扫本箱</button>
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

.selectable-box-table,
.selected-kanban-table,
.child-kanban-table {
  min-width: 980px;
}

.parent-group-row {
  background: rgba(37, 99, 235, 0.08);
}

.selectable-box-table tr.selected {
  background: rgba(22, 163, 74, 0.08);
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

.form-error {
  margin: 10px 0 0;
  color: #dc2626;
}

.subtle-text {
  color: var(--text-secondary);
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

.print-grid {
  grid-template-columns: minmax(0, 1fr);
}

.kanban-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 12px;
}

.kanban-meta,
.child-meta,
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
