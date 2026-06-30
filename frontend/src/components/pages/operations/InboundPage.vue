<!-- 本文件实现入库工作台，支持批量选件、入库单二维码打印，以及箱级看板扫码入库。 -->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatStatus } from '../../../app/displayText'
import { compareKanbanFifo, findKanbanByScanCode, findLocationForKanban } from '../../../app/kanbanHelpers'
import { equipmentCodeOptions, warehouseZoneOptions } from '../../../app/optionHelpers'
import PageModal from '../../shared/PageModal.vue'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import InboundOrderDetailModal from './inbound-modals/InboundOrderDetailModal.vue'
import InboundReturnModal from './inbound-modals/InboundReturnModal.vue'
import type { InboundDraftItem, InboundOrder, Kanban, PageModel, Part } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const createModalOpen = ref(false)
const printOrders = ref<InboundOrder[]>([])
const scanOrder = ref<InboundOrder | null>(null)
const detailOrder = ref<InboundOrder | null>(null)
const selectedOrderIds = reactive<Record<number, boolean>>({})
const scanInputRef = ref<HTMLInputElement | null>(null)
const batchPickerOpen = ref(false)
const activeModal = ref<'print' | 'scan' | 'return' | ''>('')
const returnOrders = ref<InboundOrder[]>([])
const returnInitialSelectedIds = ref<number[]>([])
const returnMode = ref<'single' | 'batch'>('single')
const batchSearch = ref('')
const batchPage = ref(1)
const batchPageSize = 12
const orders = ref<InboundOrder[]>([])
const orderPage = ref(1)
const orderPageSize = ref(20)
const orderTotal = ref(0)
const orderLoading = ref(false)
const orderError = ref('')
const formSubmitting = ref(false)
const loadingKanbanOrderIds = reactive<Record<number, boolean>>({})
const inboundKanbanCache = reactive<Record<string, Kanban[]>>({})

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
const batchDraft = reactive<Record<number, { plannedQty: number | null; boxCount: number | null; pendingRepack: boolean; equipmentCode: string; unitPerBox: number | null; warehouseZone: string }>>({})

const printedInboundScanForm = reactive({
  barcode: '',
  locationCode: '',
})

const selectedScanBoxIds = ref<number[]>([])
const scanSubmitting = ref(false)
const scanResultMessage = ref('')
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

const selectedBatchPartCount = computed(() => supplierParts.value.filter((part) => batchSelection[part.id]).length)
const filteredBatchPartCount = computed(() => filteredSupplierParts.value.length)
const selectedFilteredBatchPartCount = computed(() => filteredSupplierParts.value.filter((part) => batchSelection[part.id]).length)
const allFilteredBatchPartsSelected = computed(() =>
  filteredBatchPartCount.value > 0 && selectedFilteredBatchPartCount.value === filteredBatchPartCount.value,
)
const batchTotalPages = computed(() => Math.max(1, Math.ceil(filteredSupplierParts.value.length / batchPageSize)))
const pagedSupplierParts = computed(() => {
  const page = Math.min(batchPage.value, batchTotalPages.value)
  const start = (page - 1) * batchPageSize
  return filteredSupplierParts.value.slice(start, start + batchPageSize)
})

const filteredOrders = computed(() => orders.value)
const orderTotalPages = computed(() => Math.max(1, Math.ceil(orderTotal.value / orderPageSize.value)))
const selectedOrderCount = computed(() => filteredOrders.value.filter((item) => selectedOrderIds[item.id]).length)
const canBatchPrint = computed(() => selectedOrderCount.value > 0)
const canOpenBatchReturn = computed(() => filteredOrders.value.length > 0)
const scanOrderBoxes = computed(() => (scanOrder.value ? kanbansForInbound(scanOrder.value) : []))
const pendingScanBoxes = computed(() => scanOrderBoxes.value.filter(isInboundPendingKanban))
const scannedKanban = computed(() => findKanbanByScanCode(props.model.state.kanbans, printedInboundScanForm.barcode))
const scanBoxSummary = computed(() => {
  if (!scanOrder.value) return '请先选择入库单'
  if (!pendingScanBoxes.value.length) return '没有待入库箱看板'
  return `已选择 ${selectedScanBoxIds.value.length} / ${pendingScanBoxes.value.length} 箱`
})
const canSubmitInboundScan = computed(() => Boolean(printedInboundScanForm.barcode && selectedScanBoxIds.value.length && !scanSubmitting.value))

function mergeKanbanCache(kanbans: Kanban[]) {
  if (!kanbans.length) return
  const map = new Map(props.model.state.kanbans.map((item) => [item.id, item]))
  kanbans.forEach((item) => map.set(item.id, item))
  props.model.state.kanbans = Array.from(map.values()).sort(compareKanbanFifo)

  const byInboundNo = new Map<string, Kanban[]>()
  props.model.state.kanbans.forEach((item) => {
    if (!item.inboundNo) return
    const rows = byInboundNo.get(item.inboundNo) ?? []
    rows.push(item)
    byInboundNo.set(item.inboundNo, rows)
  })
  byInboundNo.forEach((rows, inboundNo) => {
    inboundKanbanCache[inboundNo] = rows.sort(compareKanbanFifo)
  })
}

async function loadKanbansForInbound(order: InboundOrder, force = false) {
  if (!order.inboundNo || (!force && inboundKanbanCache[order.inboundNo]?.length)) {
    return inboundKanbanCache[order.inboundNo] ?? []
  }
  if (loadingKanbanOrderIds[order.id]) {
    return inboundKanbanCache[order.inboundNo] ?? []
  }
  loadingKanbanOrderIds[order.id] = true
  try {
    const result = await api.listKanbansPage({
      inboundNo: order.inboundNo,
      page: 1,
      size: 500,
    })
    mergeKanbanCache(result.records)
    return inboundKanbanCache[order.inboundNo] ?? []
  } finally {
    loadingKanbanOrderIds[order.id] = false
  }
}

function ceilBoxCount(plannedQty: number, unitPerBox: number) {
  if (!plannedQty || !unitPerBox || plannedQty <= 0 || unitPerBox <= 0) return 0
  return Math.max(1, Math.ceil(plannedQty / unitPerBox))
}

function equipmentCapacity(equipmentCode?: string | null) {
  if (!equipmentCode) return 0
  const equipment = props.model.state.equipment.find((item) => item.equipmentCode === equipmentCode)
  const capacity = Number(equipment?.capacity ?? 0)
  return Number.isFinite(capacity) && capacity > 0 ? capacity : 0
}

function partDefaultCapacity(partId: number) {
  const part = props.model.state.parts.find((item) => item.id === partId)
  const capacity = Number(part?.defaultUnitPerBox ?? 0)
  return Number.isFinite(capacity) && capacity > 0 ? capacity : 0
}

function resolvedUnitPerBox(partId: number, equipmentCode?: string | null) {
  return equipmentCapacity(equipmentCode) || partDefaultCapacity(partId)
}

