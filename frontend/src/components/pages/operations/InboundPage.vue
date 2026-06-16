<!-- 本文件实现入库工作台，支持批量选件、批量打印、模拟扫码和连续扫码入库。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { equipmentCodeOptions, warehouseZoneOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { InboundDraftItem, InboundOrder, Kanban, PageModel, Part } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const mode = ref<'query' | 'edit' | 'print' | 'scan'>('query')
const printOrders = ref<InboundOrder[]>([])
const selectedOrderIds = reactive<Record<number, boolean>>({})
const scanInputRef = ref<HTMLInputElement | null>(null)

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
const batchDraft = reactive<Record<number, { plannedQty: number; boxCount: number; pendingRepack: boolean; equipmentCode: string; packageCapacity: number; warehouseZone: string }>>({})

const printedInboundScanForm = reactive({
  barcode: '',
  locationCode: '',
})

const scanMatchHint = ref('')
const scanSuccessMap = reactive<Record<string, boolean>>({})
const inboundWarehouseZoneOptions = computed(() => warehouseZoneOptions(props.model.state.locations))
const inboundEquipmentOptions = computed(() => equipmentCodeOptions(props.model.state.equipment))
const workModes = computed(() => [
  { key: 'query', label: '查看入库' },
  { key: 'edit', label: '创建入库单' },
  { key: 'print', label: '打印入库单', disabled: !printOrders.value.length },
  { key: 'scan', label: '扫码入库' },
])

const supplierParts = computed(() =>
  props.model.state.parts.filter((item) => !form.supplierId || item.supplierId === form.supplierId),
)

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

function createDraftItem(part?: Part): InboundDraftItem {
  return {
    partId: part?.id ?? 0,
    plannedQty: 1,
    boxCount: 1,
    pendingRepack: false,
    equipmentCode: part?.defaultEquipmentCode ?? '',
    packageCapacity: part?.defaultPackageCapacity ?? 1,
    warehouseZone: '',
  }
}

function ensureBatchDraft(part: Part) {
  if (!batchDraft[part.id]) {
    batchDraft[part.id] = {
      plannedQty: 1,
      boxCount: 1,
      pendingRepack: false,
      equipmentCode: part.defaultEquipmentCode ?? '',
      packageCapacity: part.defaultPackageCapacity ?? 1,
      warehouseZone: '',
    }
  }
  return batchDraft[part.id]
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
}

function resetCreateForm() {
  form.supplierId = props.model.state.suppliers[0]?.id ?? 0
  form.items = [createDraftItem()]
  resetBatchSelection()
}

function openCreate() {
  resetCreateForm()
  mode.value = 'edit'
}

function handleModeSelect(nextMode: string) {
  if (nextMode === 'edit') {
    openCreate()
  }
}

function openPrint(order: InboundOrder) {
  printOrders.value = [order]
  mode.value = 'print'
}

function openBatchPrint() {
  printOrders.value = filteredOrders.value.filter((item) => selectedOrderIds[item.id])
  mode.value = 'print'
}

function toggleSelectAllOrders(checked: boolean) {
  filteredOrders.value.forEach((item) => {
    selectedOrderIds[item.id] = checked
  })
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
        packageCapacity: draft.packageCapacity,
        warehouseZone: draft.warehouseZone,
      })
    })
  form.items = form.items.filter((item, index, rows) => rows.findIndex((row) => row.partId === item.partId) === index)
  resetBatchSelection()
}

function handlePartChange(item: InboundDraftItem) {
  const part = props.model.state.parts.find((candidate) => candidate.id === item.partId)
  if (!part) return
  if (!item.equipmentCode) {
    item.equipmentCode = part.defaultEquipmentCode ?? ''
  }
  if (!item.packageCapacity || item.packageCapacity <= 0) {
    item.packageCapacity = part.defaultPackageCapacity ?? 1
  }
}

async function submit() {
  await props.model.actions.createInboundOrder({
    supplierId: form.supplierId,
    items: form.items.filter((item) => item.partId > 0 && item.plannedQty > 0 && item.boxCount > 0 && item.warehouseZone),
  })
  mode.value = 'query'
}

function browserPrint() {
  window.print()
}

function kanbansForInbound(order: InboundOrder) {
  return props.model.state.kanbans.filter((item) => item.inboundNo === order.inboundNo)
}

function normalizeScanCode(value: string) {
  return value.trim()
}

function findMatchedKanban(scanCode: string) {
  const normalized = normalizeScanCode(scanCode)
  if (!normalized) return null
  const direct = props.model.state.kanbans.find((item) => item.barcode === normalized || item.qrContent === normalized)
  if (direct) return direct
  const parts = normalized.split('|')
  if (parts.length === 3 && parts[0] === 'WMS-KANBAN') {
    return props.model.state.kanbans.find((item) => item.barcode === parts[2]) ?? null
  }
  return null
}

