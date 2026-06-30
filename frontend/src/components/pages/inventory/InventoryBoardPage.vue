<!-- 本文件实现库存看板，支持多级库存预警、箱级看板查看以及库存流水折线图。 -->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatBusinessType } from '../../../app/displayText'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import TransactionDetailModal from '../../shared/TransactionDetailModal.vue'
import InventoryDetailModal from './modals/InventoryDetailModal.vue'
import type { ConfigItem, InventoryPartSummary, InventoryRow, Kanban, PageModel, TransactionRow } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const WARNING_MODULE_KEY = 'inventoryWarning'
const WARNING_LEVELS = [
  { key: 'critical', label: '严重不足' },
  { key: 'low', label: '低库存' },
  { key: 'attention', label: '关注' },
] as const

type WarningLevelKey = (typeof WARNING_LEVELS)[number]['key']
type WarningThresholdConfig = Record<WarningLevelKey, number>

const filters = reactive({
  warehouseName: '',
  zoneName: '',
  materialKeyword: '',
  supplierId: 0,
})
const rows = ref<InventoryPartSummary[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const detailLocationRows = ref<InventoryRow[]>([])
const detailKanbans = ref<Kanban[]>([])
const detailLocationPage = ref(1)
const detailLocationPageSize = ref(10)
const detailLocationTotal = ref(0)
const detailKanbanPage = ref(1)
const detailKanbanPageSize = ref(10)
const detailKanbanTotal = ref(0)
const detailLoading = ref(false)
const detailErrorMessage = ref('')
const transactionRows = ref<TransactionRow[]>([])
const transactionPage = ref(1)
const transactionPageSize = ref(20)
const transactionTotal = ref(0)
const transactionLoading = ref(false)
const transactionErrorMessage = ref('')

const thresholdItems = ref<ConfigItem[]>([])
const thresholdDrafts = reactive<Record<string, WarningThresholdConfig>>({})
const batchThresholdDraft = reactive<WarningThresholdConfig>({
  critical: 0,
  low: 0,
  attention: 0,
})
const selectedPartCodes = reactive<Record<string, boolean>>({})
const activePartCode = ref('')
const inventoryDetailPartCode = ref('')
const activeTransactionId = ref<number | null>(null)
const transactionDetail = ref<TransactionRow | null>(null)
const thresholdSaving = ref(false)
const showSummaryBoard = ref(false)
const inventoryDetailModalOpen = ref(false)
const thresholdEditorOpen = ref(false)
const transactionModalOpen = ref(false)
const thresholdEditorMode = ref<'single' | 'batch'>('single')
const activeThresholdPart = ref<{ partCode: string; partName: string } | null>(null)
const thresholdEditorDraft = reactive<WarningThresholdConfig>({
  critical: 0,
  low: 0,
  attention: 0,
})

const inventoryWarehouseOptions = computed(() => warehouseOptions(props.model.state.locations))
const inventoryZoneOptions = computed(() => zoneOptions(props.model.state.locations, filters.warehouseName))

const partRows = computed(() => rows.value)

const thresholdMap = computed(() => {
  const map = new Map<string, ConfigItem>()
  thresholdItems.value.forEach((item) => map.set(item.itemCode, item))
  return map
})

const activePartTransactions = computed(() =>
  transactionRows.value
    .filter((row) => !activePartCode.value || row.partCode === activePartCode.value)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()),
)

const activeInventoryDetailRow = computed(() =>
  partRows.value.find((row) => row.partCode === inventoryDetailPartCode.value) ?? null,
)

const activeInventoryKanbans = computed(() => detailKanbans.value)

function emptyThresholdConfig(): WarningThresholdConfig {
  return { critical: 0, low: 0, attention: 0 }
}

function normalizeThresholdConfig(input?: Partial<WarningThresholdConfig> | null): WarningThresholdConfig {
  const config = emptyThresholdConfig()
  WARNING_LEVELS.forEach((level) => {
    config[level.key] = Math.max(0, Number(input?.[level.key] ?? 0))
  })
  return config
}

function parseThresholdConfig(raw: string | null | undefined): WarningThresholdConfig {
  if (!raw) return emptyThresholdConfig()
  try {
    return normalizeThresholdConfig(JSON.parse(raw))
  } catch {
    return emptyThresholdConfig()
  }
}

function isEmptyThresholdConfig(config: WarningThresholdConfig) {
  return Object.values(config).every((value) => value <= 0)
}

function configuredThresholdConfig(partCode: string) {
  const item = thresholdMap.value.get(partCode)
  return item ? parseThresholdConfig(item.remark) : null
}

function defaultThresholdConfig() {
  return parseThresholdConfig(thresholdMap.value.get('DEFAULT')?.remark)
}

function thresholdConfig(partCode: string) {
  if (thresholdDrafts[partCode]) return thresholdDrafts[partCode]
  const configured = configuredThresholdConfig(partCode)
  if (configured && !isEmptyThresholdConfig(configured)) return configured
  return defaultThresholdConfig()
}

