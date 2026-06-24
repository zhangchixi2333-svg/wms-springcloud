<!-- 本文件实现库存看板，支持多级库存预警、父子看板展开以及库存流水折线图。 -->
<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api } from '../../../api/wms'
import { formatBusinessType, formatStatus } from '../../../app/displayText'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import type { ConfigItem, InventoryRow, Kanban, PageModel } from '../../../types/app'

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

const thresholdItems = ref<ConfigItem[]>([])
const thresholdDrafts = reactive<Record<string, WarningThresholdConfig>>({})
const batchThresholdDraft = reactive<WarningThresholdConfig>({
  critical: 0,
  low: 0,
  attention: 0,
})
const selectedPartCodes = reactive<Record<string, boolean>>({})
const expandedPartCodes = reactive<Record<string, boolean>>({})
const expandedKanbanIds = reactive<Record<number, boolean>>({})
const activePartCode = ref('')
const activeTransactionId = ref<number | null>(null)
const thresholdSaving = ref(false)

const inventoryWarehouseOptions = computed(() => warehouseOptions(props.model.state.locations))
const inventoryZoneOptions = computed(() => zoneOptions(props.model.state.locations, filters.warehouseName))

const filteredInventoryRows = computed(() =>
  props.model.state.inventory.filter((row) => {
    const warehouseMatch = !filters.warehouseName || row.warehouseName === filters.warehouseName
    const zoneMatch = !filters.zoneName || row.zoneName === filters.zoneName
    const materialMatch =
      !filters.materialKeyword ||
      `${row.partCode} ${row.partName}`.toLowerCase().includes(filters.materialKeyword.toLowerCase())
    const supplierMatch =
      !filters.supplierId ||
      props.model.state.suppliers.find((item) => item.id === filters.supplierId)?.supplierName === row.supplierName
    return warehouseMatch && zoneMatch && materialMatch && supplierMatch
  }),
)

const partRows = computed(() => {
  const grouped = new Map<string, {
    partCode: string
    partName: string
    supplierName: string
    totalQty: number
    locations: InventoryRow[]
    latestUpdatedAt: string
  }>()

  filteredInventoryRows.value.forEach((row) => {
    const current = grouped.get(row.partCode)
    if (!current) {
      grouped.set(row.partCode, {
        partCode: row.partCode,
        partName: row.partName,
        supplierName: row.supplierName,
        totalQty: Number(row.qty),
        locations: [row],
        latestUpdatedAt: row.updatedAt,
      })
      return
    }
    current.totalQty += Number(row.qty)
    current.locations.push(row)
    if (new Date(row.updatedAt).getTime() > new Date(current.latestUpdatedAt).getTime()) {
      current.latestUpdatedAt = row.updatedAt
    }
  })

  return Array.from(grouped.values()).sort((a, b) => a.partCode.localeCompare(b.partCode))
})

const thresholdMap = computed(() => {
  const map = new Map<string, ConfigItem>()
  thresholdItems.value.forEach((item) => map.set(item.itemCode, item))
  return map
})

const partKanbanMap = computed(() => {
  const map = new Map<string, Kanban[]>()
  props.model.state.kanbans
    .filter((item) => item.parentKanban)
    .filter((item) => item.locationCode)
    .filter((item) => ['INBOUND', 'FROZEN', 'REPACK_OUTBOUND', 'REPACK_INBOUND', 'PARTIAL'].includes(item.status))
    .forEach((item) => {
      const rows = map.get(item.partCode) ?? []
      rows.push(item)
      map.set(item.partCode, rows)
    })
  Array.from(map.values()).forEach((rows) =>
    rows.sort((a, b) => (b.inboundTime ?? b.createdAt).localeCompare(a.inboundTime ?? a.createdAt)),
  )
  return map
})

const activePartTransactions = computed(() =>
  props.model.state.transactions
    .filter((row) => !activePartCode.value || row.partCode === activePartCode.value)
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()),
)

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
    const legacy = Number(raw)
    if (Number.isFinite(legacy) && legacy > 0) {
      return { critical: legacy, low: legacy, attention: legacy }
    }
    return emptyThresholdConfig()
  }
}

function thresholdConfig(partCode: string) {
  return thresholdDrafts[partCode] ?? parseThresholdConfig(thresholdMap.value.get(partCode)?.remark)
}