function findPlannedLocationCode(scanCode: string) {
  const kanban = findMatchedKanban(scanCode)
  if (!kanban) {
    scanMatchHint.value = '未识别到对应看板'
    return ''
  }
  const location = props.model.state.locations.find(
    (item) => item.warehouseName === kanban.warehouseName && item.zoneName === kanban.zoneName,
  )
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
  const first = props.model.state.kanbans.find((item) => ['CREATED', 'WAIT_SCAN'].includes(item.status))?.qrContent
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
  scanMatchHint.value = '入库成功，已自动定位到下一次扫码'
  await focusScanInput()
}

async function simulateScanKanban(kanban: Kanban) {
  printedInboundScanForm.barcode = kanban.qrContent || kanban.barcode
  printedInboundScanForm.locationCode = findPlannedLocationCode(printedInboundScanForm.barcode)
  await submitPrintedInboundScan()
}

async function submitInboundScanByEnter() {
  if (!printedInboundScanForm.barcode) {
    return
  }
  if (!printedInboundScanForm.locationCode) {
    printedInboundScanForm.locationCode = findPlannedLocationCode(printedInboundScanForm.barcode)
  }
  if (!printedInboundScanForm.locationCode) {
    await focusScanInput()
    return
  }
  await submitPrintedInboundScan()
}