function ensureThresholdDraft(partCode: string) {
  if (!thresholdDrafts[partCode]) {
    thresholdDrafts[partCode] = thresholdConfig(partCode)
  }
  return thresholdDrafts[partCode]
}

function warningLevel(partCode: string, totalQty: number) {
  const config = thresholdConfig(partCode)
  if (config.critical > 0 && totalQty <= config.critical) return 'critical'
  if (config.low > 0 && totalQty <= config.low) return 'low'
  if (config.attention > 0 && totalQty <= config.attention) return 'attention'
  return ''
}

function warningLevelLabel(level: string) {
  return WARNING_LEVELS.find((item) => item.key === level)?.label ?? '正常'
}

const allSelected = computed(() => partRows.value.length > 0 && partRows.value.every((row) => selectedPartCodes[row.partCode]))
const selectedRows = computed(() => partRows.value.filter((row) => selectedPartCodes[row.partCode]))
const summary = computed(() => ({
  rowCount: total.value,
  totalQty: partRows.value.reduce((total, item) => total + Number(item.totalQty), 0),
  partCount: total.value,
  warningCount: partRows.value.filter((item) => Boolean(warningLevel(item.partCode, item.totalQty))).length,
  criticalCount: partRows.value.filter((item) => warningLevel(item.partCode, item.totalQty) === 'critical').length,
  lowCount: partRows.value.filter((item) => warningLevel(item.partCode, item.totalQty) === 'low').length,
  attentionCount: partRows.value.filter((item) => warningLevel(item.partCode, item.totalQty) === 'attention').length,
}))

const chartPoints = computed(() => {
  const rows = activePartTransactions.value
  if (!rows.length) return []
  let runningQty = 0
  return rows.map((row, index) => {
    runningQty += Number(row.qtyChange)
    return { ...row, runningQty, index }
  })
})