function syncBoxPlan(target: { partId: number; plannedQty: number | null; boxCount: number | null; unitPerBox: number | null; equipmentCode: string }) {
  const unitPerBox = resolvedUnitPerBox(target.partId, target.equipmentCode)
  target.unitPerBox = unitPerBox > 0 ? unitPerBox : null
  target.boxCount = unitPerBox > 0 ? ceilBoxCount(Number(target.plannedQty), unitPerBox) || null : null
}

function syncBatchBoxPlan(part: Part) {
  const draft = ensureBatchDraft(part)
  const unitPerBox = resolvedUnitPerBox(part.id, draft.equipmentCode)
  draft.unitPerBox = unitPerBox > 0 ? unitPerBox : null
  if (!Number(draft.plannedQty) && unitPerBox > 0) {
    draft.plannedQty = unitPerBox
  }
  draft.boxCount = unitPerBox > 0 ? ceilBoxCount(Number(draft.plannedQty), unitPerBox) || null : null
}

function defaultWarehouseZoneForEquipment(equipmentCode?: string | null) {
  if (!equipmentCode) return ''
  const equipment = props.model.state.equipment.find((item) => item.equipmentCode === equipmentCode)
  if (!equipment?.warehouseName || !equipment.zoneName) return ''
  return `${equipment.warehouseName} / ${equipment.zoneName}`
}

function isOutsourcedPart(part?: Part | null) {
  return (part?.categoryCode || 'DEFAULT').toUpperCase() === 'OUTSOURCED'
}

function defaultWarehouseZoneByPart(part?: Part | null) {
  const fromEquipment = defaultWarehouseZoneForEquipment(part?.defaultEquipmentCode)
  const expectedType = isOutsourcedPart(part) ? 'THIRD_PARTY' : 'OWN'
  const matchedByEquipment = inboundWarehouseZoneOptions.value.find((item) => item.value === fromEquipment && item.warehouseType === expectedType)
  return matchedByEquipment?.value ?? inboundWarehouseZoneOptions.value.find((item) => item.warehouseType === expectedType)?.value ?? fromEquipment
}

function isThirdPartyWarehouseZone(warehouseZone?: string | null) {
  if (!warehouseZone) return false
  return inboundWarehouseZoneOptions.value.some((item) => item.value === warehouseZone && item.warehouseType === 'THIRD_PARTY')
}

function syncPendingRepackByWarehouseZone(target: { warehouseZone: string; pendingRepack: boolean }) {
  target.pendingRepack = isThirdPartyWarehouseZone(target.warehouseZone)
}

function repackText(warehouseZone?: string | null) {
  return isThirdPartyWarehouseZone(warehouseZone) ? '自动转包' : '自己仓'
}

function createDraftItem(part?: Part): InboundDraftItem {
  const equipmentCode = part?.defaultEquipmentCode ?? ''
  const unitPerBox = resolvedUnitPerBox(part?.id ?? 0, equipmentCode)
  return {
    partId: part?.id ?? 0,
    plannedQty: null,
    boxCount: null,
    pendingRepack: false,
    equipmentCode,
    unitPerBox: unitPerBox > 0 ? unitPerBox : null,
    warehouseZone: defaultWarehouseZoneByPart(part),
  }
}

function ensureBatchDraft(part: Part) {
  if (!batchDraft[part.id]) {
    batchDraft[part.id] = {
      plannedQty: resolvedUnitPerBox(part.id, part.defaultEquipmentCode) || null,
      boxCount: resolvedUnitPerBox(part.id, part.defaultEquipmentCode) ? 1 : null,
      pendingRepack: false,
      equipmentCode: part.defaultEquipmentCode ?? '',
      unitPerBox: resolvedUnitPerBox(part.id, part.defaultEquipmentCode) || null,
      warehouseZone: defaultWarehouseZoneByPart(part),
    }
    syncPendingRepackByWarehouseZone(batchDraft[part.id])
  }
  return batchDraft[part.id]
}

function batchUnitPerBox(part: Part) {
  const draft = ensureBatchDraft(part)
  return Number(draft.unitPerBox ?? 0)
}

function batchBoxCount(part: Part) {
  const draft = ensureBatchDraft(part)
  return Number(draft.boxCount ?? 0)
}

function itemUnitPerBox(item: InboundDraftItem) {
  return Number(item.unitPerBox ?? 0)
}