function ensureThresholdDraft(partCode: string) {
  if (!thresholdDrafts[partCode]) {
    thresholdDrafts[partCode] = parseThresholdConfig(thresholdMap.value.get(partCode)?.remark)
  }
  return thresholdDrafts[partCode]
}

function updateThresholdDraft(partCode: string, key: WarningLevelKey, value: string) {
  ensureThresholdDraft(partCode)[key] = Math.max(0, Number(value) || 0)
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
const summary = computed(() => ({
  rowCount: filteredInventoryRows.value.length,
  totalQty: partRows.value.reduce((total, item) => total + Number(item.totalQty), 0),
  partCount: partRows.value.length,
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
}

async function loadThresholds() {
  thresholdItems.value = await api.listConfigItems(WARNING_MODULE_KEY)
  Object.keys(thresholdDrafts).forEach((key) => delete thresholdDrafts[key])
  thresholdItems.value.forEach((item) => {
    thresholdDrafts[item.itemCode] = parseThresholdConfig(item.remark)
  })
}

function toggleSelectAll(checked: boolean) {
  partRows.value.forEach((row) => {
    selectedPartCodes[row.partCode] = checked
  })
}

async function saveThresholdForPart(row: { partCode: string; partName: string }) {
  thresholdSaving.value = true
  try {
    const draft = ensureThresholdDraft(row.partCode)
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
  const selected = partRows.value.filter((row) => selectedPartCodes[row.partCode])
  const batchConfig = normalizeThresholdConfig(batchThresholdDraft)
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
    props.model.actions.setNotice(`已批量设置 ${selected.length} 个零件的分级预警阈值`)
  } finally {
    thresholdSaving.value = false
  }
}

function togglePartExpand(partCode: string) {
  expandedPartCodes[partCode] = !expandedPartCodes[partCode]
}

function toggleKanbanExpand(id: number) {
  expandedKanbanIds[id] = !expandedKanbanIds[id]
}

function openTransactions(partCode: string) {
  activePartCode.value = partCode
  activeTransactionId.value = null
}

async function jumpToTransaction(id: number) {
  activeTransactionId.value = id
  await nextTick()
  document.getElementById(`tx-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function pointTitle(point: { businessType: string; qtyChange: number; runningQty: number; createdAt: string; remark: string }) {
  return `${formatBusinessType(point.businessType)} | 变化 ${point.qtyChange} | 库存 ${point.runningQty} | ${new Date(point.createdAt).toLocaleString('zh-CN', { hour12: false })}${point.remark ? ` | ${point.remark}` : ''}`
}

onMounted(async () => {
  await loadThresholds()
  if (partRows.value[0]) activePartCode.value = partRows.value[0].partCode
})
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>库存看板</h3>
          <p>按零件聚合库存，支持多级预警、看板展开和库存流水图。</p>
        </div>
      </div>
      <div class="form-grid four">
        <select v-model="filters.warehouseName" @change="filters.zoneName = ''">
          <option value="">全部仓库</option>
          <option v-for="warehouse in inventoryWarehouseOptions" :key="warehouse" :value="warehouse">{{ warehouse }}</option>
        </select>
        <select v-model="filters.zoneName">
          <option value="">全部库区</option>
          <option v-for="zone in inventoryZoneOptions" :key="zone" :value="zone">{{ zone }}</option>
        </select>
        <input v-model="filters.materialKeyword" placeholder="物料 / 零件号" />
        <select v-model.number="filters.supplierId">
          <option :value="0">全部供应商</option>
          <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">{{ item.supplierCode }} | {{ item.supplierName }}</option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" @click="resetFilters">重置筛选</button>
      </div>
    </section>

    <section class="summary-grid">
      <div class="panel summary-card"><span class="summary-label">库存记录数</span><strong>{{ summary.rowCount }}</strong></div>
      <div class="panel summary-card"><span class="summary-label">库存总量</span><strong>{{ summary.totalQty }}</strong></div>
      <div class="panel summary-card"><span class="summary-label">涉及零件</span><strong>{{ summary.partCount }}</strong></div>
      <div class="panel summary-card warning-card"><span class="summary-label">预警零件</span><strong>{{ summary.warningCount }}</strong></div>
      <div class="panel summary-card critical-card"><span class="summary-label">严重不足</span><strong>{{ summary.criticalCount }}</strong></div>
      <div class="panel summary-card low-card"><span class="summary-label">低库存</span><strong>{{ summary.lowCount }}</strong></div>
      <div class="panel summary-card attention-card"><span class="summary-label">关注</span><strong>{{ summary.attentionCount }}</strong></div>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>批量设置预警阈值</h3>
          <p>可统一设置严重不足、低库存、关注三档阈值。</p>
        </div>
        <div class="warning-actions">
          <label class="threshold-field critical">
            <span>严重不足阈值</span>
            <input v-model.number="batchThresholdDraft.critical" type="number" min="0" step="0.001" />
          </label>
          <label class="threshold-field low">
            <span>低库存阈值</span>
            <input v-model.number="batchThresholdDraft.low" type="number" min="0" step="0.001" />
          </label>
          <label class="threshold-field attention">
            <span>关注阈值</span>
            <input v-model.number="batchThresholdDraft.attention" type="number" min="0" step="0.001" />
          </label>
          <button :disabled="thresholdSaving" @click="batchSaveThreshold">批量保存</button>
        </div>
      </div>
    </section>

    <section class="panel">
      <table class="table">
        <thead>
          <tr>
            <th><input type="checkbox" :checked="allSelected" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
            <th>零件编码</th>
            <th>零件名称</th>
            <th>供应商</th>
            <th>库存总量</th>
            <th>预警阈值</th>
            <th>预警状态</th>
            <th>最近更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in partRows" :key="row.partCode">
            <tr :class="warningLevel(row.partCode, row.totalQty) ? [`warning-${warningLevel(row.partCode, row.totalQty)}`] : []">
              <td><input v-model="selectedPartCodes[row.partCode]" type="checkbox" /></td>
              <td>{{ row.partCode }}</td>
              <td>{{ row.partName }}</td>
              <td>{{ row.supplierName }}</td>
              <td>{{ row.totalQty }}</td>
              <td>
                <div class="threshold-grid">
                  <label class="threshold-field critical">
                    <span>严重不足</span>
                    <input :value="ensureThresholdDraft(row.partCode).critical" type="number" min="0" step="0.001" @input="updateThresholdDraft(row.partCode, 'critical', ($event.target as HTMLInputElement).value)" />
                  </label>
                  <label class="threshold-field low">
                    <span>低库存</span>
                    <input :value="ensureThresholdDraft(row.partCode).low" type="number" min="0" step="0.001" @input="updateThresholdDraft(row.partCode, 'low', ($event.target as HTMLInputElement).value)" />
                  </label>
                  <label class="threshold-field attention">
                    <span>关注</span>
                    <input :value="ensureThresholdDraft(row.partCode).attention" type="number" min="0" step="0.001" @input="updateThresholdDraft(row.partCode, 'attention', ($event.target as HTMLInputElement).value)" />
                  </label>
                  <button class="secondary-button" :disabled="thresholdSaving" @click="saveThresholdForPart(row)">保存</button>
                </div>
              </td>
              <td><span :class="['warning-badge', warningLevel(row.partCode, row.totalQty) || 'normal']">{{ warningLevelLabel(warningLevel(row.partCode, row.totalQty)) }}</span></td>
              <td>{{ new Date(row.latestUpdatedAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
              <td class="action-row">
                <button class="secondary-button" @click="togglePartExpand(row.partCode)">{{ expandedPartCodes[row.partCode] ? '收起库存' : '展开库存' }}</button>
                <button class="secondary-button" @click="openTransactions(row.partCode)">库存流水</button>
              </td>
            </tr>
            <tr v-if="expandedPartCodes[row.partCode]">
              <td colspan="9">
                <div class="expand-stack">
                  <section class="sub-panel">
                    <h4>库位明细</h4>
                    <div class="location-grid">
                      <article v-for="location in row.locations" :key="location.id" class="location-card">
                        <strong>{{ location.locationCode }}</strong>
                        <span>{{ location.warehouseName }} / {{ location.zoneName }}</span>
                        <span>数量 {{ location.qty }}</span>
                        <span>{{ new Date(location.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</span>
                      </article>
                    </div>
                  </section>
                  <section class="sub-panel">
                    <h4>对应看板</h4>
                    <div class="kanban-grid">
                      <article v-for="kanban in partKanbanMap.get(row.partCode) ?? []" :key="kanban.id" class="kanban-card">
                        <div class="kanban-head">
                          <div>
                            <strong>{{ kanban.kanbanNo }}</strong>
                            <p>{{ kanban.warehouseName }} / {{ kanban.zoneName }}</p>
                          </div>
                          <QrCodeImage :text="kanban.qrContent || kanban.barcode" :size="80" />
                        </div>
                        <div class="kanban-meta">
                          <span>状态 {{ formatStatus(kanban.status) }}</span>
                          <span>箱数 {{ kanban.boxCount }} / 每箱 {{ kanban.unitPerBox }}</span>
                          <span>数量 {{ kanban.qty }}</span>
                        </div>
                        <div class="action-row">
                          <button class="secondary-button" @click="toggleKanbanExpand(kanban.id)">{{ expandedKanbanIds[kanban.id] ? '收起子看板' : `展开子看板(${(kanban.children ?? []).length})` }}</button>
                        </div>
                        <div v-if="expandedKanbanIds[kanban.id]" class="child-grid">
                          <div v-for="child in kanban.children ?? []" :key="child.id" class="child-card">
                            <QrCodeImage :text="child.qrContent || child.barcode" :size="72" />
                            <strong>{{ child.kanbanNo }}</strong>
                            <span>第 {{ child.boxIndex }} 箱</span>
                            <span>数量 {{ child.qty }}</span>
                            <span>{{ formatStatus(child.status) }}</span>
                          </div>
                        </div>
                      </article>
                    </div>
                  </section>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>零件库存流水</h3>
          <p>点击折线图上的事件点可跳转到下方对应流水记录。</p>
        </div>
        <div class="action-row">
          <select v-model="activePartCode">
            <option value="">选择零件</option>
            <option v-for="row in partRows" :key="row.partCode" :value="row.partCode">{{ row.partCode }} | {{ row.partName }}</option>
          </select>
        </div>
      </div>

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
      <p v-else class="empty-hint">当前零件还没有可展示的库存流水。</p>

      <table class="table">
        <thead>
          <tr>
            <th>流水号</th>
            <th>类型</th>
            <th>业务单号</th>
            <th>条码</th>
            <th>库位</th>
            <th>数量变化</th>
            <th>备注</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in activePartTransactions" :id="`tx-${row.id}`" :key="row.id" :class="{ active: activeTransactionId === row.id }">
            <td class="mono">{{ row.transactionNo }}</td>
            <td>{{ formatBusinessType(row.businessType) }}</td>
            <td>{{ row.businessNo }}</td>
            <td class="mono">{{ row.barcode }}</td>
            <td>{{ row.locationCode }}</td>
            <td :class="{ outbound: Number(row.qtyChange) < 0, inbound: Number(row.qtyChange) > 0 }">{{ row.qtyChange }}</td>
            <td>{{ row.remark }}</td>
            <td>{{ new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.filter-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: grid;
  gap: 8px;
}

.summary-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.warning-card strong { color: #dc2626; }
.critical-card strong, tr.warning-critical td { color: #dc2626; }
.low-card strong, tr.warning-low td { color: #ea580c; }
.attention-card strong, tr.warning-attention td { color: #d97706; }

.threshold-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr)) auto;
  gap: 8px;
  align-items: end;
}

.warning-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(132px, 1fr)) auto;
  gap: 10px;
  align-items: end;
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

.expand-stack,
.location-grid,
.kanban-grid,
.child-grid {
  display: grid;
  gap: 12px;
}

.sub-panel {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.location-grid { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
.kanban-grid { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.child-grid { grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); }

.location-card,
.kanban-card,
.child-card {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
  display: grid;
  gap: 4px;
}

.kanban-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
}

.kanban-meta {
  display: grid;
  gap: 4px;
}

.child-card {
  justify-items: center;
  text-align: center;
}

.chart-wrap {
  margin-bottom: 16px;
  overflow-x: auto;
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
  color: var(--text-secondary);
}

tr.active td {
  background: rgba(37, 99, 235, 0.08);
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .threshold-grid,
  .warning-actions {
    grid-template-columns: 1fr;
  }
}
</style>