function formatChartQty(value: number) {
  if (Math.abs(value) >= 1000) return value.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
  if (Number.isInteger(value)) return String(value)
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function formatChartTime(value: string) {
  const date = new Date(value)
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function calculateNiceStep(range: number, targetTickCount: number) {
  const roughStep = Math.max(range, 1) / Math.max(targetTickCount, 1)
  const magnitude = 10 ** Math.floor(Math.log10(roughStep || 1))
  const normalized = roughStep / magnitude
  if (normalized <= 1) return magnitude
  if (normalized <= 2) return 2 * magnitude
  if (normalized <= 5) return 5 * magnitude
  return 10 * magnitude
}

const chartGeometry = computed(() => {
  const points = chartPoints.value
  if (!points.length) {
    return {
      width: 760,
      height: 320,
      points: [],
      path: '',
      yTicks: [],
      xTicks: [],
      horizontalGridLines: [],
      verticalGridLines: [],
      paddingLeft: 72,
      paddingRight: 24,
      paddingTop: 24,
      paddingBottom: 52,
    }
  }

  const width = 760
  const height = 320
  const paddingLeft = 72
  const paddingRight = 24
  const paddingTop = 24
  const paddingBottom = 52
  const plotWidth = width - paddingLeft - paddingRight
  const plotHeight = height - paddingTop - paddingBottom
  const values = points.map((item) => item.runningQty)
  const rawMin = Math.min(...values, 0)
  const rawMax = Math.max(...values, 0)
  const step = calculateNiceStep(rawMax - rawMin, 5)
  const min = Math.floor(rawMin / step) * step
  const max = Math.ceil(rawMax / step) * step
  const span = Math.max(max - min, step)

  const svgPoints = points.map((item, index) => ({
    ...item,
    x: paddingLeft + (index * plotWidth) / Math.max(points.length - 1, 1),
    y: height - paddingBottom - ((item.runningQty - min) / span) * plotHeight,
  }))

  const yTicks = Array.from({ length: Math.round(span / step) + 1 }, (_, index) => {
    const value = min + index * step
    return {
      value,
      label: formatChartQty(value),
      y: height - paddingBottom - ((value - min) / span) * plotHeight,
    }
  })

  const xTickIndexes = new Set<number>([0, points.length - 1])
  const desiredXTickCount = Math.min(5, points.length)
  for (let index = 0; index < desiredXTickCount; index += 1) {
    xTickIndexes.add(Math.round((index * (points.length - 1)) / Math.max(desiredXTickCount - 1, 1)))
  }

  const xTicks = Array.from(xTickIndexes).sort((a, b) => a - b).map((index) => ({
    index,
    x: svgPoints[index].x,
    label: formatChartTime(points[index].createdAt),
  }))

  return {
    width,
    height,
    points: svgPoints,
    path: svgPoints.map((item, index) => `${index === 0 ? 'M' : 'L'} ${item.x} ${item.y}`).join(' '),
    yTicks,
    xTicks,
    horizontalGridLines: yTicks,
    verticalGridLines: xTicks,
    paddingLeft,
    paddingRight,
    paddingTop,
    paddingBottom,
  }
})

function resetFilters() {
  filters.warehouseName = ''
  filters.zoneName = ''
  filters.materialKeyword = ''
  filters.supplierId = 0
  void goFirstAndLoadInventory()
}

function toggleSummaryBoard() {
  showSummaryBoard.value = !showSummaryBoard.value
}

async function loadThresholds() {
  thresholdItems.value = await api.listConfigItems(WARNING_MODULE_KEY)
  Object.keys(thresholdDrafts).forEach((key) => delete thresholdDrafts[key])
  thresholdItems.value.forEach((item) => {
    thresholdDrafts[item.itemCode] = parseThresholdConfig(item.remark)
  })
}

function mergeKanbanCache(kanbans: Kanban[]) {
  if (!kanbans.length) return
  const map = new Map(props.model.state.kanbans.map((item) => [item.id, item]))
  kanbans.forEach((item) => map.set(item.id, item))
  props.model.state.kanbans = Array.from(map.values()).sort((left, right) => {
    const leftTime = new Date(left.createdAt).getTime() || 0
    const rightTime = new Date(right.createdAt).getTime() || 0
    if (rightTime !== leftTime) return rightTime - leftTime
    return Number(right.id) - Number(left.id)
  })
}

async function loadInventory(nextPage = page.value) {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listInventoryPage({
      warehouseName: filters.warehouseName,
      zoneName: filters.zoneName,
      materialKeyword: filters.materialKeyword.trim(),
      supplierId: filters.supplierId,
      page: nextPage,
      size: pageSize.value,
    })
    rows.value = result.records
    page.value = result.page
    total.value = result.total
    if (!result.records.length && result.total > 0 && result.page > 1) {
      await loadInventory(Math.max(1, result.totalPages))
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '库存查询失败'
  } finally {
    loading.value = false
  }
}

async function goFirstAndLoadInventory() {
  if (page.value === 1) {
    await loadInventory(1)
    return
  }
  page.value = 1
}

async function searchInventory() {
  await goFirstAndLoadInventory()
}

async function loadInventoryDetailLocations(nextPage = detailLocationPage.value) {
  if (!inventoryDetailPartCode.value) return
  detailLoading.value = true
  detailErrorMessage.value = ''
  try {
    const result = await api.listInventoryDetailsPage({
      partCode: inventoryDetailPartCode.value,
      warehouseName: filters.warehouseName,
      zoneName: filters.zoneName,
      materialKeyword: '',
      supplierId: filters.supplierId,
      page: nextPage,
      size: detailLocationPageSize.value,
    })
    detailLocationRows.value = result.records
    detailLocationPage.value = result.page
    detailLocationTotal.value = result.total
  } catch (error) {
    detailErrorMessage.value = error instanceof Error ? error.message : '库位明细查询失败'
  } finally {
    detailLoading.value = false
  }
}

async function loadInventoryDetailKanbans(nextPage = detailKanbanPage.value) {
  if (!inventoryDetailPartCode.value) return
  detailLoading.value = true
  detailErrorMessage.value = ''
  try {
    const result = await api.listInventoryKanbansPage({
      partCode: inventoryDetailPartCode.value,
      warehouseName: filters.warehouseName,
      zoneName: filters.zoneName,
      supplierId: filters.supplierId,
      page: nextPage,
      size: detailKanbanPageSize.value,
    })
    detailKanbans.value = result.records
    detailKanbanPage.value = result.page
    detailKanbanTotal.value = result.total
    mergeKanbanCache(result.records)
  } catch (error) {
    detailErrorMessage.value = error instanceof Error ? error.message : '看板明细查询失败'
  } finally {
    detailLoading.value = false
  }
}

async function loadInventoryDetails() {
  await Promise.all([loadInventoryDetailLocations(1), loadInventoryDetailKanbans(1)])
}

async function loadTransactions(nextPage = transactionPage.value) {
  if (!activePartCode.value) return
  transactionLoading.value = true
  transactionErrorMessage.value = ''
  try {
    const result = await api.listTransactionsPage({
      partCode: activePartCode.value,
      page: nextPage,
      size: transactionPageSize.value,
    })
    transactionRows.value = result.records
    transactionPage.value = result.page
    transactionTotal.value = result.total
  } catch (error) {
    transactionErrorMessage.value = error instanceof Error ? error.message : '库存流水查询失败'
  } finally {
    transactionLoading.value = false
  }
}

function toggleSelectAll(checked: boolean) {
  partRows.value.forEach((row) => {
    selectedPartCodes[row.partCode] = checked
  })
}

function setPartSelected(partCode: string, checked: boolean) {
  selectedPartCodes[partCode] = checked
}

function togglePartSelected(partCode: string) {
  selectedPartCodes[partCode] = !selectedPartCodes[partCode]
}

function thresholdSummaryText(partCode: string) {
  const config = thresholdConfig(partCode)
  const values = [
    config.critical > 0 ? `严重不足≤${config.critical}` : '',
    config.low > 0 ? `低库存≤${config.low}` : '',
    config.attention > 0 ? `关注≤${config.attention}` : '',
  ].filter(Boolean)
  const source = thresholdMap.value.has(partCode) ? '' : '默认：'
  return values.length ? `${source}${values.join(' / ')}` : '未设置'
}

function copyThresholdConfigToEditor(config: WarningThresholdConfig) {
  thresholdEditorDraft.critical = config.critical
  thresholdEditorDraft.low = config.low
  thresholdEditorDraft.attention = config.attention
}

function openSingleThresholdEditor(row: { partCode: string; partName: string }) {
  thresholdEditorMode.value = 'single'
  activeThresholdPart.value = { partCode: row.partCode, partName: row.partName }
  copyThresholdConfigToEditor(ensureThresholdDraft(row.partCode))
  thresholdEditorOpen.value = true
}

function openBatchThresholdEditor() {
  if (!selectedRows.value.length) {
    props.model.actions.setNotice('请先勾选要批量设置阈值的零件')
    return
  }
  thresholdEditorMode.value = 'batch'
  activeThresholdPart.value = null
  copyThresholdConfigToEditor(batchThresholdDraft)
  thresholdEditorOpen.value = true
}

function closeThresholdEditor() {
  thresholdEditorOpen.value = false
}

async function saveThresholdForPart(row: { partCode: string; partName: string }, sourceConfig?: WarningThresholdConfig) {
  thresholdSaving.value = true
  try {
    const draft = normalizeThresholdConfig(sourceConfig ?? ensureThresholdDraft(row.partCode))
    const payload = {
      moduleKey: WARNING_MODULE_KEY,
      itemCode: row.partCode,
      itemName: row.partName,
      status: 'ENABLED',
      remark: JSON.stringify(normalizeThresholdConfig(draft)),
    }
    const existing = thresholdMap.value.get(row.partCode)
    if (existing) {
      await api.updateConfigItem(existing.id, payload)
    } else {
      await api.createConfigItem(payload)
    }
    await loadThresholds()
    props.model.actions.setNotice(`已保存 ${row.partCode} 的分级预警阈值`)
  } finally {
    thresholdSaving.value = false
  }
}

async function batchSaveThreshold() {
  const selected = selectedRows.value
  const batchConfig = normalizeThresholdConfig(thresholdEditorDraft)
  if (!selected.length || Object.values(batchConfig).every((value) => value <= 0)) {
    props.model.actions.setNotice('请选择零件并至少填写一个大于 0 的预警阈值')
    return
  }
  thresholdSaving.value = true
  try {
    for (const row of selected) {
      const payload = {
        moduleKey: WARNING_MODULE_KEY,
        itemCode: row.partCode,
        itemName: row.partName,
        status: 'ENABLED',
        remark: JSON.stringify(batchConfig),
      }
      const existing = thresholdMap.value.get(row.partCode)
      if (existing) {
        await api.updateConfigItem(existing.id, payload)
      } else {
        await api.createConfigItem(payload)
      }
    }
    await loadThresholds()
    batchThresholdDraft.critical = batchConfig.critical
    batchThresholdDraft.low = batchConfig.low
    batchThresholdDraft.attention = batchConfig.attention
    props.model.actions.setNotice(`已批量设置 ${selected.length} 个零件的分级预警阈值`)
  } finally {
    thresholdSaving.value = false
  }
}

async function saveThresholdEditor() {
  const config = normalizeThresholdConfig(thresholdEditorDraft)
  if (Object.values(config).every((value) => value <= 0)) {
    props.model.actions.setNotice('请至少填写一个大于 0 的预警阈值')
    return
  }
  if (thresholdEditorMode.value === 'single') {
    if (!activeThresholdPart.value) return
    thresholdDrafts[activeThresholdPart.value.partCode] = config
    await saveThresholdForPart(activeThresholdPart.value, config)
  } else {
    await batchSaveThreshold()
  }
  closeThresholdEditor()
}

async function openInventoryDetail(partCode: string) {
  inventoryDetailPartCode.value = partCode
  detailLocationPage.value = 1
  detailKanbanPage.value = 1
  detailLocationRows.value = []
  detailKanbans.value = []
  inventoryDetailModalOpen.value = true
  await loadInventoryDetails()
}

function closeInventoryDetail() {
  inventoryDetailModalOpen.value = false
  inventoryDetailPartCode.value = ''
  detailLocationRows.value = []
  detailKanbans.value = []
  detailErrorMessage.value = ''
}

async function openTransactions(partCode: string) {
  activePartCode.value = partCode
  activeTransactionId.value = null
  transactionDetail.value = null
  transactionPage.value = 1
  transactionRows.value = []
  transactionModalOpen.value = true
  await loadTransactions(1)
}

function closeTransactionModal() {
  transactionModalOpen.value = false
  activeTransactionId.value = null
  transactionDetail.value = null
  transactionRows.value = []
  transactionErrorMessage.value = ''
}

async function jumpToTransaction(id: number) {
  activeTransactionId.value = id
  await nextTick()
  document.getElementById(`tx-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function openTransactionDetail(row: TransactionRow) {
  activeTransactionId.value = row.id
  transactionDetail.value = row
}

function closeTransactionDetail() {
  transactionDetail.value = null
}

function pointTitle(point: { businessType: string; qtyChange: number; runningQty: number; createdAt: string; remark: string }) {
  return `${formatBusinessType(point.businessType)} | 变化 ${point.qtyChange} | 库存 ${point.runningQty} | ${new Date(point.createdAt).toLocaleString('zh-CN', { hour12: false })}${point.remark ? ` | ${point.remark}` : ''}`
}

watch(page, async (value) => {
  await loadInventory(value)
})

watch(pageSize, async () => {
  await goFirstAndLoadInventory()
})

watch(detailLocationPage, async (value) => {
  if (inventoryDetailModalOpen.value) await loadInventoryDetailLocations(value)
})

watch(detailLocationPageSize, async () => {
  detailLocationPage.value = 1
  if (inventoryDetailModalOpen.value) await loadInventoryDetailLocations(1)
})

watch(detailKanbanPage, async (value) => {
  if (inventoryDetailModalOpen.value) await loadInventoryDetailKanbans(value)
})

watch(detailKanbanPageSize, async () => {
  detailKanbanPage.value = 1
  if (inventoryDetailModalOpen.value) await loadInventoryDetailKanbans(1)
})

watch(transactionPage, async (value) => {
  if (transactionModalOpen.value) await loadTransactions(value)
})

watch(transactionPageSize, async () => {
  transactionPage.value = 1
  if (transactionModalOpen.value) await loadTransactions(1)
})

watch(activePartCode, async (value, oldValue) => {
  if (!transactionModalOpen.value || value === oldValue) return
  transactionPage.value = 1
  await loadTransactions(1)
})

function handleBusinessChanged() {
  void loadInventory(page.value)
  if (inventoryDetailModalOpen.value) void loadInventoryDetails()
  if (transactionModalOpen.value) void loadTransactions(transactionPage.value)
}

onMounted(async () => {
  window.addEventListener('wms-business-changed', handleBusinessChanged)
  await Promise.all([loadThresholds(), loadInventory(1)])
  if (partRows.value[0]) activePartCode.value = partRows.value[0].partCode
})

onBeforeUnmount(() => {
  window.removeEventListener('wms-business-changed', handleBusinessChanged)
})
</script>

<template>
  <section class="stack inventory-board-page">
    <section class="panel inventory-filter-panel">
      <div class="inventory-filter-line">
        <h3>库存看板</h3>
        <div class="inventory-filter-row">
          <input v-model="filters.materialKeyword" placeholder="物料 / 零件号" />
          <select v-model="filters.warehouseName" @change="filters.zoneName = ''">
            <option value="">全部仓库</option>
            <option v-for="warehouse in inventoryWarehouseOptions" :key="warehouse" :value="warehouse">{{ warehouse }}</option>
          </select>
          <select v-model="filters.zoneName">
            <option value="">全部库区</option>
            <option v-for="zone in inventoryZoneOptions" :key="zone" :value="zone">{{ zone }}</option>
          </select>
          <select v-model.number="filters.supplierId">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">{{ item.supplierCode }} | {{ item.supplierName }}</option>
          </select>
          <button class="secondary-button compact-filter-button" :disabled="loading" @click="searchInventory">查询</button>
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row inventory-filter-actions">
          <span class="summary-pill">零件 {{ summary.partCount }} / 预警 {{ summary.warningCount }}</span>
          <button class="secondary-button" @click="toggleSummaryBoard">{{ showSummaryBoard ? '隐藏总看板' : '查看总看板' }}</button>
          <button :disabled="!selectedRows.length || thresholdSaving" @click="openBatchThresholdEditor">批量设置阈值</button>
        </div>
      </div>
    </section>

    <section v-if="showSummaryBoard" class="summary-grid compact-summary-grid">
      <div class="panel summary-card"><span class="summary-label">记录</span><strong>{{ summary.rowCount }}</strong></div>
      <div class="panel summary-card"><span class="summary-label">总量</span><strong>{{ summary.totalQty }}</strong></div>
      <div class="panel summary-card"><span class="summary-label">零件</span><strong>{{ summary.partCount }}</strong></div>
      <div class="panel summary-card warning-card"><span class="summary-label">预警</span><strong>{{ summary.warningCount }}</strong></div>
      <div class="panel summary-card critical-card"><span class="summary-label">严重不足</span><strong>{{ summary.criticalCount }}</strong></div>
      <div class="panel summary-card low-card"><span class="summary-label">低库存</span><strong>{{ summary.lowCount }}</strong></div>
      <div class="panel summary-card attention-card"><span class="summary-label">关注</span><strong>{{ summary.attentionCount }}</strong></div>
    </section>

    <section class="panel inventory-table-panel">
      <div class="table-toolbar">
        <span>{{ loading ? '正在查询库存...' : `已选 ${selectedRows.length} 个，本页 ${partRows.length} 个，库存零件 ${total} 个` }}</span>
        <span v-if="thresholdSaving" class="selected-count">正在保存阈值...</span>
        <span v-if="errorMessage" class="form-error">{{ errorMessage }}</span>
        <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="total" :selected="selectedRows.length" />
      </div>
      <div class="table-scroll aligned-table-shell">
        <table class="table inventory-part-table">
          <thead>
            <tr>
              <th class="select-col"><input class="compact-check" type="checkbox" :checked="allSelected" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
              <th>零件编码</th>
              <th>零件名称</th>
              <th>供应商</th>
              <th>库存总量</th>
              <th>预警阈值</th>
              <th>预警状态</th>
              <th>最近更新时间</th>
              <th class="action-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="(row, index) in partRows" :key="row.partCode">
              <tr
                class="inventory-part-row"
                :class="[
                  warningLevel(row.partCode, row.totalQty) ? `warning-${warningLevel(row.partCode, row.totalQty)}` : '',
                  selectedPartCodes[row.partCode] ? 'selected' : '',
                  Math.floor(index / 2) % 2 === 0 ? 'tone-a' : 'tone-b',
                ]"
                @click="togglePartSelected(row.partCode)"
              >
                <td class="select-cell"><input class="compact-check" type="checkbox" :checked="!!selectedPartCodes[row.partCode]" @click.stop @change.stop="setPartSelected(row.partCode, ($event.target as HTMLInputElement).checked)" /></td>
                <td class="mono">{{ row.partCode }}</td>
                <td>{{ row.partName }}</td>
                <td>{{ row.supplierName }}</td>
                <td>{{ row.totalQty }}</td>
                <td class="threshold-summary" :title="thresholdSummaryText(row.partCode)">
                  <span class="cell-ellipsis">{{ thresholdSummaryText(row.partCode) }}</span>
                </td>
                <td><span :class="['warning-badge', warningLevel(row.partCode, row.totalQty) || 'normal']">{{ warningLevelLabel(warningLevel(row.partCode, row.totalQty)) }}</span></td>
                <td>{{ new Date(row.latestUpdatedAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                <td class="action-cell" @click.stop>
                  <div class="row-actions">
                    <button class="secondary-button" @click="openInventoryDetail(row.partCode)">明细</button>
                    <button class="secondary-button" :disabled="thresholdSaving" @click="openSingleThresholdEditor(row)">阈值</button>
                    <button class="secondary-button" @click="openTransactions(row.partCode)">流水</button>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="!partRows.length">
              <td colspan="9" class="empty-cell">没有匹配的库存零件</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <InventoryDetailModal
      v-model:location-page="detailLocationPage"
      v-model:location-page-size="detailLocationPageSize"
      v-model:kanban-page="detailKanbanPage"
      v-model:kanban-page-size="detailKanbanPageSize"
      :open="inventoryDetailModalOpen"
      :active-row="activeInventoryDetailRow"
      :location-rows="detailLocationRows"
      :kanbans="activeInventoryKanbans"
      :location-total="detailLocationTotal"
      :kanban-total="detailKanbanTotal"
      :loading="detailLoading"
      :error-message="detailErrorMessage"
      @close="closeInventoryDetail"
    />

    <PageModal :open="transactionModalOpen" wide @close="closeTransactionModal">
      <section class="panel flow-panel transaction-flow-modal">
        <div class="section-head compact-flow-head">
          <div>
            <h3>零件库存流水</h3>
          </div>
          <div class="action-row flow-actions">
            <select v-model="activePartCode">
              <option value="">选择零件</option>
              <option v-for="row in partRows" :key="row.partCode" :value="row.partCode">{{ row.partCode }} | {{ row.partName }}</option>
            </select>
            <CompactPager v-model:page="transactionPage" v-model:page-size="transactionPageSize" :total="transactionTotal" :page-size-options="[10, 20, 50, 100]" />
          </div>
        </div>
        <p v-if="transactionErrorMessage" class="form-error compact-modal-error">{{ transactionErrorMessage }}</p>

        <div v-if="chartGeometry.points.length" class="chart-wrap">
          <svg :viewBox="`0 0 ${chartGeometry.width} ${chartGeometry.height}`" class="flow-chart" role="img" aria-label="库存流水折线图">
            <line v-for="tick in chartGeometry.horizontalGridLines" :key="`y-${tick.value}`" :x1="chartGeometry.paddingLeft" :y1="tick.y" :x2="chartGeometry.width - chartGeometry.paddingRight" :y2="tick.y" class="grid-line" />
            <line v-for="tick in chartGeometry.verticalGridLines" :key="`x-${tick.index}`" :x1="tick.x" :y1="chartGeometry.paddingTop" :x2="tick.x" :y2="chartGeometry.height - chartGeometry.paddingBottom" class="grid-line vertical" />
            <line :x1="chartGeometry.paddingLeft" :y1="chartGeometry.height - chartGeometry.paddingBottom" :x2="chartGeometry.width - chartGeometry.paddingRight" :y2="chartGeometry.height - chartGeometry.paddingBottom" class="axis" />
            <line :x1="chartGeometry.paddingLeft" :y1="chartGeometry.paddingTop" :x2="chartGeometry.paddingLeft" :y2="chartGeometry.height - chartGeometry.paddingBottom" class="axis" />
            <path :d="chartGeometry.path" class="flow-line" />
            <text v-for="tick in chartGeometry.yTicks" :key="`yt-${tick.value}`" :x="chartGeometry.paddingLeft - 10" :y="tick.y + 4" class="axis-label axis-label-y">{{ tick.label }}</text>
            <text v-for="tick in chartGeometry.xTicks" :key="`xt-${tick.index}`" :x="tick.x" :y="chartGeometry.height - 18" class="axis-label axis-label-x">{{ tick.label }}</text>
            <circle v-for="point in chartGeometry.points" :key="point.id" :cx="point.x" :cy="point.y" r="5" class="flow-point" @click="jumpToTransaction(point.id)">
              <title>{{ pointTitle(point) }}</title>
            </circle>
          </svg>
        </div>
        <p v-else class="empty-hint">{{ transactionLoading ? '正在查询库存流水...' : '当前零件还没有可展示的库存流水。' }}</p>

        <div class="table-scroll modal-table-shell">
          <table class="table transaction-table">
            <thead>
              <tr>
                <th>流水号</th>
                <th>类型</th>
                <th>业务单号</th>
                <th>库位</th>
                <th>数量变化</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in activePartTransactions" :id="`tx-${row.id}`" :key="row.id" :class="{ active: activeTransactionId === row.id }" @dblclick="openTransactionDetail(row)">
                <td class="mono">{{ row.transactionNo }}</td>
                <td>{{ formatBusinessType(row.businessType) }}</td>
                <td class="mono compact-text">{{ row.businessNo || '-' }}</td>
                <td>{{ row.locationCode }}</td>
                <td :class="{ outbound: Number(row.qtyChange) < 0, inbound: Number(row.qtyChange) > 0 }">{{ row.qtyChange }}</td>
                <td>{{ new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                <td><button class="secondary-button compact-row-button" @click="openTransactionDetail(row)">详情</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </PageModal>

    <TransactionDetailModal :transaction="transactionDetail" @close="closeTransactionDetail" />

    <PageModal :open="thresholdEditorOpen" @close="closeThresholdEditor">
      <section class="panel threshold-modal">
        <div class="section-head compact-modal-head">
          <div>
            <h3>{{ thresholdEditorMode === 'batch' ? '批量设置预警阈值' : `设置阈值：${activeThresholdPart?.partCode}` }}</h3>
          </div>
          <div class="action-row">
            <button :disabled="thresholdSaving" @click="saveThresholdEditor">保存</button>
          </div>
        </div>
        <div class="threshold-editor-grid">
          <label class="threshold-field critical">
            <span>严重不足阈值</span>
            <input v-model.number="thresholdEditorDraft.critical" type="number" min="0" step="0.001" />
          </label>
          <label class="threshold-field low">
            <span>低库存阈值</span>
            <input v-model.number="thresholdEditorDraft.low" type="number" min="0" step="0.001" />
          </label>
          <label class="threshold-field attention">
            <span>关注阈值</span>
            <input v-model.number="thresholdEditorDraft.attention" type="number" min="0" step="0.001" />
          </label>
        </div>
      </section>
    </PageModal>
  </section>
</template>
<style scoped>
.inventory-filter-panel {
  padding: 10px 12px;
}

.inventory-board-page,
.inventory-board-page > .panel,
.inventory-board-page > .summary-grid {
  min-width: 0;
  max-width: 100%;
}

.inventory-board-page {
  gap: 12px;
}

.inventory-filter-line {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) max-content;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.inventory-filter-line h3,
.compact-flow-head h3,
.compact-modal-head h3 {
  margin: 0;
  white-space: nowrap;
}

.inventory-filter-line > h3 {
  width: 96px;
}

.inventory-filter-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) minmax(138px, 0.8fr) minmax(138px, 0.8fr) minmax(180px, 1.05fr) 58px 58px;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.inventory-filter-row input,
.inventory-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.inventory-filter-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.inventory-filter-actions button {
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
}

.summary-pill {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.compact-summary-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(92px, 1fr));
  gap: 8px;
}

.summary-card {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
}

.summary-card strong {
  text-align: right;
}

.summary-label {
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.warning-card strong { color: #dc2626; }
.critical-card strong { color: #dc2626; }
.low-card strong { color: #ea580c; }
.attention-card strong { color: #d97706; }

.table-scroll {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
}

.aligned-table-shell {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
  overflow-x: auto;
  overflow-y: hidden;
}

.inventory-table-panel {
  padding: 10px 12px;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 0 0 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.selected-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.inventory-part-table {
  min-width: 1220px;
  table-layout: fixed;
}

.inventory-part-table th {
  background: rgba(148, 163, 184, 0.08);
}

.inventory-part-table tbody tr:last-child td {
  border-bottom: 0;
}

.inventory-part-table th,
.inventory-part-table td,
.transaction-table th,
.transaction-table td {
  padding: 6px 8px;
  vertical-align: middle;
}

.inventory-part-table th,
.inventory-part-table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inventory-part-table .select-col,
.inventory-part-table .select-cell {
  width: 38px;
  padding-inline: 6px;
  text-align: center;
}

.inventory-part-table th:nth-child(2),
.inventory-part-table td:nth-child(2) {
  width: 132px;
}

.inventory-part-table th:nth-child(4),
.inventory-part-table td:nth-child(4) {
  width: 150px;
}

.inventory-part-table th:nth-child(5),
.inventory-part-table td:nth-child(5) {
  width: 88px;
}

.inventory-part-table th:nth-child(6),
.inventory-part-table td:nth-child(6) {
  width: 230px;
}

.inventory-part-table th:nth-child(7),
.inventory-part-table td:nth-child(7) {
  width: 110px;
}

.inventory-part-table th:nth-child(8),
.inventory-part-table td:nth-child(8) {
  width: 170px;
}

.inventory-part-table .action-col,
.inventory-part-table td:last-child {
  width: 176px;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
}

.inventory-part-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.inventory-part-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.inventory-part-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.inventory-part-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.inventory-part-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.inventory-part-row.warning-critical td {
  color: #b91c1c;
}

.inventory-part-row.warning-low td {
  color: #c2410c;
}

.inventory-part-row.warning-attention td {
  color: #a16207;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 5px;
  align-items: center;
  min-width: 0;
}

.row-actions button {
  min-height: 28px;
  padding: 0 8px;
  white-space: nowrap;
}

.inventory-part-table td.action-cell {
  overflow: visible;
  text-align: right;
}

.cell-ellipsis {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-table-shell {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
  overflow: auto;
}

.compact-modal-error {
  margin: 0;
}

.threshold-field {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.threshold-field span {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  white-space: nowrap;
}

.threshold-field.critical span { color: #dc2626; }
.threshold-field.low span { color: #ea580c; }
.threshold-field.attention span { color: #d97706; }

.threshold-field input {
  min-width: 0;
  width: 100%;
}

.threshold-summary {
  max-width: 280px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.threshold-modal {
  display: grid;
  gap: 10px;
  padding: 14px;
  min-width: 0;
}

.compact-modal-head {
  align-items: center;
  margin-bottom: 0;
}

.compact-modal-head > div {
  min-width: 0;
}

.compact-modal-head h3,
.compact-flow-head h3 {
  overflow-wrap: anywhere;
}

.threshold-editor-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.warning-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.warning-badge.normal { background: rgba(148, 163, 184, 0.14); color: #475569; }
.warning-badge.critical { background: rgba(220, 38, 38, 0.12); color: #dc2626; }
.warning-badge.low { background: rgba(234, 88, 12, 0.12); color: #ea580c; }
.warning-badge.attention { background: rgba(217, 119, 6, 0.12); color: #d97706; }

.chart-wrap {
  margin-bottom: 10px;
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
}

.flow-chart {
  width: 100%;
  min-width: 760px;
  height: 320px;
}

.grid-line {
  stroke: rgba(148, 163, 184, 0.28);
  stroke-width: 1;
}

.grid-line.vertical {
  stroke-dasharray: 4 6;
}

.axis {
  stroke: var(--border-color);
  stroke-width: 1.5;
}

.flow-line {
  fill: none;
  stroke: #2563eb;
  stroke-width: 2.5;
}

.flow-point {
  fill: #2563eb;
  cursor: pointer;
}

.axis-label {
  fill: var(--text-secondary);
  font-size: 12px;
}

.axis-label-y { text-anchor: end; }
.axis-label-x { text-anchor: middle; }

.empty-hint {
  margin: 0 0 8px;
  color: var(--text-secondary);
}

tr.active td {
  background: rgba(37, 99, 235, 0.08);
}

.flow-panel {
  display: grid;
  gap: 8px;
  padding: 10px;
  min-width: 0;
}

.transaction-flow-modal {
  max-width: 100%;
  min-width: 0;
}

.compact-flow-head {
  align-items: center;
  margin-bottom: 0;
}

.flow-actions select {
  min-height: 32px;
  width: min(360px, 100%);
  min-width: 0;
}

.transaction-table {
  min-width: 860px;
  table-layout: fixed;
}

.transaction-table th:nth-child(1),
.transaction-table td:nth-child(1) {
  width: 150px;
}

.transaction-table th:nth-child(2),
.transaction-table td:nth-child(2) {
  width: 112px;
}

.transaction-table th:nth-child(3),
.transaction-table td:nth-child(3) {
  width: 180px;
}

.transaction-table th:nth-child(4),
.transaction-table td:nth-child(4),
.transaction-table th:nth-child(5),
.transaction-table td:nth-child(5) {
  width: 100px;
}

.transaction-table th:nth-child(6),
.transaction-table td:nth-child(6) {
  width: 170px;
}

.transaction-table th:nth-child(7),
.transaction-table td:nth-child(7) {
  width: 78px;
  text-align: right;
}

.transaction-table tbody tr {
  cursor: pointer;
}

.compact-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-row-button {
  min-height: 28px;
  padding: 0 8px;
  white-space: nowrap;
}

.transaction-table .outbound {
  color: #dc2626;
}

.transaction-table .inbound {
  color: #15803d;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

@media (max-width: 1100px) {
  .inventory-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .compact-modal-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .threshold-editor-grid {
    grid-template-columns: 1fr;
  }

  .inventory-filter-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .compact-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .inventory-filter-row,
  .compact-summary-grid {
    grid-template-columns: 1fr;
  }

  .threshold-editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>

