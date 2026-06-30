<!-- 本文件实现出库工作台：按零件和箱数创建出库单，后端按 FIFO 自动分配箱级看板，并支持打印后扫码出库。 -->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatStatus } from '../../../app/displayText'
import { splitBusinessNos } from '../../../app/kanbanHelpers'
import PageModal from '../../shared/PageModal.vue'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import OutboundCancelModal from './outbound-modals/OutboundCancelModal.vue'
import OutboundCreateModal from './outbound-modals/OutboundCreateModal.vue'
import OutboundOrderDetailModal from './outbound-modals/OutboundOrderDetailModal.vue'
import type { OutboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

type OutboundPrintLine = {
  key: string
  kanbanNo: string
  partId: number
  partText: string
  unit: string
  allocatedQty: number
  outboundQty: number
  remainingQty: number
  status: string
  locationText: string
  barcode: string
  qrContent: string
  inboundNo: string
  boxIndex: number
  pending: boolean
}

const viewMode = ref<'query' | 'create' | 'manual'>('query')
const printOrders = ref<OutboundOrder[]>([])
const detailOrder = ref<OutboundOrder | null>(null)
const scanInputRef = ref<HTMLInputElement | null>(null)
const activeModal = ref<'print' | 'scan' | 'cancel' | ''>('')
const selectedOrderIds = reactive<Record<number, boolean>>({})
const cancelOrders = ref<OutboundOrder[]>([])
const cancelInitialSelectedIds = ref<number[]>([])
const cancelMode = ref<'single' | 'batch'>('single')
const orders = ref<OutboundOrder[]>([])
const orderPage = ref(1)
const orderPageSize = ref(20)
const orderTotal = ref(0)
const orderLoading = ref(false)
const orderError = ref('')
const scanSubmitting = ref(false)
const scanResultMessage = ref('')
const scanMatchHint = ref('')
const selectedScanKanbanNos = ref<string[]>([])
const scanSuccessMap = reactive<Record<string, boolean>>({})

const filters = reactive({
  status: '',
  customerId: 0,
  outboundNo: '',
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

const rows = computed(() => orders.value)
const orderTotalPages = computed(() => Math.max(1, Math.ceil(orderTotal.value / orderPageSize.value)))

const outboundOrderOptions = computed(() => props.model.state.outboundOrders.filter((order) => !['COMPLETED', 'CANCELLED', 'CANCELED'].includes(order.status)))
const activeScanOrder = computed(() => props.model.state.outboundOrders.find((order) => order.outboundNo === scanForm.outboundOrderNo) ?? null)
const activeScanLines = computed(() => (activeScanOrder.value ? allocationLinesForOrder(activeScanOrder.value) : []))
const pendingScanLines = computed(() => activeScanLines.value.filter((line) => line.pending))
const scannedOutboundKanban = computed(() => findOutboundKanbanByScanCode(scanForm.barcode))
const outboundScanSummary = computed(() => {
  if (!activeScanOrder.value) return '请先选择出库单'
  if (!pendingScanLines.value.length) return '没有待出库箱看板'
  return `已选择 ${selectedScanKanbanNos.value.length} / ${pendingScanLines.value.length} 箱`
})
const canSubmitOutboundScan = computed(() => Boolean(scanForm.outboundOrderNo && scanForm.barcode && selectedScanKanbanNos.value.length && !scanSubmitting.value))
const selectedOrderCount = computed(() => rows.value.filter((item) => selectedOrderIds[item.id]).length)
const canBatchPrint = computed(() => selectedOrderCount.value > 0)
const canOpenBatchCancel = computed(() => rows.value.length > 0)

function clearSelectedOrders() {
  Object.keys(selectedOrderIds).forEach((key) => delete selectedOrderIds[Number(key)])
}

function mergeOutboundOrders(records: OutboundOrder[]) {
  if (!records.length) return
  const map = new Map(props.model.state.outboundOrders.map((item) => [item.id, item]))
  records.forEach((item) => map.set(item.id, item))
  props.model.state.outboundOrders = Array.from(map.values()).sort((left, right) => {
    const leftTime = new Date(left.createdAt).getTime() || 0
    const rightTime = new Date(right.createdAt).getTime() || 0
    return rightTime - leftTime
  })
}

function reloadOrdersAfterMutation(page = orderPage.value) {
  return loadOrders(page)
}

async function loadOrders(page = orderPage.value) {
  const nextPage = Math.max(1, page)
  orderLoading.value = true
  orderError.value = ''
  try {
    const result = await api.listOutboundOrdersPage({
      status: filters.status,
      customerId: filters.customerId,
      outboundNo: filters.outboundNo.trim(),
      page: nextPage,
      size: orderPageSize.value,
    })
    orders.value = result.records
    orderPage.value = result.page
    orderTotal.value = result.total
    mergeOutboundOrders(result.records)
    if (!result.records.length && result.total > 0 && result.page > 1) {
      await loadOrders(Math.max(1, result.totalPages))
      return
    }
    clearSelectedOrders()
  } catch (error) {
    orderError.value = error instanceof Error ? error.message : '出库单查询失败'
  } finally {
    orderLoading.value = false
  }
}

async function searchOrders() {
  await loadOrders(1)
}

async function changeOrderPage(page: number) {
  if (page < 1 || page > orderTotalPages.value || page === orderPage.value) return
  await loadOrders(page)
}

async function resetFilters() {
  filters.status = ''
  filters.customerId = 0
  filters.outboundNo = ''
  await loadOrders(1)
}

function toggleSelectAllOrders(checked: boolean) {
  rows.value.forEach((item) => {
    selectedOrderIds[item.id] = checked
  })
}

function setOrderSelected(orderId: number, checked: boolean) {
  selectedOrderIds[orderId] = checked
}

function toggleOrderSelected(orderId: number) {
  selectedOrderIds[orderId] = !selectedOrderIds[orderId]
}

function formatOutboundStatus(status: string | null | undefined) {
  if (status === 'PARTIAL' || status === 'PARTIAL_OUTBOUND') return '部分出库'
  return formatStatus(status)
}

function canCancelOutboundOrder(order: OutboundOrder) {
  return order.status === 'CREATED'
}

function openCancelOrder(order: OutboundOrder) {
  cancelMode.value = 'single'
  cancelOrders.value = canCancelOutboundOrder(order) ? [order] : []
  cancelInitialSelectedIds.value = canCancelOutboundOrder(order) ? [order.id] : []
  activeModal.value = 'cancel'
}

function openBatchCancel() {
  cancelMode.value = 'batch'
  const eligibleOrders = rows.value.filter(canCancelOutboundOrder)
  cancelOrders.value = eligibleOrders
  cancelInitialSelectedIds.value = eligibleOrders.filter((order) => selectedOrderIds[order.id]).map((order) => order.id)
  activeModal.value = 'cancel'
}

async function completeOutboundCancel(cancelledOrders: OutboundOrder[]) {
  mergeOutboundOrders(cancelledOrders)
  await reloadOrdersAfterMutation(orderPage.value)
  closeOperationModal()
}

function openCreate() {
  viewMode.value = 'create'
}

function openManualEntry() {
  viewMode.value = 'manual'
}

function closeViewModal() {
  viewMode.value = 'query'
}

async function handleCreateCreated() {
  orderPage.value = 1
  await reloadOrdersAfterMutation(1)
  closeViewModal()
}

async function openScan(order: OutboundOrder) {
  scanForm.outboundOrderNo = order.outboundNo
  scanForm.barcode = outboundOrderScanCode(order)
  scanResultMessage.value = ''
  syncOutboundScanSelectionByCode(scanForm.barcode)
  activeModal.value = 'scan'
}

async function openPrint(order: OutboundOrder) {
  printOrders.value = [order]
  scanForm.outboundOrderNo = order.outboundNo
  activeModal.value = 'print'
}

async function openBatchPrint() {
  printOrders.value = rows.value.filter((item) => selectedOrderIds[item.id])
  scanForm.outboundOrderNo = printOrders.value[0]?.outboundNo ?? ''
  activeModal.value = 'print'
}

function closeOperationModal() {
  activeModal.value = ''
  scanForm.barcode = ''
  scanResultMessage.value = ''
  scanMatchHint.value = ''
  selectedScanKanbanNos.value = []
  cancelOrders.value = []
  cancelInitialSelectedIds.value = []
}

function browserPrint() {
  document.body.classList.add('printing-operation-modal')
  window.addEventListener('afterprint', () => document.body.classList.remove('printing-operation-modal'), { once: true })
  window.print()
  window.setTimeout(() => document.body.classList.remove('printing-operation-modal'), 800)
}

function sourceText(order: OutboundOrder) {
  return order.inboundOrderNos.length ? order.inboundOrderNos.join('，') : '系统按 FIFO 自动分配'
}

function outboundOrderScanCode(order: OutboundOrder) {
  return order.qrContent || `WMS-OUTBOUND|${order.outboundNo}`
}

function scanCodeForLine(line: OutboundPrintLine) {
  return line.qrContent || line.barcode || line.kanbanNo
}

function findOutboundKanbanByScanCode(scanCode: string) {
  const normalized = scanCode.trim()
  if (!normalized) return null
  return props.model.state.kanbans.find((item) =>
    item.kanbanNo === normalized
    || item.barcode === normalized
    || item.qrContent === normalized,
  ) ?? null
}

function outboundScanOptionsForOrder(order: OutboundOrder) {
  const orderOption = [{
    label: `出库单 ${order.outboundNo} | 批量执行全部待出库分配`,
    value: outboundOrderScanCode(order),
  }]
  const lineOptions = allocationLinesForOrder(order)
    .filter((item) => item.pending)
    .map((item) => ({
      label: `箱看板 ${item.kanbanNo} | ${item.partText} | 待出 ${item.remainingQty.toFixed(3)} ${item.unit}`,
      value: scanCodeForLine(item),
    }))
  return [...orderOption, ...lineOptions]
}

function parseAllocationQtyText(detail: string | null | undefined, kanbanNo: string, fallbackQty: number) {
  const segment = (detail || '')
    .split('；')
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${kanbanNo}：`))
  if (!segment) {
    return {
      allocatedQty: fallbackQty,
      outboundQty: 0,
      remainingQty: fallbackQty,
    }
  }
  const allocatedQty = Number(segment.match(/分配\s*([0-9.]+)/)?.[1] ?? fallbackQty)
  const outboundQty = Number(segment.match(/已出\s*([0-9.]+)/)?.[1] ?? 0)
  const remainingQty = Number(segment.match(/剩余\s*([0-9.]+)/)?.[1] ?? Math.max(0, allocatedQty - outboundQty))
  return {
    allocatedQty: Number.isFinite(allocatedQty) ? allocatedQty : fallbackQty,
    outboundQty: Number.isFinite(outboundQty) ? outboundQty : 0,
    remainingQty: Number.isFinite(remainingQty) ? remainingQty : Math.max(0, fallbackQty),
  }
}

function allocationLinesForOrder(order: OutboundOrder): OutboundPrintLine[] {
  return order.items.flatMap((item) =>
    splitBusinessNos(item.kanbanNo).map((kanbanNo) => {
      const kanban = props.model.state.kanbans.find((box) => box.kanbanNo === kanbanNo)
      const qty = parseAllocationQtyText(item.allocationDetail, kanbanNo, Number(item.plannedQty ?? 0))
      const status = kanban?.status ?? (item.scannedQty >= item.plannedQty ? 'OUTBOUND' : 'ALLOCATED')
      return {
        key: `${item.id}:${kanbanNo}`,
        kanbanNo,
        partId: item.partId,
        partText: `${item.partCode} | ${item.partName}`,
        unit: item.unit,
        allocatedQty: qty.allocatedQty,
        outboundQty: qty.outboundQty,
        remainingQty: qty.remainingQty,
        status,
        locationText: kanban ? `${kanban.warehouseName} / ${kanban.zoneName}` : `${item.warehouseName} / ${item.zoneName}`,
        barcode: kanban?.barcode ?? kanbanNo,
        qrContent: kanban?.qrContent ?? kanbanNo,
        inboundNo: kanban?.inboundNo ?? '-',
        boxIndex: kanban?.boxIndex ?? 0,
        pending: qty.remainingQty > 0 && ['ALLOCATED', 'INBOUND', 'PARTIAL_OUTBOUND'].includes(status),
      }
    }),
  )
}

function groupedAllocationLines(order: OutboundOrder) {
  const groups = new Map<string, { key: string; partText: string; locationText: string; lines: OutboundPrintLine[] }>()
  allocationLinesForOrder(order).forEach((line) => {
    const key = `${line.partId}:${line.locationText}`
    const group = groups.get(key) ?? {
      key,
      partText: line.partText,
      locationText: line.locationText,
      lines: [],
    }
    group.lines.push(line)
    groups.set(key, group)
  })
  return Array.from(groups.values())
}

function handleScanOrderChange() {
  scanForm.barcode = activeScanOrder.value ? outboundOrderScanCode(activeScanOrder.value) : ''
  scanResultMessage.value = ''
  syncOutboundScanSelectionByCode(scanForm.barcode)
}

function isScanLineSelected(kanbanNo: string) {
  return selectedScanKanbanNos.value.includes(kanbanNo)
}

function toggleScanLine(kanbanNo: string, checked: boolean) {
  if (checked) {
    selectedScanKanbanNos.value = Array.from(new Set([...selectedScanKanbanNos.value, kanbanNo]))
    return
  }
  selectedScanKanbanNos.value = selectedScanKanbanNos.value.filter((item) => item !== kanbanNo)
}

function selectAllPendingOutboundLines() {
  selectedScanKanbanNos.value = pendingScanLines.value.map((line) => line.kanbanNo)
}

function clearOutboundScanLines() {
  selectedScanKanbanNos.value = []
}

function uniqueLinesByKanbanNo(lines: OutboundPrintLine[]) {
  const seen = new Set<string>()
  return lines.filter((line) => {
    if (seen.has(line.kanbanNo)) return false
    seen.add(line.kanbanNo)
    return true
  })
}

function syncOutboundScanSelectionByCode(scanCode: string) {
  const normalized = scanCode.trim()
  scanResultMessage.value = ''
  if (!normalized) {
    selectedScanKanbanNos.value = []
    scanMatchHint.value = ''
    return
  }
  const order = activeScanOrder.value
  if (order && normalized === outboundOrderScanCode(order)) {
    selectedScanKanbanNos.value = pendingScanLines.value.map((line) => line.kanbanNo)
    scanMatchHint.value = pendingScanLines.value.length
      ? `已识别出库单二维码，默认选择 ${pendingScanLines.value.length} 个待出库箱。`
      : '当前出库单没有待出库箱。'
    return
  }
  const kanban = findOutboundKanbanByScanCode(normalized)
  if (!kanban) {
    selectedScanKanbanNos.value = []
    scanMatchHint.value = '未识别到对应箱看板'
    return
  }
  const line = activeScanLines.value.find((item) => item.kanbanNo === kanban.kanbanNo)
  if (!line) {
    selectedScanKanbanNos.value = []
    scanMatchHint.value = `箱看板 ${kanban.kanbanNo} 不属于当前出库单 ${scanForm.outboundOrderNo || '-'}`
    return
  }
  if (!line.pending) {
    selectedScanKanbanNos.value = []
    scanMatchHint.value = `箱看板 ${kanban.kanbanNo} 当前状态为 ${formatStatus(line.status)}，没有待出库分配。`
    return
  }
  selectedScanKanbanNos.value = [line.kanbanNo]
  scanMatchHint.value = `已识别箱看板 ${line.kanbanNo}，将出库 ${line.remainingQty.toFixed(3)} ${line.unit}。`
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
    syncOutboundScanSelectionByCode(first)
  }
}

async function submitScan() {
  scanResultMessage.value = ''
  if (!activeScanOrder.value) {
    scanMatchHint.value = '请先选择出库单。'
    return
  }
  if (!selectedScanKanbanNos.value.length) {
    scanMatchHint.value = pendingScanLines.value.length ? '请至少选择一个待出库箱看板。' : '当前出库单没有待出库箱看板。'
    return
  }
  const code = scanForm.barcode
  const selectedLines = uniqueLinesByKanbanNo(activeScanLines.value.filter((line) => selectedScanKanbanNos.value.includes(line.kanbanNo) && line.pending))
  const orderCodeSelected = activeScanOrder.value
    && code === outboundOrderScanCode(activeScanOrder.value)
    && selectedLines.length === pendingScanLines.value.length
  scanSubmitting.value = true
  try {
    if (orderCodeSelected) {
      const result = await props.model.actions.scanOutbound({
        barcode: code,
        outboundOrderNo: activeScanOrder.value.outboundNo,
      })
      result.affectedKanbanNos.forEach((kanbanNo) => {
        scanSuccessMap[kanbanNo] = true
      })
      scanResultMessage.value = `${result.message}：${result.affectedKanbanNos.join('、')}`
    } else {
      const handled: string[] = []
      for (const line of selectedLines) {
        const result = await props.model.actions.scanOutbound({
          barcode: scanCodeForLine(line),
          outboundOrderNo: activeScanOrder.value.outboundNo,
        })
        result.affectedKanbanNos.forEach((kanbanNo) => {
          scanSuccessMap[kanbanNo] = true
          if (!handled.includes(kanbanNo)) handled.push(kanbanNo)
        })
      }
      scanResultMessage.value = handled.length
        ? `扫码出库成功，已处理 ${handled.length} 箱：${handled.join('、')}`
        : '扫码出库成功。'
    }
    selectedScanKanbanNos.value = []
    scanForm.barcode = activeScanOrder.value ? outboundOrderScanCode(activeScanOrder.value) : ''
    await loadOrders(orderPage.value)
    syncOutboundScanSelectionByCode(scanForm.barcode)
  } catch (error) {
    scanResultMessage.value = error instanceof Error ? error.message : '出库失败，请检查出库单和箱看板状态。'
  } finally {
    scanSubmitting.value = false
    await focusScanInput()
  }
}

async function submitScanByEnter() {
  if (!scanForm.barcode || !scanForm.outboundOrderNo) return
  await submitScan()
}

async function submitManual() {
  await props.model.actions.manualInventoryEntry(manualForm)
  manualForm.partId = 0
  manualForm.locationId = 0
  manualForm.qty = 1
  manualForm.remark = ''
  closeViewModal()
}

function openOutboundDetail(order: OutboundOrder) {
  detailOrder.value = order
}

function closeOutboundDetail() {
  detailOrder.value = null
}

watch(activeModal, async (value) => {
  if (value === 'scan') {
    await focusScanInput()
  }
})

watch(orderPageSize, async () => {
  await loadOrders(1)
})

watch(
  () => scanForm.barcode,
  (value) => {
    if (activeModal.value !== 'scan') return
    syncOutboundScanSelectionByCode(value)
  },
)

function handleBusinessChanged() {
  void loadOrders(orderPage.value)
}

onMounted(() => {
  window.addEventListener('wms-business-changed', handleBusinessChanged)
  void loadOrders(1)
})

onBeforeUnmount(() => {
  window.removeEventListener('wms-business-changed', handleBusinessChanged)
})
</script>

<template>
  <section class="stack">
    <section class="stack">
      <section class="panel outbound-filter-panel">
        <div class="outbound-filter-line">
          <div class="outbound-filter-title">
            <h3>出库筛选</h3>
          </div>
          <div class="outbound-filter-row">
            <select v-model="filters.status">
              <option value="">全部状态</option>
              <option value="CREATED">{{ formatOutboundStatus('CREATED') }}</option>
              <option value="PARTIAL">{{ formatOutboundStatus('PARTIAL') }}</option>
              <option value="COMPLETED">{{ formatOutboundStatus('COMPLETED') }}</option>
              <option value="CANCELLED">{{ formatOutboundStatus('CANCELLED') }}</option>
            </select>
            <select v-model.number="filters.customerId">
              <option :value="0">全部客户</option>
              <option v-for="item in model.state.customers" :key="item.id" :value="item.id">
                {{ item.customerCode }} | {{ item.customerName }}
              </option>
            </select>
            <input v-model="filters.outboundNo" placeholder="出库单号" />
            <select v-model.number="orderPageSize" title="每页数量">
              <option :value="10">10条/页</option>
              <option :value="20">20条/页</option>
              <option :value="50">50条/页</option>
              <option :value="100">100条/页</option>
            </select>
            <button class="secondary-button compact-filter-button" :disabled="orderLoading" @click="searchOrders">查询</button>
            <button class="secondary-button compact-filter-button" :disabled="orderLoading" @click="resetFilters">重置</button>
          </div>
          <div class="action-row outbound-filter-actions">
            <button @click="openCreate">创建出库单</button>
            <button class="batch-action-button" :disabled="!canBatchPrint" @click="openBatchPrint">批量打印</button>
            <button class="secondary-button batch-action-button" :disabled="!canOpenBatchCancel" @click="openBatchCancel">批量取消</button>
            <button class="secondary-button" @click="openManualEntry">手工入账</button>
          </div>
        </div>
      </section>

      <section class="panel table-scroll">
        <div class="table-toolbar">
          <span>{{ orderLoading ? '正在查询出库单...' : `共 ${orderTotal} 条，第 ${orderPage} / ${orderTotalPages} 页` }}</span>
          <span v-if="orderError" class="form-error">{{ orderError }}</span>
          <div class="pager-actions compact-pager">
            <button class="secondary-button" :disabled="orderLoading || orderPage <= 1" @click="changeOrderPage(orderPage - 1)">上一页</button>
            <button class="secondary-button" :disabled="orderLoading || orderPage >= orderTotalPages" @click="changeOrderPage(orderPage + 1)">下一页</button>
          </div>
        </div>
        <table class="table order-table outbound-order-table">
          <thead>
            <tr>
              <th class="select-col">
                <input
                  class="compact-check"
                  type="checkbox"
                  :checked="selectedOrderCount === rows.length && rows.length > 0"
                  @change="toggleSelectAllOrders(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th class="no-col">出库单号</th>
              <th class="customer-col">客户</th>
              <th class="source-col">FIFO 来源</th>
              <th class="status-col">状态</th>
              <th class="count-col">箱数</th>
              <th class="qty-col">数量</th>
              <th class="time-col">创建时间</th>
              <th class="action-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(order, index) in rows" :key="order.id">
              <tr
                class="outbound-order-row"
                :class="{
                  selected: selectedOrderIds[order.id],
                  'tone-a': Math.floor(index / 2) % 2 === 0,
                  'tone-b': Math.floor(index / 2) % 2 === 1,
                }"
                @click="toggleOrderSelected(order.id)"
              >
                <td class="select-cell">
                  <input
                    class="compact-check"
                    type="checkbox"
                    :checked="!!selectedOrderIds[order.id]"
                    @click.stop
                    @change.stop="setOrderSelected(order.id, ($event.target as HTMLInputElement).checked)"
                  />
                </td>
                <td class="mono">{{ order.outboundNo }}</td>
                <td>{{ order.customerName }}</td>
                <td class="source-cell" :title="sourceText(order)">
                  <span class="cell-ellipsis">{{ sourceText(order) }}</span>
                </td>
                <td>{{ formatOutboundStatus(order.status) }}</td>
                <td>{{ order.items.reduce((sum, item) => sum + Number(item.boxCount ?? 0), 0) }}</td>
                <td>{{ order.items.reduce((sum, item) => sum + Number(item.plannedQty), 0).toFixed(3) }}</td>
                <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                <td class="action-cell" @click.stop>
                  <div class="row-actions">
                    <button class="secondary-button" @click="openOutboundDetail(order)">明细</button>
                    <button class="secondary-button" @click="openPrint(order)">打印</button>
                    <button class="secondary-button" @click="openScan(order)">扫码</button>
                    <button class="secondary-button danger-button" :disabled="!canCancelOutboundOrder(order)" @click="openCancelOrder(order)">取消</button>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="!rows.length">
              <td colspan="9" class="empty-cell">没有匹配的出库单</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <OutboundCreateModal
      :open="viewMode === 'create'"
      :model="model"
      @close="closeViewModal"
      @created="handleCreateCreated"
    />

    <OutboundOrderDetailModal
      :open="!!detailOrder"
      :order="detailOrder"
      :kanbans="model.state.kanbans"
      @close="closeOutboundDetail"
    />

    <PageModal :open="activeModal === 'scan'" wide @close="closeOperationModal">
      <section v-if="activeModal === 'scan'" class="panel operation-modal-panel outbound-scan-panel">
        <div class="section-head compact-modal-head">
          <div>
            <h3>扫码出库{{ activeScanOrder ? `：${activeScanOrder.outboundNo}` : '' }}</h3>
          </div>
        </div>
        <div class="outbound-scan-layout">
          <div class="outbound-scan-main">
            <div class="scan-bound-summary">
              <span>绑定单号：<strong class="mono">{{ activeScanOrder?.outboundNo ?? '-' }}</strong></span>
              <span>分配箱：{{ activeScanLines.length }}</span>
              <span>{{ outboundScanSummary }}</span>
            </div>

            <div class="form-grid three scan-control-grid">
              <label class="inline-field">
                <span>出库单</span>
                <select v-model="scanForm.outboundOrderNo" @change="handleScanOrderChange">
                  <option value="">选择出库单</option>
                  <option v-for="order in outboundOrderOptions" :key="order.id" :value="order.outboundNo">
                    {{ order.outboundNo }} | {{ order.items.length }} 项 | {{ formatOutboundStatus(order.status) }}
                  </option>
                </select>
              </label>
              <label class="inline-field">
                <span>扫码对象</span>
                <select v-model="scanForm.barcode" :disabled="!scanForm.outboundOrderNo">
                  <option value="">辅助选择出库单二维码或箱看板</option>
                  <option v-for="item in activeScanOrder ? outboundScanOptionsForOrder(activeScanOrder) : []" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </option>
                </select>
              </label>
              <label class="inline-field">
                <span>扫描枪输入</span>
                <input
                  ref="scanInputRef"
                  v-model="scanForm.barcode"
                  placeholder="扫描出库单二维码或箱看板"
                  :disabled="!scanForm.outboundOrderNo"
                  @keydown.enter.prevent="submitScanByEnter"
                />
              </label>
            </div>

            <div class="scan-line-toolbar">
              <div class="summary-strip">
                <span>待出库 {{ pendingScanLines.length }} 箱</span>
                <span>已选择 {{ selectedScanKanbanNos.length }} 箱</span>
                <span v-if="scannedOutboundKanban">当前箱：<strong class="mono">{{ scannedOutboundKanban.kanbanNo }}</strong></span>
              </div>
              <div class="action-row">
                <button class="secondary-button" :disabled="!pendingScanLines.length" @click="selectAllPendingOutboundLines">全选待出库</button>
                <button class="secondary-button" :disabled="!selectedScanKanbanNos.length" @click="clearOutboundScanLines">清空选择</button>
                <button class="secondary-button" :disabled="!scanForm.outboundOrderNo" @click="fillFirstOutboundScanCode">填充整单码</button>
                <button :disabled="!canSubmitOutboundScan" @click="submitScan">
                  {{ scanSubmitting ? '正在出库...' : '确认出库' }}
                </button>
              </div>
            </div>

            <div class="scan-line-table-wrap">
              <table class="table scan-line-table outbound-scan-table">
                <thead>
                  <tr>
                    <th>选择</th>
                    <th>箱看板</th>
                    <th>零件</th>
                    <th>分配</th>
                    <th>已出</th>
                    <th>剩余</th>
                    <th>状态</th>
                    <th>库区</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="line in activeScanLines"
                    :key="line.key"
                    :class="{ selected: isScanLineSelected(line.kanbanNo), disabled: !line.pending, success: scanSuccessMap[line.kanbanNo] }"
                    @click="line.pending && toggleScanLine(line.kanbanNo, !isScanLineSelected(line.kanbanNo))"
                  >
                    <td>
                      <input
                        class="compact-check"
                        type="checkbox"
                        :checked="isScanLineSelected(line.kanbanNo)"
                        :disabled="!line.pending"
                        @click.stop
                        @change.stop="toggleScanLine(line.kanbanNo, ($event.target as HTMLInputElement).checked)"
                      />
                    </td>
                    <td class="mono">{{ line.kanbanNo }}</td>
                    <td>{{ line.partText }}</td>
                    <td>{{ line.allocatedQty.toFixed(3) }} {{ line.unit }}</td>
                    <td>{{ line.outboundQty.toFixed(3) }} {{ line.unit }}</td>
                    <td>{{ line.remainingQty.toFixed(3) }} {{ line.unit }}</td>
                    <td>{{ formatStatus(line.status) }}</td>
                    <td>{{ line.locationText }}</td>
                  </tr>
                  <tr v-if="activeScanOrder && !activeScanLines.length">
                    <td colspan="8" class="empty-cell">该出库单没有分配箱看板</td>
                  </tr>
                  <tr v-else-if="activeScanOrder && !pendingScanLines.length">
                    <td colspan="8" class="empty-cell">该出库单没有待出库箱看板</td>
                  </tr>
                  <tr v-else-if="!activeScanOrder">
                    <td colspan="8" class="empty-cell">未选择出库单</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
            <p v-if="scanResultMessage" class="scan-result-message" :class="{ error: !scanResultMessage.includes('成功') }">{{ scanResultMessage }}</p>
          </div>

          <aside class="scan-side-card">
            <div v-if="scanForm.barcode" class="scan-qr-preview compact-qr-preview">
              <QrCodeImage :text="scanForm.barcode" :size="126" />
              <p class="mono">{{ scanForm.barcode }}</p>
            </div>
            <dl v-if="activeScanOrder" class="scan-order-info">
              <div><dt>出库单</dt><dd class="mono">{{ activeScanOrder.outboundNo }}</dd></div>
              <div><dt>客户</dt><dd>{{ activeScanOrder.customerName || '-' }}</dd></div>
              <div><dt>状态</dt><dd>{{ formatOutboundStatus(activeScanOrder.status) }}</dd></div>
              <div><dt>分配箱</dt><dd>{{ activeScanLines.length }} 箱</dd></div>
              <div><dt>待出库</dt><dd>{{ pendingScanLines.length }} 箱</dd></div>
              <div><dt>已选择</dt><dd>{{ selectedScanKanbanNos.length }} 箱</dd></div>
            </dl>
            <p v-else class="empty-cell">未选择出库单</p>
          </aside>
        </div>
      </section>
    </PageModal>

    <OutboundCancelModal
      :open="activeModal === 'cancel'"
      :mode="cancelMode"
      :orders="cancelOrders"
      :initial-selected-ids="cancelInitialSelectedIds"
      @close="closeOperationModal"
      @completed="completeOutboundCancel"
    />

    <PageModal :open="viewMode === 'manual'" @close="closeViewModal">
      <section class="panel manual-operation-panel">
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
        <button class="secondary-button" @click="closeViewModal">返回查询</button>
      </div>
      </section>
    </PageModal>

    <PageModal :open="activeModal === 'print' && !!printOrders.length" wide print-mode @close="closeOperationModal">
      <section class="panel print-panel operation-modal-panel wide-operation-modal">
        <div class="section-head print-toolbar">
          <div>
            <h3>出库打印</h3>
          </div>
          <div class="action-row print-actions">
            <button @click="browserPrint">浏览器打印</button>
          </div>
        </div>

        <div class="print-grid">
          <article v-for="order in printOrders" :key="order.id" class="print-order-card">
            <header class="print-order-header">
              <div>
                <strong class="mono">{{ order.outboundNo }}</strong>
                <span>{{ order.customerName || '未绑定客户' }}</span>
                <span>{{ formatOutboundStatus(order.status) }}</span>
                <span>FIFO 来源：{{ sourceText(order) }}</span>
              </div>
              <div class="order-qr-inline">
                <QrCodeImage :text="outboundOrderScanCode(order)" :size="74" />
                <span class="mono">{{ outboundOrderScanCode(order) }}</span>
              </div>
            </header>

            <div class="print-part-groups">
              <section v-for="group in groupedAllocationLines(order)" :key="group.key" class="print-part-group">
                <h4>{{ group.partText }}（{{ group.lines.length }} 箱 / {{ group.locationText }}）</h4>
                <div class="print-kanban-grid">
                  <article v-for="line in group.lines" :key="line.key" class="print-label-card box-label">
                    <div class="print-label-title">
                      <strong>出库箱看板</strong>
                      <span class="mono">{{ line.kanbanNo }}</span>
                    </div>
                    <div class="print-label-body compact-box-body">
                      <table class="print-info-table">
                        <tbody>
                          <tr><th>出库单</th><td class="mono">{{ order.outboundNo }}</td></tr>
                          <tr><th>入库单</th><td class="mono">{{ line.inboundNo }}</td></tr>
                          <tr><th>箱序</th><td>{{ line.boxIndex ? `第 ${line.boxIndex} 箱` : '-' }}</td></tr>
                          <tr><th>零件</th><td>{{ line.partText }}</td></tr>
                          <tr><th>分配</th><td>{{ line.allocatedQty.toFixed(3) }} {{ line.unit }}</td></tr>
                          <tr><th>已出</th><td>{{ line.outboundQty.toFixed(3) }} {{ line.unit }}</td></tr>
                          <tr><th>剩余</th><td>{{ line.remainingQty.toFixed(3) }} {{ line.unit }}</td></tr>
                          <tr><th>库区</th><td>{{ line.locationText }}</td></tr>
                          <tr><th>状态</th><td>{{ formatStatus(line.status) }}</td></tr>
                          <tr><th>条码</th><td class="mono">{{ line.barcode }}</td></tr>
                        </tbody>
                      </table>
                      <div class="print-qr-box box-qr">
                        <QrCodeImage :text="line.qrContent" :size="82" />
                      </div>
                    </div>
                  </article>
                </div>
              </section>
              <p v-if="!allocationLinesForOrder(order).length" class="empty-cell">该出库单暂无分配箱看板</p>
            </div>
          </article>
        </div>
      </section>
    </PageModal>
  </section>
</template>

<style scoped>
.summary-strip,
.pager-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-strip {
  margin-top: 12px;
}

.outbound-filter-panel {
  padding: 10px 12px;
}

.outbound-filter-line {
  display: grid;
  grid-template-columns: auto minmax(460px, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.outbound-filter-title h3 {
  margin: 0;
  white-space: nowrap;
}

.outbound-filter-row {
  display: grid;
  grid-template-columns: 108px minmax(170px, 1.1fr) minmax(130px, 0.8fr) 96px 58px 58px;
  gap: 6px;
  align-items: center;
}

.outbound-filter-row input,
.outbound-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.compact-filter-button {
  padding: 0 10px;
  white-space: nowrap;
}

.outbound-filter-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
}

.outbound-order-table {
  min-width: 1120px;
  table-layout: fixed;
}

.outbound-order-table th,
.outbound-order-table td {
  padding: 8px 10px;
  vertical-align: middle;
}

.outbound-order-table .select-col,
.outbound-order-table .select-cell {
  width: 36px;
  padding-inline: 6px;
  text-align: center;
}

.outbound-order-table .no-col {
  width: 150px;
}

.outbound-order-table .customer-col {
  width: 160px;
}

.outbound-order-table .source-col {
  width: 220px;
}

.outbound-order-table .status-col {
  width: 108px;
}

.outbound-order-table .count-col {
  width: 72px;
}

.outbound-order-table .qty-col {
  width: 112px;
}

.outbound-order-table .time-col {
  width: 178px;
}

.outbound-order-table .action-col {
  width: 176px;
  text-align: right;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
}

.outbound-order-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.outbound-order-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.outbound-order-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.outbound-order-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.outbound-order-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.row-actions button {
  min-height: 30px;
  padding: 0 8px;
  white-space: nowrap;
}

.action-cell {
  text-align: right;
  overflow: visible;
}

.outbound-order-row > td {
  height: 42px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.outbound-order-row > .action-cell {
  overflow: visible;
}

.cell-ellipsis {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.inline-field span {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.compact-modal-head {
  align-items: center;
  margin-bottom: 10px;
}

.compact-modal-head h3 {
  margin: 0;
  overflow-wrap: anywhere;
}

.outbound-scan-panel {
  padding: 14px;
  min-width: 0;
  max-width: 100%;
}

.outbound-scan-layout {
  display: grid;
  grid-template-columns: minmax(740px, 1fr) 270px;
  gap: 12px;
  align-items: start;
  overflow-x: auto;
}

.outbound-scan-main {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.scan-bound-summary,
.scan-line-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.scan-bound-summary {
  padding: 7px 9px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: rgba(14, 165, 233, 0.08);
  color: var(--text-secondary);
  font-size: 13px;
}

.scan-bound-summary span,
.summary-strip span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.scan-control-grid {
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 8px;
  align-items: end;
  overflow-x: auto;
}

.scan-control-grid input,
.scan-control-grid select {
  min-width: 0;
  width: 100%;
  min-height: 34px;
}

.scan-line-toolbar .summary-strip {
  margin-top: 0;
}

.scan-line-toolbar button {
  min-height: 32px;
  padding: 0 10px;
  white-space: nowrap;
}

.scan-line-table-wrap {
  max-height: min(430px, 48vh);
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  min-width: 0;
  max-width: 100%;
}

.scan-line-table {
  min-width: 1220px;
  table-layout: fixed;
}

.scan-line-table th,
.scan-line-table td {
  padding: 6px 7px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.scan-line-table th:first-child,
.scan-line-table td:first-child {
  width: 44px;
  text-align: center;
}

.scan-line-table th:nth-child(2),
.scan-line-table td:nth-child(2) {
  width: 208px;
}

.scan-line-table th:nth-child(4),
.scan-line-table td:nth-child(4),
.scan-line-table th:nth-child(5),
.scan-line-table td:nth-child(5),
.scan-line-table th:nth-child(6),
.scan-line-table td:nth-child(6),
.scan-line-table th:nth-child(7),
.scan-line-table td:nth-child(7) {
  width: 112px;
}

.scan-line-table th:nth-child(8),
.scan-line-table td:nth-child(8) {
  width: 210px;
}

.scan-line-table tr {
  cursor: pointer;
}

.scan-line-table tr.selected {
  background: rgba(20, 184, 166, 0.14);
}

.scan-line-table tr.success {
  background: rgba(22, 163, 74, 0.13);
}

.scan-line-table tr.disabled {
  cursor: not-allowed;
  color: var(--text-secondary);
  background: rgba(148, 163, 184, 0.08);
}

.scan-result-message {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid rgba(22, 163, 74, 0.24);
  border-radius: 8px;
  background: rgba(22, 163, 74, 0.1);
  color: #15803d;
}

.scan-result-message.error {
  border-color: rgba(220, 38, 38, 0.24);
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
}

.scan-side-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.92);
}

.compact-qr-preview {
  padding: 8px;
}

.scan-order-info {
  display: grid;
  gap: 0;
  margin: 0;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  overflow: hidden;
  font-size: 12px;
}

.scan-order-info div {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  border-bottom: 1px solid var(--border-color);
}

.scan-order-info div:last-child {
  border-bottom: 0;
}

.scan-order-info dt,
.scan-order-info dd {
  margin: 0;
  padding: 5px 6px;
  min-width: 0;
  overflow-wrap: anywhere;
}

.scan-order-info dt {
  color: var(--text-secondary);
  background: rgba(148, 163, 184, 0.1);
  font-weight: 700;
}

.print-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.print-panel {
  background: #f8fafc;
  max-height: none;
  overflow: visible;
}

.print-toolbar {
  margin-bottom: 10px;
}

.print-actions {
  flex-wrap: nowrap;
}

.print-order-card {
  display: grid;
  min-width: 0;
  gap: 10px;
  padding: 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #fff;
  break-inside: avoid;
}

.print-order-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
  color: #334155;
  font-size: 13px;
}

.print-order-header > div:first-child {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.print-order-header strong {
  color: #0f172a;
  font-size: 15px;
}

.order-qr-inline {
  display: grid;
  justify-items: center;
  gap: 2px;
  max-width: 140px;
  color: #475569;
  font-size: 9px;
  overflow-wrap: anywhere;
  text-align: center;
}

.print-part-groups {
  display: grid;
  gap: 8px;
}

.print-part-group {
  display: grid;
  gap: 6px;
  break-inside: avoid;
}

.print-part-group h4 {
  margin: 0;
  color: #0f172a;
  font-size: 12px;
}

.print-kanban-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 360px));
  gap: 8px;
  align-items: start;
  justify-content: start;
}

.print-label-card {
  border: 1px solid #334155;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;
  break-inside: avoid;
}

.box-label {
  min-height: 0;
}

.print-label-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 7px;
  border-bottom: 1px solid #334155;
  background: #e2e8f0;
  color: #0f172a;
  font-size: 12px;
  line-height: 1.2;
}

.print-label-title span,
.print-label-title strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.print-label-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) max-content;
  gap: 3px;
  padding: 4px;
  align-items: stretch;
}

.compact-box-body {
  grid-template-columns: minmax(0, 1fr) max-content;
}

.print-info-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 10px;
  line-height: 1.18;
}

.print-info-table th,
.print-info-table td {
  border: 1px solid #cbd5e1;
  padding: 2px 3px;
  text-align: left;
  vertical-align: middle;
  word-break: break-word;
}

.print-info-table th {
  width: 42px;
  background: #f1f5f9;
  color: #475569;
  font-weight: 700;
}

.print-info-table td {
  color: #0f172a;
  overflow-wrap: anywhere;
}

.print-qr-box {
  display: grid;
  align-content: center;
  justify-items: center;
  min-width: 0;
  border: 1px solid #cbd5e1;
  padding: 2px;
}

.print-qr-box :deep(.qr-wrap) {
  padding: 0;
  border: 0;
  border-radius: 0;
}

.operation-modal-panel {
  min-width: 0;
  max-width: 100%;
  overflow: auto;
}

.scan-hint {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 0 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.compact-pager button {
  min-height: 30px;
  padding: 0 10px;
}

.table-scroll {
  overflow-x: auto;
}

.order-table {
  min-width: 1180px;
  table-layout: fixed;
}

.outbound-order-table {
  min-width: 1320px;
}

.outbound-order-table th,
.outbound-order-table td {
  padding: 8px 10px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.outbound-order-table .select-col,
.outbound-order-table .select-cell {
  width: 36px;
  padding-inline: 6px;
  text-align: center;
}

.outbound-order-table th:nth-child(2),
.outbound-order-table td:nth-child(2) {
  width: 164px;
}

.outbound-order-table th:nth-child(3),
.outbound-order-table td:nth-child(3) {
  width: 170px;
}

.outbound-order-table th:nth-child(4),
.outbound-order-table td:nth-child(4) {
  width: 116px;
}

.outbound-order-table th:nth-child(5),
.outbound-order-table td:nth-child(5),
.outbound-order-table th:nth-child(6),
.outbound-order-table td:nth-child(6),
.outbound-order-table th:nth-child(7),
.outbound-order-table td:nth-child(7) {
  width: 104px;
}

.outbound-order-table th:nth-child(8),
.outbound-order-table td:nth-child(8) {
  width: 180px;
}

.outbound-order-table th:nth-child(9),
.outbound-order-table td:nth-child(9) {
  width: 240px;
  text-align: right;
}

.manual-operation-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.manual-operation-panel .form-grid.four {
  grid-template-columns: repeat(2, minmax(280px, 1fr));
}

.manual-operation-panel input,
.manual-operation-panel select {
  min-width: 0;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

.form-error {
  margin: 10px 0 0;
  color: #dc2626;
}

@media (max-width: 1100px) {
  .manual-operation-panel .form-grid.four {
    grid-template-columns: 1fr;
  }

  .outbound-scan-layout {
    grid-template-columns: 1fr;
  }

  .operation-modal-panel {
    max-height: calc(100vh - 20px);
  }
}

@media (max-width: 760px) {
  .print-kanban-grid,
  .print-label-body,
  .compact-box-body {
    grid-template-columns: 1fr;
  }
}

@media print {
  .print-panel {
    background: #fff;
    padding: 0;
    max-height: none !important;
    overflow: visible !important;
  }

  .print-toolbar {
    display: none !important;
  }

  .print-grid,
  .print-order-card,
  .print-part-group {
    gap: 6px;
  }

  .print-order-card {
    border: 0;
    padding: 0;
    page-break-inside: avoid;
  }

  .print-order-header {
    padding: 0 0 4px;
    font-size: 11px;
  }

  .print-kanban-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
  }

  .print-label-card {
    border-color: #111827;
    border-radius: 0;
    break-inside: avoid;
    page-break-inside: avoid;
  }

  .print-label-title {
    padding: 2px 4px;
    border-color: #111827;
    background: #e5e7eb !important;
    font-size: 10px;
  }

  .print-label-body,
  .compact-box-body {
    grid-template-columns: minmax(0, 1fr) max-content;
    gap: 2px;
    padding: 2px;
  }

  .print-info-table {
    font-size: 8px;
  }

  .print-info-table th,
  .print-info-table td {
    padding: 1px 2px;
  }

  .print-info-table th {
    width: 34px;
  }

  .print-qr-box {
    padding: 1px;
  }

  .print-qr-box :deep(.qr-image) {
    width: 72px !important;
  }
}
</style>