function isKanbanScanSuccess(kanban: Kanban) {
  const latest = props.model.state.kanbans.find((item) => item.id === kanban.id)
  return Boolean(scanSuccessMap[kanban.qrContent] || scanSuccessMap[kanban.barcode] || latest?.status === 'INBOUND')
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
  <WorkModePage
    v-model="mode"
    :modes="workModes"
    hint="默认先查询；创建、打印和扫码入库共用统一工作台，也支持打印页直接模拟扫码入库。"
    @select="handleModeSelect"
  >
    <section v-if="mode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>入库单查询</h3>
            <p>支持按状态、供应商和入库单号查询；创建入库单后系统会自动生成看板。</p>
          </div>
          <div class="action-row">
            <button class="secondary-button" @click="toggleSelectAllOrders(true)">全选当前结果</button>
            <button class="secondary-button" @click="toggleSelectAllOrders(false)">取消全选</button>
            <button @click="openBatchPrint" :disabled="!canBatchPrint">批量打印 {{ selectedOrderCount }}</button>
          </div>
        </div>
        <div class="form-grid filters-grid">
          <select v-model="filters.status">
            <option value="">全部状态</option>
            <option value="CREATED">CREATED</option>
            <option value="PARTIAL">PARTIAL</option>
            <option value="COMPLETED">COMPLETED</option>
          </select>
          <select v-model.number="filters.supplierId">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <input v-model="filters.inboundNo" placeholder="输入入库单号筛选" />
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>选择</th>
              <th>入库单号</th>
              <th>供应商</th>
              <th>状态</th>
              <th>明细摘要</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in filteredOrders" :key="order.id">
              <td><input v-model="selectedOrderIds[order.id]" type="checkbox" /></td>
              <td>{{ order.inboundNo }}</td>
              <td>{{ order.supplierName }}</td>
              <td>{{ order.status }}</td>
              <td>
                <span v-for="detail in order.items" :key="detail.id" class="inline-tag">
                  {{ detail.partCode }} / 计划 {{ detail.plannedQty }} / 已收 {{ detail.receivedQty }} / {{ detail.warehouseZone }}
                </span>
              </td>
              <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
              <td>
                <div class="action-row">
                  <button class="secondary-button" @click="openPrint(order)">打印</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="mode === 'edit'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>创建入库单</h3>
            <p>先选择供应商，再批量勾选该供应商的零件带入明细；保存后系统会自动生成看板并进入后续扫码流程。</p>
          </div>
        </div>

        <div class="form-grid two">
          <select v-model.number="form.supplierId">
            <option :value="0" disabled>选择供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <div class="action-row">
            <button class="secondary-button" @click="addItem">手工新增明细</button>
            <button class="secondary-button" @click="applyBatchParts" :disabled="!Object.values(batchSelection).some(Boolean)">批量加入明细</button>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>供应商零件批量选择</h3>
            <p>适合一次选择同一供应商下多种零件后批量生成入库明细。</p>
          </div>
        </div>
        <table class="table">
          <thead>
            <tr>
              <th>选择</th>
              <th>零件编码</th>
              <th>零件名称</th>
              <th>单位</th>
              <th>计划数量</th>
              <th>箱数</th>
              <th>默认器具编码</th>
              <th>包装容量</th>
              <th>仓库/库区</th>
              <th>待转包</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="part in supplierParts" :key="part.id">
              <td><input v-model="batchSelection[part.id]" type="checkbox" /></td>
              <td>{{ part.partCode }}</td>
              <td>{{ part.partName }}</td>
              <td>{{ part.unit }}</td>
              <td><input v-model.number="ensureBatchDraft(part).plannedQty" type="number" min="1" /></td>
              <td><input v-model.number="ensureBatchDraft(part).boxCount" type="number" min="1" /></td>
              <td>
                <select v-model="ensureBatchDraft(part).equipmentCode">
                  <option value="">选择器具编码</option>
                  <option v-for="equipment in inboundEquipmentOptions" :key="equipment.value" :value="equipment.value">
                    {{ equipment.label }}
                  </option>
                </select>
              </td>
              <td><input v-model.number="ensureBatchDraft(part).packageCapacity" type="number" min="0.001" step="0.001" /></td>
              <td>
                <select v-model="ensureBatchDraft(part).warehouseZone">
                  <option value="">选择仓库/库区</option>
                  <option v-for="zone in inboundWarehouseZoneOptions" :key="zone.value" :value="zone.value">
                    {{ zone.label }}
                  </option>
                </select>
              </td>
              <td><input v-model="ensureBatchDraft(part).pendingRepack" type="checkbox" /></td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="panel">
        <div class="section-head">
          <div>
            <h3>入库明细</h3>
            <p>可以继续手工补充或修正批量带入的明细。</p>
          </div>
        </div>

        <div v-for="(item, index) in form.items" :key="index" class="detail-card">
          <div class="detail-grid">
            <select v-model.number="item.partId" @change="handlePartChange(item)">
              <option :value="0" disabled>选择零件</option>
              <option v-for="part in model.state.parts.filter((candidate) => !form.supplierId || candidate.supplierId === form.supplierId)" :key="part.id" :value="part.id">
                {{ part.partCode }} | {{ part.partName }} | {{ part.unit }}
              </option>
            </select>
            <input v-model.number="item.plannedQty" type="number" min="1" placeholder="入库数量" />
            <input v-model.number="item.boxCount" type="number" min="1" placeholder="箱数" />
            <select v-model="item.equipmentCode">
              <option value="">选择器具编码</option>
              <option v-for="equipment in inboundEquipmentOptions" :key="equipment.value" :value="equipment.value">
                {{ equipment.label }}
              </option>
            </select>
            <input v-model.number="item.packageCapacity" type="number" min="0.001" step="0.001" placeholder="包装容量" />
            <select v-model="item.warehouseZone">
              <option value="">选择仓库/库区</option>
              <option v-for="zone in inboundWarehouseZoneOptions" :key="zone.value" :value="zone.value">
                {{ zone.label }}
              </option>
            </select>
            <label class="checkbox-line">
              <input v-model="item.pendingRepack" type="checkbox" />
              <span>待转包</span>
            </label>
            <button class="secondary-button" @click="removeItem(index)">删除</button>
          </div>
        </div>

        <div class="footer-actions">
          <button @click="submit">保存入库单</button>
          <button class="secondary-button" @click="mode = 'query'">返回查询</button>
        </div>
      </section>
    </section>

    <section v-else-if="mode === 'print'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>入库打印与模拟扫码</h3>
            <p>支持一次批量打印多个订单，打印页紧凑排版；点击模拟扫码标志后，页面会自动识别并直接入库。</p>
          </div>
          <div class="action-row">
            <button @click="browserPrint">浏览器打印</button>
            <button class="secondary-button" @click="mode = 'query'">返回查询</button>
          </div>
        </div>

        <div class="scan-toolbar">
          <input
            ref="scanInputRef"
            v-model="printedInboundScanForm.barcode"
            class="scan-toolbar-input"
            placeholder="浏览器模拟扫码：输入二维码内容或条码后回车"
            @keydown.enter.prevent="submitInboundScanByEnter"
          />
          <select v-model="printedInboundScanForm.locationCode">
            <option value="">自动匹配入库库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitPrintedInboundScan">确认入库</button>
          <button class="secondary-button" @click="fillFirstInboundScanCode">模拟扫码填充</button>
        </div>
        <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>

        <div class="print-batch-columns">
          <article v-for="order in printOrders" :key="order.id" class="print-card">
            <header class="print-card-header">
              <div>
                <h2>WMS 入库单</h2>
                <p>{{ order.inboundNo }}</p>
              </div>
              <div class="print-card-meta">
                <p>{{ order.supplierName }}</p>
                <p>{{ order.status }}</p>
              </div>
            </header>

            <table class="table compact-table">
              <thead>
                <tr>
                  <th>零件</th>
                  <th>数量</th>
                  <th>箱数</th>
                  <th>器具</th>
                  <th>仓库/库区</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in order.items" :key="item.id">
                  <td>{{ item.partCode }}</td>
                  <td>{{ item.plannedQty }}</td>
                  <td>{{ item.boxCount }}</td>
                  <td>{{ item.equipmentCode || '-' }}</td>
                  <td>{{ item.warehouseZone }}</td>
                </tr>
              </tbody>
            </table>

            <div class="kanban-compact-list">
              <section v-for="kanban in kanbansForInbound(order)" :key="kanban.id" class="kanban-compact-card">
                <div class="kanban-compact-top">
                  <QrCodeImage :text="kanban.qrContent" :size="84" />
                  <div class="kanban-compact-info">
                    <strong>{{ kanban.kanbanNo }}</strong>
                    <p class="mono">{{ kanban.barcode }}</p>
                    <p>{{ kanban.partCode }} | {{ kanban.qty }}</p>
                    <p>{{ kanban.warehouseName }} / {{ kanban.zoneName }}</p>
                  </div>
                </div>
                <div class="kanban-compact-actions">
                  <button class="secondary-button" @click="simulateScanKanban(kanban)">模拟扫码</button>
                  <span v-if="isKanbanScanSuccess(kanban)" class="scan-success-badge">入库成功</span>
                  <span v-else class="scan-pending-badge">待入库</span>
                </div>
              </section>
            </div>
          </article>
        </div>
      </section>
    </section>

    <section v-else-if="mode === 'scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码入库</h3>
          <p>支持扫描枪直接输入二维码内容或条码并回车提交，成功后自动清空并重新聚焦，适合连续扫码。</p>
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
            <option value="">自动匹配入库库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitPrintedInboundScan">确认入库</button>
        </div>
        <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
        <div class="scan-assist-row two-col">
          <button class="secondary-button" @click="fillFirstInboundScanCode">模拟扫码填充</button>
        </div>
        <div v-if="printedInboundScanForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="printedInboundScanForm.barcode" :size="160" />
          <p class="mono">{{ printedInboundScanForm.barcode }}</p>
        </div>
      </div>
    </section>
  </WorkModePage>
</template>

<style scoped>
.filters-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.checkbox-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
}

