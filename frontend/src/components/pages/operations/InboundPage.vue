<!-- 本文件实现入库工作台，支持批量选件、父子看板打印，以及按数量和箱数自动计算每箱数量后扫码入库。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { formatStatus } from '../../../app/displayText'
import { findKanbanByScanCode, findLocationForKanban } from '../../../app/kanbanHelpers'
import { equipmentCodeOptions, warehouseZoneOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import type { InboundDraftItem, InboundOrder, Kanban, PageModel, Part } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const mode = ref<'query' | 'edit' | 'print' | 'scan'>('query')
const printOrders = ref<InboundOrder[]>([])
const scanOrder = ref<InboundOrder | null>(null)
const selectedOrderIds = reactive<Record<number, boolean>>({})
const expandedOrders = reactive<Record<number, boolean>>({})
const expandedParents = reactive<Record<number, boolean>>({})
const scanInputRef = ref<HTMLInputElement | null>(null)
const batchPickerOpen = ref(false)
const batchSearch = ref('')
const batchPage = ref(1)
const batchPageSize = 8

const filters = reactive({
  status: '',
  supplierId: 0,
  inboundNo: '',
})

const form = reactive({
  supplierId: 0,
  items: [] as InboundDraftItem[],
})

const batchSelection = reactive<Record<number, boolean>>({})
const batchDraft = reactive<Record<number, { plannedQty: number; boxCount: number; pendingRepack: boolean; equipmentCode: string; warehouseZone: string }>>({})

const printedInboundScanForm = reactive({
  barcode: '',
  locationCode: '',
})

const scanMatchHint = ref('')
const formMessage = ref('')
const scanSuccessMap = reactive<Record<string, boolean>>({})
const inboundWarehouseZoneOptions = computed(() => warehouseZoneOptions(props.model.state.locations))
const inboundEquipmentOptions = computed(() => equipmentCodeOptions(props.model.state.equipment))

const supplierParts = computed(() =>
  props.model.state.parts.filter((item) => !form.supplierId || item.supplierId === form.supplierId),
)

const filteredSupplierParts = computed(() => {
  const keyword = batchSearch.value.trim().toLowerCase()
  return supplierParts.value.filter((part) => !keyword || `${part.partCode} ${part.partName}`.toLowerCase().includes(keyword))
})

const batchTotalPages = computed(() => Math.max(1, Math.ceil(filteredSupplierParts.value.length / batchPageSize)))
const pagedSupplierParts = computed(() => {
  const page = Math.min(batchPage.value, batchTotalPages.value)
  const start = (page - 1) * batchPageSize
  return filteredSupplierParts.value.slice(start, start + batchPageSize)
})

const filteredOrders = computed(() =>
  props.model.state.inboundOrders.filter((order) => {
    const statusMatch = !filters.status || order.status === filters.status
    const supplierMatch = !filters.supplierId || order.supplierId === filters.supplierId
    const orderNoMatch = !filters.inboundNo || order.inboundNo.toLowerCase().includes(filters.inboundNo.toLowerCase())
    return statusMatch && supplierMatch && orderNoMatch
  }),
)

const selectedOrderCount = computed(() => filteredOrders.value.filter((item) => selectedOrderIds[item.id]).length)
const canBatchPrint = computed(() => selectedOrderCount.value > 0)

function calculateUnitPerBox(plannedQty: number, boxCount: number) {
  if (!plannedQty || !boxCount || plannedQty <= 0 || boxCount <= 0) return 0
  return Number((plannedQty / boxCount).toFixed(3))
}

function createDraftItem(part?: Part): InboundDraftItem {
  const plannedQty = part?.defaultUnitPerBox ?? 1
  const boxCount = 1
  return {
    partId: part?.id ?? 0,
    plannedQty,
    boxCount,
    pendingRepack: false,
    equipmentCode: part?.defaultEquipmentCode ?? '',
    unitPerBox: calculateUnitPerBox(plannedQty, boxCount),
    warehouseZone: '',
  }
}

function ensureBatchDraft(part: Part) {
  if (!batchDraft[part.id]) {
    batchDraft[part.id] = {
      plannedQty: part.defaultUnitPerBox ?? 1,
      boxCount: 1,
      pendingRepack: false,
      equipmentCode: part.defaultEquipmentCode ?? '',
      warehouseZone: '',
    }
  }
  return batchDraft[part.id]
}

function batchUnitPerBox(part: Part) {
  const draft = ensureBatchDraft(part)
  return calculateUnitPerBox(draft.plannedQty, draft.boxCount)
}

function itemUnitPerBox(item: InboundDraftItem) {
  return calculateUnitPerBox(Number(item.plannedQty), Number(item.boxCount))
}

function syncItemUnitPerBox(item: InboundDraftItem) {
  item.unitPerBox = itemUnitPerBox(item)
}

function addItem() {
  form.items.push(createDraftItem())
}

function removeItem(index: number) {
  form.items.splice(index, 1)
  if (!form.items.length) {
    form.items.push(createDraftItem())
  }
}

function resetFilters() {
  filters.status = ''
  filters.supplierId = 0
  filters.inboundNo = ''
}

function resetBatchSelection() {
  Object.keys(batchSelection).forEach((key) => delete batchSelection[Number(key)])
  batchSearch.value = ''
  batchPage.value = 1
  batchPickerOpen.value = false
}

function resetCreateForm() {
  form.supplierId = props.model.state.suppliers[0]?.id ?? 0
  form.items = [createDraftItem()]
  formMessage.value = ''
  resetBatchSelection()
}

function openCreate() {
  resetCreateForm()
  scanOrder.value = null
  mode.value = 'edit'
}

async function ensureInboundOrderChildren(order: InboundOrder) {
  const parents = parentKanbansForInbound(order)
  await Promise.all(parents.map((kanban) => props.model.actions.loadKanbanChildren(kanban.id)))
}

async function openPrint(order: InboundOrder) {
  await ensureInboundOrderChildren(order)
  printOrders.value = [order]
  scanOrder.value = null
  mode.value = 'print'
}

async function openBatchPrint() {
  printOrders.value = filteredOrders.value.filter((item) => selectedOrderIds[item.id])
  await Promise.all(printOrders.value.map(ensureInboundOrderChildren))
  scanOrder.value = null
  mode.value = 'print'
}

async function openScan(order: InboundOrder) {
  await ensureInboundOrderChildren(order)
  scanOrder.value = order
  printedInboundScanForm.barcode = ''
  printedInboundScanForm.locationCode = ''
  scanMatchHint.value = ''
  mode.value = 'scan'
}

function toggleSelectAllOrders(checked: boolean) {
  filteredOrders.value.forEach((item) => {
    selectedOrderIds[item.id] = checked
  })
}

function toggleOrderExpanded(orderId: number) {
  expandedOrders[orderId] = !expandedOrders[orderId]
}

function openBatchPicker() {
  batchPickerOpen.value = true
  batchPage.value = 1
}

function closeBatchPicker() {
  batchPickerOpen.value = false
}

function applyBatchParts() {
  supplierParts.value
    .filter((part) => batchSelection[part.id])
    .forEach((part) => {
      const draft = ensureBatchDraft(part)
      form.items.push({
        partId: part.id,
        plannedQty: draft.plannedQty,
        boxCount: draft.boxCount,
        pendingRepack: draft.pendingRepack,
        equipmentCode: draft.equipmentCode,
        unitPerBox: calculateUnitPerBox(draft.plannedQty, draft.boxCount),
        warehouseZone: draft.warehouseZone,
      })
    })
  form.items = form.items.filter((item, index, rows) => rows.findIndex((row) => row.partId === item.partId) === index)
  resetBatchSelection()
}

function orderQty(order: InboundOrder, field: 'plannedQty' | 'receivedQty') {
  return order.items.reduce((sum, item) => sum + Number(item[field] ?? 0), 0)
}

function handlePartChange(item: InboundDraftItem) {
  const part = props.model.state.parts.find((candidate) => candidate.id === item.partId)
  if (!part) return
  if (!item.equipmentCode) {
    item.equipmentCode = part.defaultEquipmentCode ?? ''
  }
  if (!item.plannedQty || item.plannedQty <= 0) {
    item.plannedQty = part.defaultUnitPerBox ?? 1
  }
  if (!item.boxCount || item.boxCount <= 0) {
    item.boxCount = 1
  }
  syncItemUnitPerBox(item)
}

async function submit() {
  form.items.forEach(syncItemUnitPerBox)
  formMessage.value = ''
  const validItems = form.items.filter((item) => item.partId > 0 && item.plannedQty > 0 && item.boxCount > 0 && item.unitPerBox > 0 && item.warehouseZone)
  if (!form.supplierId) {
    formMessage.value = '请先选择供应商。'
    return
  }
  if (!validItems.length) {
    formMessage.value = '请至少添加一条完整入库明细：零件、数量、箱数、每箱数量和目标仓区都不能为空。'
    return
  }
  await props.model.actions.createInboundOrder({
    supplierId: form.supplierId,
    items: validItems,
  })
  mode.value = 'query'
}

function browserPrint() {
  window.print()
}

function parentKanbansForInbound(order: InboundOrder) {
  return props.model.state.kanbans
    .filter((item) => item.inboundNo === order.inboundNo && item.parentKanban)
    .sort((a, b) => a.kanbanNo.localeCompare(b.kanbanNo))
}

async function toggleExpanded(kanbanId: number) {
  const nextExpanded = !expandedParents[kanbanId]
  expandedParents[kanbanId] = nextExpanded
  if (nextExpanded) {
    await props.model.actions.loadKanbanChildren(kanbanId)
  }
}

function findPlannedLocationCode(scanCode: string) {
  const kanban = findKanbanByScanCode(props.model.state.kanbans, scanCode)
  if (!kanban) {
    scanMatchHint.value = '未识别到对应看板'
    return ''
  }
  const location = findLocationForKanban(props.model.state.locations, kanban)
  if (!location) {
    scanMatchHint.value = `已识别看板 ${kanban.kanbanNo}，但未找到 ${kanban.warehouseName} / ${kanban.zoneName} 对应库位`
    return ''
  }
  scanMatchHint.value = `已识别看板 ${kanban.kanbanNo}，自动匹配库位 ${location.locationCode}`
  return location.locationCode
}

async function focusScanInput() {
  await nextTick()
  scanInputRef.value?.focus()
  scanInputRef.value?.select()
}

function fillFirstInboundScanCode() {
  const first = props.model.state.kanbans.find((item) => {
    const orderMatch = !scanOrder.value || item.inboundNo === scanOrder.value.inboundNo
    return orderMatch && item.parentKanban && ['CREATED', 'WAIT_SCAN', 'PARTIAL', 'PARTIAL_INBOUND'].includes(item.status)
  })?.qrContent
  if (first) {
    printedInboundScanForm.barcode = first
  }
}

async function submitPrintedInboundScan() {
  if (!printedInboundScanForm.locationCode) {
    printedInboundScanForm.locationCode = findPlannedLocationCode(printedInboundScanForm.barcode)
  }
  if (!printedInboundScanForm.locationCode) {
    await focusScanInput()
    return
  }
  const successCode = printedInboundScanForm.barcode
  await props.model.actions.scanInbound(printedInboundScanForm)
  scanSuccessMap[successCode] = true
  printedInboundScanForm.barcode = ''
  printedInboundScanForm.locationCode = ''
  scanMatchHint.value = '入库成功，已定位到下一次扫码。'
  await focusScanInput()
}

async function simulateScanKanban(kanban: Kanban) {
  printedInboundScanForm.barcode = kanban.qrContent || kanban.barcode
  printedInboundScanForm.locationCode = findPlannedLocationCode(printedInboundScanForm.barcode)
  await submitPrintedInboundScan()
}

async function submitInboundScanByEnter() {
  if (!printedInboundScanForm.barcode) return
  await submitPrintedInboundScan()
}

watch(mode, async (value) => {
  if (value === 'scan' || value === 'print') {
    await focusScanInput()
  }
})

watch(
  () => printedInboundScanForm.barcode,
  (value) => {
    if (!value.trim()) {
      printedInboundScanForm.locationCode = ''
      scanMatchHint.value = ''
      return
    }
    printedInboundScanForm.locationCode = findPlannedLocationCode(value)
  },
)
</script>

<template>
  <section class="stack">
    <section v-if="mode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>入库筛选</h3>
            <p>可先查询入库单，再选择打印或直接进入扫码入库。</p>
          </div>
          <div class="action-row">
            <button @click="openCreate">创建入库单</button>
            <button :disabled="!canBatchPrint" @click="openBatchPrint">批量打印</button>
          </div>
        </div>
        <div class="form-grid four">
          <select v-model="filters.status">
            <option value="">全部状态</option>
            <option value="CREATED">{{ formatStatus('CREATED') }}</option>
            <option value="PARTIAL">{{ formatStatus('PARTIAL') }}</option>
            <option value="PARTIAL_INBOUND">{{ formatStatus('PARTIAL_INBOUND') }}</option>
            <option value="COMPLETED">{{ formatStatus('COMPLETED') }}</option>
          </select>
          <select v-model.number="filters.supplierId">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <input v-model="filters.inboundNo" placeholder="入库单号" />
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel table-scroll">
        <table class="table inbound-order-table">
          <thead>
            <tr>
              <th><input type="checkbox" :checked="selectedOrderCount === filteredOrders.length && filteredOrders.length > 0" @change="toggleSelectAllOrders(($event.target as HTMLInputElement).checked)" /></th>
              <th>入库单号</th>
              <th>供应商</th>
              <th>状态</th>
              <th>明细数</th>
              <th>箱数</th>
              <th>计划数量</th>
              <th>已入库数量</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="order in filteredOrders" :key="order.id">
              <tr>
                <td><input v-model="selectedOrderIds[order.id]" type="checkbox" /></td>
                <td class="mono">{{ order.inboundNo }}</td>
                <td>{{ order.supplierName }}</td>
                <td>{{ formatStatus(order.status) }}</td>
                <td>{{ order.items.length }}</td>
                <td>{{ order.items.reduce((sum, item) => sum + Number(item.boxCount), 0) }}</td>
                <td>{{ orderQty(order, 'plannedQty').toFixed(3) }}</td>
                <td>{{ orderQty(order, 'receivedQty').toFixed(3) }}</td>
                <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                <td class="action-row">
                  <button class="secondary-button" @click="toggleOrderExpanded(order.id)">
                    {{ expandedOrders[order.id] ? '收起' : '明细' }}
                  </button>
                  <button class="secondary-button" @click="openPrint(order)">打印</button>
                  <button class="secondary-button" @click="openScan(order)">扫码入库</button>
                </td>
              </tr>
              <tr v-if="expandedOrders[order.id]" class="order-detail-row">
                <td colspan="10">
                  <div class="order-detail-grid">
                    <table class="table detail-table">
                      <thead>
                        <tr>
                          <th>零件</th>
                          <th>计划数量</th>
                          <th>已入库</th>
                          <th>箱数</th>
                          <th>每箱数量</th>
                          <th>器具</th>
                          <th>目标仓区</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="item in order.items" :key="item.id">
                          <td>{{ item.partCode }} | {{ item.partName }}</td>
                          <td>{{ Number(item.plannedQty).toFixed(3) }} {{ item.unit }}</td>
                          <td>{{ Number(item.receivedQty).toFixed(3) }} {{ item.unit }}</td>
                          <td>{{ item.boxCount }}</td>
                          <td>{{ Number(item.unitPerBox).toFixed(3) }}</td>
                          <td>{{ item.equipmentCode || '-' }}</td>
                          <td>{{ item.warehouseZone }}</td>
                        </tr>
                      </tbody>
                    </table>
                    <div class="query-kanban-grid">
                      <article v-for="kanban in parentKanbansForInbound(order)" :key="kanban.id" class="query-kanban-card">
                        <div>
                          <strong class="mono">{{ kanban.kanbanNo }}</strong>
                          <p>{{ kanban.partCode }} | {{ kanban.partName }}</p>
                          <p>{{ kanban.warehouseName }} / {{ kanban.zoneName }} | {{ formatStatus(kanban.status) }}</p>
                        </div>
                        <div class="child-chip-row">
                          <span v-for="child in kanban.children ?? []" :key="child.id" class="tag-pill mono">
                            {{ child.kanbanNo }} | {{ formatStatus(child.status) }}
                          </span>
                        </div>
                      </article>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="mode === 'edit'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>创建入库单</h3>
            <p>保存后会按箱数自动生成父子看板，每箱数量由数量和箱数自动计算。</p>
          </div>
        </div>
        <div class="form-grid three">
          <select v-model.number="form.supplierId">
            <option :value="0">选择供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>供应商零件批量加入</h3>
            <p>零件较多时可在弹窗中搜索、分页选择，再批量加入入库明细。</p>
          </div>
          <div class="action-row">
            <button class="secondary-button" @click="openBatchPicker">打开批量选件</button>
          </div>
        </div>
        <div class="summary-strip">
          <span>当前供应商可选 {{ supplierParts.length }} 个零件</span>
          <span>已勾选 {{ supplierParts.filter((part) => batchSelection[part.id]).length }} 个</span>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>入库明细</h3>
          </div>
          <div class="action-row">
            <button class="secondary-button" @click="addItem">新增一行</button>
          </div>
        </div>
        <div class="detail-stack">
          <div v-for="(item, index) in form.items" :key="`${item.partId}-${index}`" class="detail-row">
            <select v-model.number="item.partId" @change="handlePartChange(item)">
              <option :value="0">选择零件</option>
              <option v-for="part in supplierParts" :key="part.id" :value="part.id">
                {{ part.partCode }} | {{ part.partName }}
              </option>
            </select>
            <input v-model.number="item.plannedQty" type="number" min="0.001" step="0.001" placeholder="计划数量" @input="syncItemUnitPerBox(item)" />
            <input v-model.number="item.boxCount" type="number" min="1" step="1" placeholder="箱数" @input="syncItemUnitPerBox(item)" />
            <input :value="itemUnitPerBox(item)" type="number" readonly placeholder="每箱数量自动计算" />
            <select v-model="item.equipmentCode">
              <option value="">选择器具</option>
              <option v-for="opt in inboundEquipmentOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <select v-model="item.warehouseZone">
              <option value="">选择仓区</option>
              <option v-for="opt in inboundWarehouseZoneOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <label class="checkbox-line"><input v-model="item.pendingRepack" type="checkbox" /> 转包</label>
            <button class="secondary-button" @click="removeItem(index)">删除</button>
          </div>
        </div>
        <p v-if="formMessage" class="form-error">{{ formMessage }}</p>
        <div class="footer-actions">
          <button @click="submit">保存入库单</button>
          <button class="secondary-button" @click="mode = 'query'">返回查询</button>
        </div>
      </section>
    </section>

    <section v-else-if="mode === 'scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码入库{{ scanOrder ? `：${scanOrder.inboundNo}` : '' }}</h3>
          <p>当前扫码已绑定入库单；扫父看板会处理该订单下仍待入库的箱级子看板。</p>
        </div>
        <div class="action-row">
          <button class="secondary-button" @click="mode = 'query'">返回查询</button>
        </div>
      </div>
      <div class="scan-action-layout">
        <div class="form-grid three">
          <input
            ref="scanInputRef"
            v-model="printedInboundScanForm.barcode"
            placeholder="扫描枪输入二维码内容或条码"
            @keydown.enter.prevent="submitInboundScanByEnter"
          />
          <select v-model="printedInboundScanForm.locationCode">
            <option value="">自动匹配目标库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitPrintedInboundScan">确认入库</button>
        </div>
        <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
        <div class="scan-assist-row two-col">
          <button class="secondary-button" :disabled="!scanOrder" @click="fillFirstInboundScanCode">填充本单首个待入库父看板</button>
        </div>
        <div v-if="printedInboundScanForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="printedInboundScanForm.barcode" :size="160" />
          <p class="mono">{{ printedInboundScanForm.barcode }}</p>
        </div>
      </div>
    </section>

    <section v-else class="panel print-panel">
      <div class="section-head print-toolbar">
        <div>
          <h3>入库打印</h3>
          <p>每条入库明细会生成一个父看板和若干箱级子看板，打印后可直接扫码入库。</p>
        </div>
        <div class="action-row">
          <button @click="browserPrint">浏览器打印</button>
          <button class="secondary-button" @click="mode = 'query'">返回查询</button>
        </div>
      </div>

      <section class="panel compact-scan-panel">
        <div class="form-grid three">
          <input
            ref="scanInputRef"
            v-model="printedInboundScanForm.barcode"
            placeholder="打印后可在这里模拟扫码入库"
            @keydown.enter.prevent="submitInboundScanByEnter"
          />
          <select v-model="printedInboundScanForm.locationCode">
            <option value="">自动匹配目标库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitPrintedInboundScan">执行入库</button>
        </div>
        <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
      </section>

      <div class="print-grid">
        <article v-for="order in printOrders" :key="order.id" class="print-order-card">
          <header class="order-header">
            <div>
              <h4>{{ order.inboundNo }}</h4>
              <p>{{ order.supplierName }} | {{ formatStatus(order.status) }}</p>
            </div>
          </header>

          <div v-for="kanban in parentKanbansForInbound(order)" :key="kanban.id" class="kanban-card">
            <div class="kanban-main">
              <div class="kanban-meta">
                <strong>{{ kanban.kanbanNo }}</strong>
                <span>{{ kanban.partCode }} | {{ kanban.partName }}</span>
                <span>箱数 {{ kanban.boxCount }} / 每箱 {{ kanban.unitPerBox }}</span>
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
              <button :class="{ success: scanSuccessMap[kanban.qrContent || kanban.barcode] }" @click="simulateScanKanban(kanban)">
                {{ scanSuccessMap[kanban.qrContent || kanban.barcode] ? '已成功入库' : '模拟扫码入库' }}
              </button>
            </div>
            <div v-if="expandedParents[kanban.id]" class="child-grid">
              <div v-for="child in kanban.children ?? []" :key="child.id" class="child-card">
                <QrCodeImage :text="child.qrContent || child.barcode" :size="92" />
                <div class="child-meta">
                  <strong>{{ child.kanbanNo }}</strong>
                  <span>第 {{ child.boxIndex }} 箱</span>
                  <span>数量 {{ child.qty }}</span>
                  <span>{{ formatStatus(child.status) }}</span>
                </div>
                <button
                  class="secondary-button"
                  :disabled="!['CREATED', 'WAIT_SCAN'].includes(child.status)"
                  @click="simulateScanKanban(child)"
                >
                  {{ scanSuccessMap[child.qrContent || child.barcode] ? '本箱已入库' : '扫本箱入库' }}
                </button>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>

    <teleport to="body">
      <div v-if="batchPickerOpen" class="modal-backdrop">
        <section class="modal-panel">
          <div class="section-head">
            <div>
              <h3>批量选择供应商零件</h3>
              <p>先搜索零件，再填写计划数量、箱数、器具和目标仓区；加入后会自动计算每箱数量。</p>
            </div>
            <div class="action-row">
              <button @click="applyBatchParts">加入选中零件</button>
              <button class="secondary-button" @click="closeBatchPicker">关闭</button>
            </div>
          </div>
          <div class="form-grid three">
            <input v-model="batchSearch" placeholder="搜索零件号 / 名称" @input="batchPage = 1" />
            <select v-model.number="form.supplierId">
              <option :value="0">全部供应商零件</option>
              <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
                {{ item.supplierCode }} | {{ item.supplierName }}
              </option>
            </select>
            <div class="pager-actions">
              <button class="secondary-button" :disabled="batchPage <= 1" @click="batchPage -= 1">上一页</button>
              <span>第 {{ batchPage }} / {{ batchTotalPages }} 页</span>
              <button class="secondary-button" :disabled="batchPage >= batchTotalPages" @click="batchPage += 1">下一页</button>
            </div>
          </div>
          <div class="table-scroll">
            <table class="table batch-part-table">
              <thead>
                <tr>
                  <th>选择</th>
                  <th>零件</th>
                  <th>计划数量</th>
                  <th>箱数</th>
                  <th>每箱数量</th>
                  <th>器具编码</th>
                  <th>目标仓区</th>
                  <th>转包</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="part in pagedSupplierParts" :key="part.id">
                  <td><input v-model="batchSelection[part.id]" type="checkbox" /></td>
                  <td>{{ part.partCode }} | {{ part.partName }}</td>
                  <td><input v-model.number="ensureBatchDraft(part).plannedQty" type="number" min="0.001" step="0.001" /></td>
                  <td><input v-model.number="ensureBatchDraft(part).boxCount" type="number" min="1" step="1" /></td>
                  <td><input :value="batchUnitPerBox(part)" type="number" readonly /></td>
                  <td>
                    <select v-model="ensureBatchDraft(part).equipmentCode">
                      <option value="">选择器具</option>
                      <option v-for="item in inboundEquipmentOptions" :key="item.value" :value="item.value">
                        {{ item.label }}
                      </option>
                    </select>
                  </td>
                  <td>
                    <select v-model="ensureBatchDraft(part).warehouseZone">
                      <option value="">选择仓区</option>
                      <option v-for="item in inboundWarehouseZoneOptions" :key="item.value" :value="item.value">
                        {{ item.label }}
                      </option>
                    </select>
                  </td>
                  <td><input v-model="ensureBatchDraft(part).pendingRepack" type="checkbox" /></td>
                </tr>
                <tr v-if="!pagedSupplierParts.length">
                  <td colspan="8" class="empty-cell">没有匹配的零件</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </teleport>
  </section>
</template>

<style scoped>
.detail-stack,
.print-grid,
.child-grid {
  display: grid;
  gap: 12px;
}

.summary-strip,
.pager-actions,
.child-chip-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-strip {
  margin-top: 10px;
}

.detail-row {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.checkbox-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.scan-assist-row {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.two-col {
  grid-template-columns: minmax(0, 1fr) auto;
}

.scan-hint {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.form-error {
  margin: 10px 0 0;
  color: #dc2626;
}

.table-scroll {
  overflow-x: auto;
}

.inbound-order-table,
.detail-table,
.batch-part-table {
  min-width: 980px;
}

.order-detail-row > td {
  background: rgba(15, 23, 42, 0.03);
  padding: 12px;
}

.order-detail-grid {
  display: grid;
  gap: 12px;
}

.query-kanban-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 10px;
}

.query-kanban-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
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

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.modal-panel {
  width: min(1180px, 96vw);
  max-height: 88vh;
  overflow: auto;
  background: var(--panel-bg, #fff);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.24);
}

.print-grid {
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.child-grid {
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  align-items: start;
}

.print-order-card,
.kanban-card,
.child-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
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
.child-card {
  justify-items: center;
}

.child-card {
  min-height: 180px;
  align-content: start;
  text-align: center;
}

button.success {
  background: #16a34a;
}

@media (max-width: 1100px) {
  .detail-row {
    grid-template-columns: 1fr;
  }

  .kanban-main {
    grid-template-columns: 1fr;
  }
}
</style>