function syncItemUnitPerBox(item: InboundDraftItem) {
  syncBoxPlan(item)
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

function clearSelectedOrders() {
  Object.keys(selectedOrderIds).forEach((key) => delete selectedOrderIds[Number(key)])
}

async function loadOrders(page = orderPage.value) {
  const nextPage = Math.max(1, page)
  orderLoading.value = true
  orderError.value = ''
  try {
    const result = await api.listInboundOrdersPage({
      status: filters.status,
      supplierId: filters.supplierId,
      inboundNo: filters.inboundNo.trim(),
      page: nextPage,
      size: orderPageSize.value,
    })
    orders.value = result.records
    orderPage.value = result.page
    orderTotal.value = result.total
    if (!result.records.length && result.total > 0 && result.page > 1) {
      await loadOrders(Math.max(1, result.totalPages))
      return
    }
    clearSelectedOrders()
  } catch (error) {
    orderError.value = error instanceof Error ? error.message : '入库单查询失败'
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
  filters.supplierId = 0
  filters.inboundNo = ''
  await loadOrders(1)
}

function resetBatchSelection() {
  Object.keys(batchSelection).forEach((key) => delete batchSelection[Number(key)])
  batchSearch.value = ''
  batchPage.value = 1
  batchPickerOpen.value = false
}

function resetCreateForm() {
  form.supplierId = 0
  form.items = [createDraftItem()]
  formMessage.value = ''
  resetBatchSelection()
}

function openCreate() {
  resetCreateForm()
  scanOrder.value = null
  createModalOpen.value = true
}

function closeCreateModal() {
  createModalOpen.value = false
}

async function openPrint(order: InboundOrder) {
  await loadKanbansForInbound(order)
  printOrders.value = [order]
  scanOrder.value = null
  activeModal.value = 'print'
}

async function openBatchPrint() {
  printOrders.value = filteredOrders.value.filter((item) => selectedOrderIds[item.id])
  await Promise.all(printOrders.value.map((order) => loadKanbansForInbound(order)))
  scanOrder.value = null
  activeModal.value = 'print'
}

async function openScan(order: InboundOrder) {
  scanOrder.value = order
  printedInboundScanForm.barcode = inboundOrderScanCode(order)
  printedInboundScanForm.locationCode = ''
  selectedScanBoxIds.value = []
  scanResultMessage.value = ''
  scanMatchHint.value = ''
  activeModal.value = 'scan'
  await loadKanbansForInbound(order, true)
  const pendingBoxes = pendingBoxesForInbound(order)
  selectedScanBoxIds.value = pendingBoxes.map((box) => box.id)
  printedInboundScanForm.locationCode = ''
  if (pendingBoxes.length) {
    scanMatchHint.value = `已自动选择入库单 ${order.inboundNo} 下 ${pendingBoxes.length} 个待入库箱。`
  } else {
    scanMatchHint.value = '本入库单没有待入库箱看板。'
  }
}

function closeOperationModal() {
  activeModal.value = ''
  printedInboundScanForm.barcode = ''
  printedInboundScanForm.locationCode = ''
  selectedScanBoxIds.value = []
  scanResultMessage.value = ''
  scanMatchHint.value = ''
  returnOrders.value = []
  returnInitialSelectedIds.value = []
}

function canReturnInboundOrder(order: InboundOrder) {
  return order.status === 'CREATED'
}

function openInboundReturn(order: InboundOrder) {
  returnMode.value = 'single'
  returnOrders.value = canReturnInboundOrder(order) ? [order] : []
  returnInitialSelectedIds.value = canReturnInboundOrder(order) ? [order.id] : []
  activeModal.value = 'return'
}

function openBatchReturn() {
  returnMode.value = 'batch'
  const eligibleOrders = filteredOrders.value.filter(canReturnInboundOrder)
  returnOrders.value = eligibleOrders
  returnInitialSelectedIds.value = eligibleOrders.filter((order) => selectedOrderIds[order.id]).map((order) => order.id)
  activeModal.value = 'return'
}

async function completeInboundReturn() {
  window.dispatchEvent(new CustomEvent('wms-business-changed'))
  await loadOrders(orderPage.value)
  closeOperationModal()
}

function toggleSelectAllOrders(checked: boolean) {
  filteredOrders.value.forEach((item) => {
    selectedOrderIds[item.id] = checked
  })
}

function setOrderSelected(orderId: number, checked: boolean) {
  selectedOrderIds[orderId] = checked
}

function toggleOrderSelected(orderId: number) {
  selectedOrderIds[orderId] = !selectedOrderIds[orderId]
}

async function openInboundDetail(order: InboundOrder) {
  detailOrder.value = order
  await loadKanbansForInbound(order)
}

function closeInboundDetail() {
  detailOrder.value = null
}

function openBatchPicker() {
  supplierParts.value.forEach(syncBatchBoxPlan)
  batchPickerOpen.value = true
  batchPage.value = 1
}

function toggleBatchPart(partId: number) {
  batchSelection[partId] = !batchSelection[partId]
}

function setBatchPartSelected(partId: number, selected: boolean) {
  batchSelection[partId] = selected
}

function setFilteredBatchPartsSelected(selected: boolean) {
  filteredSupplierParts.value.forEach((part) => {
    ensureBatchDraft(part)
    syncBatchBoxPlan(part)
    batchSelection[part.id] = selected
  })
}

function handleBatchSupplierChange() {
  batchPage.value = 1
  batchSearch.value = ''
  resetBatchSelectionOnly()
  supplierParts.value.forEach((part) => {
    ensureBatchDraft(part)
    syncBatchBoxPlan(part)
  })
  if (form.supplierId) {
    setFilteredBatchPartsSelected(true)
  }
}

function resetBatchSelectionOnly() {
  Object.keys(batchSelection).forEach((key) => delete batchSelection[Number(key)])
}

function applyBatchParts() {
  supplierParts.value
    .filter((part) => batchSelection[part.id])
    .forEach((part) => {
      const draft = ensureBatchDraft(part)
      syncBatchBoxPlan(part)
      syncPendingRepackByWarehouseZone(draft)
      form.items.push({
        partId: part.id,
        plannedQty: draft.plannedQty,
        boxCount: draft.boxCount,
        pendingRepack: draft.pendingRepack,
        equipmentCode: draft.equipmentCode,
        unitPerBox: draft.unitPerBox,
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
  item.equipmentCode = part.defaultEquipmentCode ?? ''
  item.warehouseZone = defaultWarehouseZoneByPart(part)
  syncPendingRepackByWarehouseZone(item)
  syncItemUnitPerBox(item)
}

function handleItemEquipmentChange(item: InboundDraftItem) {
  item.warehouseZone = defaultWarehouseZoneForEquipment(item.equipmentCode)
  syncPendingRepackByWarehouseZone(item)
  syncItemUnitPerBox(item)
}

function handleItemWarehouseZoneChange(item: InboundDraftItem) {
  syncPendingRepackByWarehouseZone(item)
}

function handleBatchEquipmentChange(part: Part) {
  const draft = ensureBatchDraft(part)
  draft.warehouseZone = defaultWarehouseZoneForEquipment(draft.equipmentCode)
  syncPendingRepackByWarehouseZone(draft)
  syncBatchBoxPlan(part)
}

function handleBatchWarehouseZoneChange(part: Part) {
  syncPendingRepackByWarehouseZone(ensureBatchDraft(part))
}

type CompleteInboundDraftItem = InboundDraftItem & {
  plannedQty: number
  boxCount: number
  unitPerBox: number
}

function isCompleteInboundDraftItem(item: InboundDraftItem): item is CompleteInboundDraftItem {
  return item.partId > 0
    && Number(item.plannedQty) > 0
    && Number(item.boxCount) > 0
    && Number(item.unitPerBox) > 0
    && Boolean(item.warehouseZone)
}

function resolveInboundSupplierId(validItems: CompleteInboundDraftItem[]) {
  if (form.supplierId) return form.supplierId
  const supplierIds = new Set(
    validItems
      .map((item) => props.model.state.parts.find((part) => part.id === item.partId)?.supplierId)
      .filter((supplierId): supplierId is number => typeof supplierId === 'number' && supplierId > 0),
  )
  return supplierIds.size === 1 ? Array.from(supplierIds)[0] : 0
}

async function submit() {
  if (formSubmitting.value) return
  form.items.forEach(syncItemUnitPerBox)
  form.items.forEach(syncPendingRepackByWarehouseZone)
  formMessage.value = ''
  const validItems = form.items.filter(isCompleteInboundDraftItem)
  if (!validItems.length) {
    formMessage.value = '请至少添加一条完整入库明细：零件、数量、箱数、每箱数量和目标仓区都不能为空。'
    return
  }
  const supplierId = resolveInboundSupplierId(validItems)
  if (!supplierId) {
    formMessage.value = '请选择供应商，或只选择同一供应商下的零件后保存。'
    return
  }
  formSubmitting.value = true
  try {
    await props.model.actions.createInboundOrder({
      supplierId,
      items: validItems.map((item) => ({
        ...item,
        plannedQty: Number(item.plannedQty),
        boxCount: Number(item.boxCount),
        unitPerBox: Number(item.unitPerBox),
      })),
    })
    await loadOrders(1)
    closeCreateModal()
  } catch (error) {
    formMessage.value = error instanceof Error ? error.message : '入库单创建失败，请稍后重试。'
  } finally {
    formSubmitting.value = false
  }
}

function waitFrame() {
  return new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
}

function printQrCount() {
  return printOrders.value.reduce((sum, order) => sum + 1 + kanbansForInbound(order).length, 0)
}

async function waitForPrintQrImages(expectedCount: number) {
  for (let index = 0; index < 20; index += 1) {
    await nextTick()
    await waitFrame()
    const images = Array.from(document.querySelectorAll<HTMLImageElement>('.print-panel .qr-image'))
    if (images.length >= expectedCount && images.every((image) => image.complete && image.naturalWidth > 0)) {
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 50))
  }
}

async function browserPrint() {
  document.body.classList.add('printing-operation-modal')
  await waitForPrintQrImages(printQrCount())
  window.addEventListener(
    'afterprint',
    () => {
      document.body.classList.remove('printing-operation-modal')
    },
    { once: true },
  )
  window.print()
  window.setTimeout(() => {
    document.body.classList.remove('printing-operation-modal')
  }, 800)
}

function inboundOrderScanCode(order: InboundOrder) {
  return order.qrContent || `WMS-INBOUND|${order.inboundNo}`
}

function kanbansForInbound(order: InboundOrder) {
  const cached = inboundKanbanCache[order.inboundNo]
  const source = cached?.length ? cached : props.model.state.kanbans
  return source
    .filter((item) => item.inboundNo === order.inboundNo)
    .sort(compareKanbanFifo)
}

function pendingBoxesForInbound(order: InboundOrder) {
  return kanbansForInbound(order).filter(isInboundPendingKanban)
}

function groupedInboundKanbans(order: InboundOrder) {
  const groups = new Map<string, { key: string; partText: string; boxes: Kanban[] }>()
  kanbansForInbound(order).forEach((kanban) => {
    const key = `${kanban.partId}:${kanban.partCode}`
    const group = groups.get(key) ?? {
      key,
      partText: `${kanban.partCode} | ${kanban.partName}`,
      boxes: [],
    }
    group.boxes.push(kanban)
    groups.set(key, group)
  })
  return Array.from(groups.values())
}

function scanCodeForKanban(kanban: Kanban) {
  return kanban.qrContent || kanban.barcode
}

function isInboundPendingKanban(kanban: Kanban) {
  return ['CREATED', 'WAIT_SCAN'].includes(kanban.status)
}

function isScanBoxSelected(boxId: number) {
  return selectedScanBoxIds.value.includes(boxId)
}

function toggleScanBox(boxId: number, checked: boolean) {
  if (checked) {
    selectedScanBoxIds.value = Array.from(new Set([...selectedScanBoxIds.value, boxId]))
    return
  }
  selectedScanBoxIds.value = selectedScanBoxIds.value.filter((id) => id !== boxId)
}

function selectAllPendingScanBoxes() {
  selectedScanBoxIds.value = pendingScanBoxes.value.map((box) => box.id)
}

function clearScanBoxes() {
  selectedScanBoxIds.value = []
}

async function syncScanSelectionByCode(scanCode: string) {
  const normalized = scanCode.trim()
  scanResultMessage.value = ''
  if (!normalized) {
    printedInboundScanForm.locationCode = ''
    selectedScanBoxIds.value = []
    scanMatchHint.value = ''
    return
  }
  if (scanOrder.value) {
    await loadKanbansForInbound(scanOrder.value)
  }
  const scannedKanban = findKanbanByScanCode(props.model.state.kanbans, normalized)
  if (scanOrder.value && normalized === inboundOrderScanCode(scanOrder.value)) {
    const pendingBoxes = pendingBoxesForInbound(scanOrder.value)
    selectedScanBoxIds.value = pendingBoxes.map((box) => box.id)
    scanMatchHint.value = pendingBoxes.length
      ? `已识别入库单二维码，默认选择 ${pendingBoxes.length} 个待入库箱。`
      : '当前入库单没有待入库箱。'
    return
  }
  if (!scannedKanban) {
    selectedScanBoxIds.value = []
    scanMatchHint.value = '未识别到对应看板'
    return
  }
  if (scanOrder.value && scannedKanban.inboundNo !== scanOrder.value.inboundNo) {
    selectedScanBoxIds.value = []
    scanMatchHint.value = `箱看板 ${scannedKanban.kanbanNo} 不属于当前入库单 ${scanOrder.value.inboundNo}`
    return
  }
  if (!isInboundPendingKanban(scannedKanban)) {
    selectedScanBoxIds.value = []
    scanMatchHint.value = `箱看板 ${scannedKanban.kanbanNo} 当前状态为 ${formatStatus(scannedKanban.status)}，不能重复入库。`
    return
  }
  const location = findLocationForKanban(props.model.state.locations, scannedKanban)
  printedInboundScanForm.locationCode = location?.locationCode ?? printedInboundScanForm.locationCode
  selectedScanBoxIds.value = [scannedKanban.id]
  scanMatchHint.value = `已识别箱看板 ${scannedKanban.kanbanNo}，将入库 1 箱。`
}

async function focusScanInput() {
  await nextTick()
  scanInputRef.value?.focus()
  scanInputRef.value?.select()
}

async function submitPrintedInboundScan() {
  scanResultMessage.value = ''
  if (!selectedScanBoxIds.value.length) {
    scanMatchHint.value = pendingScanBoxes.value.length ? '请至少选择一个待入库箱看板。' : '当前入库单没有待入库箱看板。'
    return
  }
  const successCode = printedInboundScanForm.barcode
  scanSubmitting.value = true
  try {
    const result = await props.model.actions.scanInboundBatch({
      scanCode: successCode,
      locationCode: printedInboundScanForm.locationCode,
      kanbanIds: [...selectedScanBoxIds.value],
    })
    result.affectedKanbanNos.forEach((kanbanNo) => {
      scanSuccessMap[kanbanNo] = true
    })
    scanSuccessMap[successCode] = true
    await loadOrders(orderPage.value)
    if (scanOrder.value) {
      const refreshedOrder = orders.value.find((order) => order.id === scanOrder.value?.id)
      if (refreshedOrder) {
        scanOrder.value = refreshedOrder
      }
      await loadKanbansForInbound(scanOrder.value, true)
      const pendingBoxes = pendingBoxesForInbound(scanOrder.value)
      selectedScanBoxIds.value = pendingBoxes.map((box) => box.id)
      printedInboundScanForm.barcode = inboundOrderScanCode(scanOrder.value)
    }
    scanResultMessage.value = `${result.message}：${result.affectedKanbanNos.join('、')}`
  } catch (error) {
    scanResultMessage.value = error instanceof Error ? error.message : '入库失败，请检查看板和库位状态。'
  } finally {
    scanSubmitting.value = false
    await focusScanInput()
  }
}

async function submitInboundScanByEnter() {
  if (!printedInboundScanForm.barcode) return
  await submitPrintedInboundScan()
}

watch(activeModal, async (value) => {
  if (value === 'scan' || value === 'print') {
    await focusScanInput()
  }
})

watch(orderPageSize, async () => {
  await loadOrders(1)
})

watch(
  () => printedInboundScanForm.barcode,
  (value) => {
    if (activeModal.value !== 'scan') return
    if (!value.trim()) {
      printedInboundScanForm.locationCode = ''
      selectedScanBoxIds.value = []
      scanMatchHint.value = ''
      return
    }
    void syncScanSelectionByCode(value)
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
      <section class="panel inbound-filter-panel">
        <div class="inbound-filter-line">
          <div class="inbound-filter-title">
            <h3>入库筛选</h3>
          </div>
          <div class="inbound-filter-row">
            <select v-model="filters.status">
              <option value="">全部状态</option>
              <option value="CREATED">{{ formatStatus('CREATED') }}</option>
              <option value="PARTIAL">{{ formatStatus('PARTIAL') }}</option>
              <option value="COMPLETED">{{ formatStatus('COMPLETED') }}</option>
              <option value="RETURNED">{{ formatStatus('RETURNED') }}</option>
            </select>
            <select v-model.number="filters.supplierId">
              <option :value="0">全部供应商</option>
              <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
                {{ item.supplierCode }} | {{ item.supplierName }}
              </option>
            </select>
            <input v-model="filters.inboundNo" placeholder="入库单号" @keydown.enter.prevent="searchOrders" />
            <select v-model.number="orderPageSize" title="每页数量">
              <option :value="10">10条/页</option>
              <option :value="20">20条/页</option>
              <option :value="50">50条/页</option>
              <option :value="100">100条/页</option>
            </select>
            <button class="secondary-button compact-filter-button" :disabled="orderLoading" @click="searchOrders">查询</button>
            <button class="secondary-button compact-filter-button" :disabled="orderLoading" @click="resetFilters">重置</button>
          </div>
          <div class="action-row inbound-filter-actions">
            <button @click="openCreate">创建入库单</button>
            <button class="batch-action-button" :disabled="!canBatchPrint" @click="openBatchPrint">批量打印</button>
            <button class="secondary-button batch-action-button" :disabled="!canOpenBatchReturn" @click="openBatchReturn">批量退回</button>
          </div>
        </div>
      </section>

      <section class="panel table-scroll">
        <div class="table-toolbar">
          <span>{{ orderLoading ? '正在查询入库单...' : '共 ' + orderTotal + ' 条，第 ' + orderPage + ' / ' + orderTotalPages + ' 页' }}</span>
          <span v-if="orderError" class="form-error">{{ orderError }}</span>
          <div class="pager-actions compact-pager">
            <button class="secondary-button" :disabled="orderLoading || orderPage <= 1" @click="changeOrderPage(orderPage - 1)">上一页</button>
            <button class="secondary-button" :disabled="orderLoading || orderPage >= orderTotalPages" @click="changeOrderPage(orderPage + 1)">下一页</button>
          </div>
        </div>
        <table class="table inbound-order-table">
          <thead>
            <tr>
              <th class="select-col">
                <input class="compact-check" type="checkbox" :checked="selectedOrderCount === filteredOrders.length && filteredOrders.length > 0" @change="toggleSelectAllOrders(($event.target as HTMLInputElement).checked)" />
              </th>
              <th class="no-col">入库单号</th>
              <th class="supplier-col">供应商</th>
              <th class="status-col">状态</th>
              <th class="count-col">明细数</th>
              <th class="count-col">箱数</th>
              <th class="qty-col">计划数量</th>
              <th class="qty-col">已入库数量</th>
              <th class="time-col">创建时间</th>
              <th class="action-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(order, index) in filteredOrders" :key="order.id">
              <tr
                class="inbound-order-row"
                :class="{
                  selected: selectedOrderIds[order.id],
                  'tone-a': Math.floor(index / 2) % 2 === 0,
                  'tone-b': Math.floor(index / 2) % 2 === 1,
                }"
                @click="toggleOrderSelected(order.id)"
              >
                <td class="select-cell">
                  <input class="compact-check" type="checkbox" :checked="!!selectedOrderIds[order.id]" @click.stop @change.stop="setOrderSelected(order.id, ($event.target as HTMLInputElement).checked)" />
                </td>
                <td class="mono">{{ order.inboundNo }}</td>
                <td>{{ order.supplierName }}</td>
                <td>{{ formatStatus(order.status) }}</td>
                <td>{{ order.items.length }}</td>
                <td>{{ order.items.reduce((sum, item) => sum + Number(item.boxCount), 0) }}</td>
                <td>{{ orderQty(order, 'plannedQty').toFixed(3) }}</td>
                <td>{{ orderQty(order, 'receivedQty').toFixed(3) }}</td>
                <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                <td class="action-cell" @click.stop>
                  <div class="row-actions">
                    <button class="secondary-button" @click="openInboundDetail(order)">明细</button>
                    <button class="secondary-button" @click="openPrint(order)">打印</button>
                    <button class="secondary-button" @click="openScan(order)">扫码入库</button>
                    <button v-if="canReturnInboundOrder(order)" class="secondary-button danger-button" @click="openInboundReturn(order)">退回</button>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="!orderLoading && !filteredOrders.length">
              <td colspan="10" class="empty-cell">没有匹配的入库单</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <PageModal :open="createModalOpen" wide @close="closeCreateModal">
      <section class="panel create-inbound-panel">
        <div class="section-head">
          <div>
            <h3>创建入库单</h3>
          </div>
          <div class="action-row">
            <button class="secondary-button" @click="openBatchPicker">批量选件</button>
            <button class="secondary-button" @click="addItem">新增一行</button>
          </div>
        </div>

        <div class="create-inbound-filter-row">
          <select v-model.number="form.supplierId" aria-label="供应商">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <span class="create-inline-summary">可选零件 {{ supplierParts.length }} 个，已勾选 {{ supplierParts.filter((part) => batchSelection[part.id]).length }} 个</span>
        </div>

        <div class="detail-stack create-detail-table">
          <div class="detail-row detail-header-row">
            <span>零件</span>
            <span>总数量</span>
            <span>箱数</span>
            <span>容器容量</span>
            <span>器具编码</span>
            <span>目标仓区</span>
            <span>仓库性质</span>
            <span>操作</span>
          </div>
          <div v-for="(item, index) in form.items" :key="String(item.partId) + '-' + index" class="detail-row detail-data-row">
            <select v-model.number="item.partId" @change="handlePartChange(item)">
              <option :value="0">选择零件</option>
              <option v-for="part in supplierParts" :key="part.id" :value="part.id">
                {{ part.partCode }} | {{ part.partName }}
              </option>
            </select>
            <input v-model.number="item.plannedQty" type="number" min="0.001" step="0.001" placeholder="填写总数量" @input="syncItemUnitPerBox(item)" />
            <input :value="item.boxCount ?? ''" type="number" readonly placeholder="自动向上取整" />
            <input :value="itemUnitPerBox(item)" type="number" readonly placeholder="来自器具容量" />
            <select v-model="item.equipmentCode" @change="handleItemEquipmentChange(item)">
              <option value="">选择器具</option>
              <option v-for="opt in inboundEquipmentOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <select v-model="item.warehouseZone" @change="handleItemWarehouseZoneChange(item)">
              <option value="">选择仓区</option>
              <option v-for="opt in inboundWarehouseZoneOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <span class="repack-badge" :class="{ active: item.pendingRepack }">{{ repackText(item.warehouseZone) }}</span>
            <button class="secondary-button row-delete-button" title="删除本行" @click="removeItem(index)">删</button>
          </div>
        </div>
        <p v-if="formMessage" class="form-error">{{ formMessage }}</p>
        <div class="footer-actions">
          <button :disabled="formSubmitting" @click="submit">{{ formSubmitting ? '正在创建...' : '保存入库单' }}</button>
        </div>
      </section>
    </PageModal>

    <PageModal :open="activeModal === 'scan' || activeModal === 'print'" wide :print-mode="activeModal === 'print'" @close="closeOperationModal">
      <section v-if="activeModal === 'scan'" class="panel operation-modal-panel inbound-scan-panel">
        <div class="section-head compact-modal-head">
          <div>
            <h3>扫码入库{{ scanOrder ? '：' + scanOrder.inboundNo : '' }}</h3>
          </div>
        </div>
        <div class="inbound-scan-layout">
          <div class="inbound-scan-main">
            <div class="scan-bound-summary">
              <span>绑定单号：<strong class="mono">{{ scanOrder?.inboundNo ?? '-' }}</strong></span>
              <span>箱看板：{{ scanOrderBoxes.length }}</span>
              <span>{{ scanBoxSummary }}</span>
            </div>

            <div class="form-grid three scan-control-grid">
              <label class="inline-field">
                <span>入库单二维码</span>
                <select v-model="printedInboundScanForm.barcode">
                  <option v-if="scanOrder" :value="inboundOrderScanCode(scanOrder)">
                    {{ scanOrder.inboundNo }} | 全部待入库箱
                  </option>
                  <option v-for="kanban in scanOrderBoxes" :key="kanban.id" :value="scanCodeForKanban(kanban)">
                    {{ kanban.kanbanNo }} | {{ kanban.partCode }} | {{ formatStatus(kanban.status) }}
                  </option>
                </select>
              </label>
              <label class="inline-field">
                <span>入库库位</span>
                <select v-model="printedInboundScanForm.locationCode">
                  <option value="">后端按计划库区自动匹配</option>
                  <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
                    {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
                  </option>
                </select>
              </label>
              <label class="inline-field">
                <span>扫描枪输入</span>
                <input
                  ref="scanInputRef"
                  v-model="printedInboundScanForm.barcode"
                  placeholder="扫描入库单二维码或箱看板"
                  @keydown.enter.prevent="submitInboundScanByEnter"
                />
              </label>
            </div>

            <div class="scan-box-toolbar">
              <div class="summary-strip">
                <span>待入库 {{ pendingScanBoxes.length }} 箱</span>
                <span>已选择 {{ selectedScanBoxIds.length }} 箱</span>
                <span v-if="scannedKanban">当前箱：<strong class="mono">{{ scannedKanban.kanbanNo }}</strong></span>
              </div>
              <div class="action-row">
                <button class="secondary-button" :disabled="!pendingScanBoxes.length" @click="selectAllPendingScanBoxes">全选待入库</button>
                <button class="secondary-button" :disabled="!selectedScanBoxIds.length" @click="clearScanBoxes">清空选择</button>
                <button :disabled="!canSubmitInboundScan" @click="submitPrintedInboundScan">
                  {{ scanSubmitting ? '正在入库...' : '确认入库' }}
                </button>
              </div>
            </div>

            <div class="scan-box-table-wrap">
              <table class="table scan-box-table">
                <thead>
                  <tr>
                    <th>选择</th>
                    <th>箱号</th>
                    <th>箱序</th>
                    <th>零件</th>
                    <th>数量</th>
                    <th>状态</th>
                    <th>库区</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="box in scanOrderBoxes"
                    :key="box.id"
                    :class="{ selected: isScanBoxSelected(box.id), disabled: !isInboundPendingKanban(box), success: scanSuccessMap[box.kanbanNo] }"
                    @click="isInboundPendingKanban(box) && toggleScanBox(box.id, !isScanBoxSelected(box.id))"
                  >
                    <td>
                      <input
                        class="compact-check"
                        type="checkbox"
                        :checked="isScanBoxSelected(box.id)"
                        :disabled="!isInboundPendingKanban(box)"
                        @click.stop
                        @change.stop="toggleScanBox(box.id, ($event.target as HTMLInputElement).checked)"
                      />
                    </td>
                    <td class="mono">{{ box.kanbanNo }}</td>
                    <td>第 {{ box.boxIndex }} 箱</td>
                    <td>{{ box.partCode }} | {{ box.partName }}</td>
                    <td>{{ box.qty }} {{ box.unit }}</td>
                    <td>{{ formatStatus(box.status) }}</td>
                    <td>{{ box.warehouseName }} / {{ box.zoneName }}</td>
                  </tr>
                  <tr v-if="scanOrder && !scanOrderBoxes.length">
                    <td colspan="7" class="empty-cell">该入库单没有箱看板</td>
                  </tr>
                  <tr v-else-if="scanOrder && !pendingScanBoxes.length">
                    <td colspan="7" class="empty-cell">该入库单没有待入库箱看板</td>
                  </tr>
                  <tr v-else-if="!scanOrder">
                    <td colspan="7" class="empty-cell">未选择入库单</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
            <p v-if="scanResultMessage" class="scan-result-message" :class="{ error: !scanResultMessage.includes('成功') }">{{ scanResultMessage }}</p>
          </div>

          <aside class="scan-side-card">
            <div v-if="printedInboundScanForm.barcode" class="scan-qr-preview compact-qr-preview">
              <QrCodeImage :text="printedInboundScanForm.barcode" :size="126" />
              <p class="mono">{{ printedInboundScanForm.barcode }}</p>
            </div>
            <dl v-if="scanOrder" class="scan-order-info">
              <div><dt>入库单</dt><dd class="mono">{{ scanOrder.inboundNo }}</dd></div>
              <div><dt>供应商</dt><dd>{{ scanOrder.supplierName }}</dd></div>
              <div><dt>状态</dt><dd>{{ formatStatus(scanOrder.status) }}</dd></div>
              <div><dt>箱看板</dt><dd>{{ scanOrderBoxes.length }} 箱</dd></div>
              <div><dt>待入库</dt><dd>{{ pendingScanBoxes.length }} 箱</dd></div>
              <div><dt>已选择</dt><dd>{{ selectedScanBoxIds.length }} 箱</dd></div>
            </dl>
            <p v-else class="empty-cell">未选择入库单</p>
          </aside>
        </div>
      </section>

      <section v-if="activeModal === 'print' && printOrders.length" class="panel print-panel operation-modal-panel wide-operation-modal">
        <div class="section-head print-toolbar">
          <div>
            <h3>入库打印</h3>
          </div>
          <div class="action-row print-actions">
            <button @click="browserPrint">浏览器打印</button>
          </div>
        </div>

        <div class="print-grid">
          <article v-for="order in printOrders" :key="order.id" class="print-order-card">
            <header class="print-order-header">
              <div>
                <strong class="mono">{{ order.inboundNo }}</strong>
                <span>{{ order.supplierName }}</span>
                <span>{{ formatStatus(order.status) }}</span>
              </div>
              <div class="order-qr-inline">
                <QrCodeImage :text="inboundOrderScanCode(order)" :size="74" />
                <span class="mono">{{ inboundOrderScanCode(order) }}</span>
              </div>
            </header>

            <div class="print-part-groups">
              <section v-for="group in groupedInboundKanbans(order)" :key="group.key" class="print-part-group">
                <h4>{{ group.partText }}（{{ group.boxes.length }} 箱）</h4>
                <div class="print-kanban-grid">
                  <article v-for="box in group.boxes" :key="box.id" class="print-label-card box-label">
                    <div class="print-label-title">
                      <strong>箱看板</strong>
                      <span class="mono">{{ box.kanbanNo }}</span>
                    </div>
                    <div class="print-label-body compact-box-body">
                      <table class="print-info-table">
                        <tbody>
                          <tr><th>入库单</th><td class="mono">{{ order.inboundNo }}</td></tr>
                          <tr><th>箱序</th><td>第 {{ box.boxIndex }} 箱</td></tr>
                          <tr><th>零件</th><td>{{ box.partCode }} | {{ box.partName }}</td></tr>
                          <tr><th>数量</th><td>{{ box.qty }} {{ box.unit }}</td></tr>
                          <tr><th>库区</th><td>{{ box.warehouseName }} / {{ box.zoneName }}</td></tr>
                          <tr><th>状态</th><td>{{ formatStatus(box.status) }}</td></tr>
                          <tr><th>条码</th><td class="mono">{{ box.barcode }}</td></tr>
                        </tbody>
                      </table>
                      <div class="print-qr-box box-qr">
                        <QrCodeImage :text="box.qrContent || box.barcode" :size="82" />
                      </div>
                    </div>
                  </article>
                </div>
              </section>
            </div>
          </article>
        </div>
      </section>
    </PageModal>

    <InboundReturnModal
      :open="activeModal === 'return'"
      :mode="returnMode"
      :orders="returnOrders"
      :initial-selected-ids="returnInitialSelectedIds"
      @close="closeOperationModal"
      @completed="completeInboundReturn"
    />

    <InboundOrderDetailModal
      :open="!!detailOrder"
      :order="detailOrder"
      :kanbans="detailOrder ? kanbansForInbound(detailOrder) : []"
      :loading="detailOrder ? !!loadingKanbanOrderIds[detailOrder.id] : false"
      @close="closeInboundDetail"
    />

    <teleport to="body">
      <div v-if="batchPickerOpen" class="modal-backdrop">
        <section class="modal-panel batch-picker-panel">
          <div class="section-head batch-picker-head">
            <div>
              <h3>批量选择供应商零件</h3>
            </div>
            <div class="action-row batch-picker-actions">
              <button @click="applyBatchParts">加入选中零件</button>
            </div>
          </div>
          <div class="batch-picker-filter-row">
            <select v-model.number="form.supplierId" @change="handleBatchSupplierChange">
              <option :value="0">全部供应商零件</option>
              <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
                {{ item.supplierCode }} | {{ item.supplierName }}
              </option>
            </select>
            <input v-model="batchSearch" placeholder="搜索零件号 / 名称" @input="batchPage = 1" />
            <span class="batch-inline-summary">可选 {{ filteredSupplierParts.length }} 个，本筛选已选 {{ selectedFilteredBatchPartCount }} 个，总已选 {{ selectedBatchPartCount }} 个</span>
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
                  <th class="batch-select-head">
                    <input
                      class="compact-check"
                      type="checkbox"
                      :checked="allFilteredBatchPartsSelected"
                      :disabled="!filteredBatchPartCount"
                      title="全选或取消当前筛选零件"
                      @change="setFilteredBatchPartsSelected(($event.target as HTMLInputElement).checked)"
                    />
                  </th>
                  <th>零件</th>
                  <th>总数量</th>
                  <th>箱数</th>
                  <th>容器容量</th>
                  <th>器具编码</th>
                  <th>目标仓区</th>
                  <th>仓库性质</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(part, index) in pagedSupplierParts"
                  :key="part.id"
                  class="batch-part-row"
                  :class="{ selected: batchSelection[part.id], 'tone-a': index % 2 === 0, 'tone-b': index % 2 === 1 }"
                  @click="toggleBatchPart(part.id)"
                >
                  <td>
                    <input
                      class="compact-check"
                      type="checkbox"
                      :checked="!!batchSelection[part.id]"
                      @click.stop
                      @change.stop="setBatchPartSelected(part.id, ($event.target as HTMLInputElement).checked)"
                    />
                  </td>
                  <td>{{ part.partCode }} | {{ part.partName }}</td>
                  <td><input v-model.number="ensureBatchDraft(part).plannedQty" type="number" min="0.001" step="0.001" placeholder="填写总数量" @click.stop @input="syncBatchBoxPlan(part)" /></td>
                  <td><input :value="batchBoxCount(part) || ''" type="number" readonly placeholder="自动" @click.stop /></td>
                  <td><input :value="batchUnitPerBox(part)" type="number" readonly @click.stop /></td>
                  <td>
                    <select v-model="ensureBatchDraft(part).equipmentCode" @click.stop @change="handleBatchEquipmentChange(part)">
                      <option value="">选择器具</option>
                      <option v-for="item in inboundEquipmentOptions" :key="item.value" :value="item.value">
                        {{ item.label }}
                      </option>
                    </select>
                  </td>
                  <td>
                    <select v-model="ensureBatchDraft(part).warehouseZone" @click.stop @change="handleBatchWarehouseZoneChange(part)">
                      <option value="">选择仓区</option>
                      <option v-for="item in inboundWarehouseZoneOptions" :key="item.value" :value="item.value">
                        {{ item.label }}
                      </option>
                    </select>
                  </td>
                  <td><span class="repack-badge" :class="{ active: ensureBatchDraft(part).pendingRepack }">{{ repackText(ensureBatchDraft(part).warehouseZone) }}</span></td>
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
.print-grid {
  display: grid;
  gap: 12px;
}

.summary-strip,
.pager-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-strip {
  margin-top: 10px;
}

.create-inbound-panel {
  display: grid;
  gap: 8px;
  padding: 14px;
  min-width: 0;
  max-width: 100%;
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

.create-inbound-filter-row {
  display: grid;
  grid-template-columns: minmax(220px, 360px) minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.create-inbound-filter-row select,
.create-inbound-filter-row input {
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

.create-detail-table {
  gap: 4px;
  overflow-x: auto;
  max-width: 100%;
  padding-bottom: 2px;
}

.detail-row {
  display: grid;
  grid-template-columns:
    minmax(250px, 1.8fr)
    minmax(116px, 0.72fr)
    minmax(96px, 0.58fr)
    minmax(118px, 0.72fr)
    minmax(190px, 1.05fr)
    minmax(220px, 1.18fr)
    76px
    42px;
  gap: 6px;
  align-items: center;
  min-width: 1160px;
}

.detail-row > * {
  min-width: 0;
}

.detail-header-row {
  padding: 0 2px 2px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.detail-data-row {
  padding: 4px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.detail-row input,
.detail-row select {
  min-width: 0;
  width: 100%;
  min-height: 32px;
  padding-inline: 8px;
}

.detail-row input[readonly] {
  background: rgba(148, 163, 184, 0.1);
  color: var(--text-secondary);
}

.checkbox-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.repack-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.1);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.repack-badge.active {
  border-color: rgba(217, 119, 6, 0.28);
  background: rgba(245, 158, 11, 0.14);
  color: #92400e;
}

.row-delete-button {
  width: 34px;
  min-height: 32px;
  padding: 0;
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

.inbound-scan-panel {
  padding: 14px;
  min-width: 0;
  max-width: 100%;
}

.compact-modal-head {
  align-items: center;
  margin-bottom: 10px;
}

.compact-modal-head h3 {
  margin: 0;
  overflow-wrap: anywhere;
}

.inbound-scan-layout {
  display: grid;
  grid-template-columns: minmax(740px, 1fr) 270px;
  gap: 12px;
  align-items: start;
  overflow-x: auto;
}

.inbound-scan-main {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.scan-bound-summary,
.scan-box-toolbar {
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

.scan-box-toolbar .summary-strip {
  margin-top: 0;
}

.scan-box-toolbar button {
  min-height: 32px;
  padding: 0 10px;
  white-space: nowrap;
}

.scan-box-table-wrap {
  max-height: min(430px, 48vh);
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  min-width: 0;
  max-width: 100%;
}

.scan-box-table {
  min-width: 1160px;
  table-layout: fixed;
}

.scan-box-table th,
.scan-box-table td {
  padding: 6px 7px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.scan-box-table th:first-child,
.scan-box-table td:first-child {
  width: 44px;
  text-align: center;
}

.scan-box-table th:nth-child(2),
.scan-box-table td:nth-child(2) {
  width: 210px;
}

.scan-box-table th:nth-child(3),
.scan-box-table td:nth-child(3) {
  width: 76px;
}

.scan-box-table th:nth-child(5),
.scan-box-table td:nth-child(5),
.scan-box-table th:nth-child(6),
.scan-box-table td:nth-child(6) {
  width: 92px;
}

.scan-box-table th:nth-child(7),
.scan-box-table td:nth-child(7) {
  width: 230px;
}

.scan-box-table tr {
  cursor: pointer;
}

.scan-box-table tr.selected {
  background: rgba(20, 184, 166, 0.14);
}

.scan-box-table tr.success {
  background: rgba(22, 163, 74, 0.13);
}

.scan-box-table tr.disabled {
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

.form-error {
  margin: 10px 0 0;
  color: #dc2626;
}

.inbound-filter-panel {
  padding: 10px 12px;
}

.inbound-filter-line {
  display: grid;
  grid-template-columns: auto minmax(560px, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.inbound-filter-title h3 {
  margin: 0;
  white-space: nowrap;
}

.inbound-filter-row {
  display: grid;
  grid-template-columns: 108px minmax(170px, 1.05fr) minmax(140px, 0.9fr) 92px 58px 58px;
  gap: 6px;
  align-items: center;
}

.inbound-filter-row input,
.inbound-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.compact-filter-button {
  padding: 0 10px;
  white-space: nowrap;
}

.inbound-filter-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
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

.inbound-order-table {
  min-width: 1280px;
  table-layout: fixed;
}

.inbound-order-table th,
.inbound-order-table td {
  padding: 8px 10px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.inbound-order-table .select-col,
.inbound-order-table .select-cell {
  width: 36px;
  padding-inline: 6px;
  text-align: center;
}

.inbound-order-table .no-col {
  width: 150px;
}

.inbound-order-table .supplier-col {
  width: 190px;
}

.inbound-order-table .status-col {
  width: 108px;
}

.inbound-order-table .count-col {
  width: 72px;
}

.inbound-order-table .qty-col {
  width: 112px;
}

.inbound-order-table .time-col {
  width: 178px;
}

.inbound-order-table .action-col {
  width: 240px;
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

.inbound-order-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.inbound-order-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.inbound-order-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.inbound-order-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.inbound-order-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.inbound-order-row.selected:hover {
  background: rgba(20, 184, 166, 0.24);
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
}

.batch-part-table {
  min-width: 1160px;
  table-layout: fixed;
}

.batch-part-table th,
.batch-part-table td {
  padding: 4px 6px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.batch-part-table th:first-child,
.batch-part-table td:first-child,
.batch-part-table th:last-child,
.batch-part-table td:last-child {
  width: 40px;
  text-align: center;
}

.batch-part-table th:nth-child(2),
.batch-part-table td:nth-child(2) {
  width: 248px;
}

.batch-part-table th:nth-child(3),
.batch-part-table th:nth-child(4),
.batch-part-table th:nth-child(5) {
  width: 104px;
}

.batch-part-table th:nth-child(6),
.batch-part-table th:nth-child(7) {
  width: 218px;
}

.batch-picker-panel {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  min-width: 0;
}

.batch-picker-head {
  margin-bottom: 0;
  align-items: center;
}

.batch-picker-head h3 {
  margin: 0;
  font-size: 15px;
}

.batch-picker-actions {
  justify-content: flex-end;
}

.batch-picker-actions button {
  min-height: 30px;
  padding: 0 10px;
  white-space: nowrap;
}

.batch-picker-filter-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.05fr) minmax(190px, 0.9fr) minmax(220px, 1fr) auto;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.batch-picker-filter-row input,
.batch-picker-filter-row select,
.batch-picker-filter-row button {
  min-width: 0;
  min-height: 30px;
}

.batch-inline-summary {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-part-table input,
.batch-part-table select {
  min-height: 28px;
  font-size: 12px;
}

.batch-part-table input[type='number'] {
  padding-inline: 6px;
}

.batch-select-head {
  padding-inline: 4px;
}

.batch-part-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.batch-part-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.batch-part-row.tone-b {
  background: rgba(148, 163, 184, 0.06);
}

.batch-part-row:hover {
  background: rgba(37, 99, 235, 0.1);
}

.batch-part-row.selected {
  background: rgba(20, 184, 166, 0.15);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

@media (max-width: 1180px) {
  .inbound-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .inbound-filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .inbound-filter-row {
    grid-template-columns: 1fr 1fr;
  }

  .compact-filter-button {
    grid-column: auto;
  }
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
  z-index: 160;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.modal-panel {
  width: min(1180px, 96vw);
  min-width: 0;
  max-height: 90vh;
  overflow: auto;
  background: var(--panel-bg, #fff);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.24);
}

.batch-part-table input,
.batch-part-table select {
  width: 100%;
  min-width: 0;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 2px;
}

.operation-modal-panel {
  min-width: 0;
  max-width: 100%;
  overflow: auto;
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

.print-grid {
  grid-template-columns: 1fr;
  gap: 12px;
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

.print-qr-box span {
  max-width: 100%;
  overflow-wrap: anywhere;
  font-size: 9px;
  line-height: 1.2;
  text-align: center;
}

button.success {
  background: #16a34a;
}

@media (max-width: 1100px) {
  .create-inbound-filter-row,
  .batch-picker-filter-row {
    grid-template-columns: 1fr;
  }

  .detail-header-row {
    display: none;
  }

  .create-detail-table {
    padding-bottom: 4px;
  }

  .inbound-scan-layout {
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
  .print-order-card {
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

  .print-label-body {
    grid-template-columns: minmax(0, 1fr) max-content;
    gap: 2px;
    padding: 2px;
  }

  .compact-box-body {
    grid-template-columns: minmax(0, 1fr) max-content;
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