.checkbox-line input {
  width: 18px;
  min-height: 18px;
}

.scan-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1.4fr) minmax(220px, 1fr) auto auto;
  gap: 12px;
  align-items: center;
}

.scan-toolbar-input {
  min-width: 0;
}

.scan-hint {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.print-batch-columns {
  column-count: 2;
  column-gap: 16px;
  margin-top: 16px;
}

.print-card {
  break-inside: avoid;
  display: grid;
  gap: 10px;
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid var(--border-color);
  background: var(--panel-bg);
}

.print-card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.print-card-header h2,
.print-card-header p,
.print-card-meta p {
  margin: 0;
}

.compact-table th,
.compact-table td {
  padding: 6px 8px;
  font-size: 12px;
}

.kanban-compact-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.kanban-compact-card {
  display: grid;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--border-color);
}

.kanban-compact-top {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.kanban-compact-info {
  display: grid;
  gap: 4px;
}

.kanban-compact-info p,
.kanban-compact-info strong {
  margin: 0;
}

.kanban-compact-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.scan-success-badge,
.scan-pending-badge {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  font-size: 12px;
}

.scan-success-badge {
  color: #0f7b3e;
  border-color: #0f7b3e;
}

.scan-pending-badge {
  color: var(--text-secondary);
}

.scan-assist-row {
  display: grid;
  margin-top: 12px;
  gap: 12px;
}

.two-col {
  grid-template-columns: minmax(0, 1fr) auto;
}

@media (max-width: 1180px) {
  .filters-grid,
  .detail-grid,
  .two-col,
  .scan-toolbar,
  .kanban-compact-list {
    grid-template-columns: 1fr;
  }

  .print-batch-columns {
    column-count: 1;
  }

  .print-card-header,
  .kanban-compact-top {
    grid-template-columns: 1fr;
  }
}

@media print {
  .print-batch-columns {
    column-count: 2;
    column-gap: 12px;
  }

  .print-card {
    padding: 8px;
    margin-bottom: 12px;
    box-shadow: none;
  }

  .kanban-compact-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kanban-compact-actions button {
    display: none;
  }
}
</style>
