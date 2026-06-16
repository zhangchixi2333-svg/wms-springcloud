<!-- 本文件实现出库工作台，支持先建出库单、绑定入库来源、按父子看板扫码出库。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { formatStatus } from '../../../app/displayText'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { Kanban, OutboundDraftItem, OutboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const viewMode = ref<'query' | 'create' | 'manual' | 'print' | 'scan'>('query')
const printOrder = ref<OutboundOrder | null>(null)
const scanInputRef = ref<HTMLInputElement | null>(null)
const expandedParents = reactive<Record<number, boolean>>({})

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

const printedOutboundScanForm = reactive({
  barcode: '',
})

const form = reactive({
  customerId: 0,
  inboundOrderNos: [] as string[],
  items: [createDraftItem()],
})

const activeScanOrder = computed(() => props.model.state.outboundOrders.find((order) => order.outboundNo === scanForm.outboundOrderNo) ?? null)
const outboundOrderOptions = computed(() => props.model.state.outboundOrders.filter((order) => order.status !== 'COMPLETED'))
const outboundWarehouseOptions = computed(() => warehouseOptions(props.model.state.locations))
const workModes = computed(() => [
  { key: 'query', label: '查看出库' },
  { key: 'create', label: '创建出库单' },
  { key: 'print', label: '打印出库单', disabled: !printOrder.value },
  { key: 'scan', label: '扫码出库' },
  { key: 'manual', label: '手工入账' },
])

function createDraftItem(): OutboundDraftItem {
  return {
    partId: 0,
    plannedQty: 1,
    warehouseName: '',
    zoneName: '',
  }
}

function outboundZoneOptions(warehouseName: string) {
  return zoneOptions(props.model.state.locations, warehouseName)
}

const rows = computed(() =>
  props.model.state.outboundOrders.filter((order) => {
    const statusMatch = !filters.status || order.status === filters.status
    const customerMatch = !filters.customerId || order.customerId === filters.customerId
    const outboundMatch = !filters.outboundNo || order.outboundNo.toLowerCase().includes(filters.outboundNo.toLowerCase())
    return statusMatch && customerMatch && outboundMatch
  }),
)

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
  filters.customerId = 0
  filters.outboundNo = ''
}

function openCreate() {
  form.customerId = 0
  form.inboundOrderNos = []
  form.items = [createDraftItem()]
  viewMode.value = 'create'
}

function handleModeSelect(nextMode: string) {
  if (nextMode === 'create') {
    openCreate()
  }
}

function openPrint(order: OutboundOrder) {
  printOrder.value = order
  printedOutboundScanForm.barcode = ''
  viewMode.value = 'print'
}

function browserPrint() {
  window.print()
}

function sourceText(order: OutboundOrder) {
  return order.inboundOrderNos.length ? order.inboundOrderNos.join('，') : '-'
}

function inboundOrderLabel(inboundNo: string) {
  const order = props.model.state.inboundOrders.find((item) => item.inboundNo === inboundNo)
  return order ? `${order.inboundNo} | ${order.supplierName} | ${order.status}` : inboundNo
}

function handleScanOrderChange() {
  scanForm.barcode = ''
}

function parentKanbansForOutbound(order: OutboundOrder) {
  const partCodes = new Set(order.items.map((item) => item.partCode))
  const inboundNos = new Set(order.inboundOrderNos)
  return props.model.state.kanbans
    .filter((item) => item.parentKanban && item.status === 'INBOUND' && partCodes.has(item.partCode) && inboundNos.has(item.inboundNo))
    .sort((a, b) => (a.inboundTime ?? a.createdAt).localeCompare(b.inboundTime ?? b.createdAt))
}

function outboundScanOptionsForOrder(order: OutboundOrder) {
  return parentKanbansForOutbound(order).map((item) => ({
    label: `${item.kanbanNo} | ${item.partCode} | ${item.barcode}`,
    value: item.qrContent || item.barcode,
  }))
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
  await props.model.actions.scanOutbound(scanForm)
  scanForm.barcode = ''
  await focusScanInput()
}

async function submitScanByEnter() {
  if (!scanForm.barcode || !scanForm.outboundOrderNo) return
  await submitScan()
}

async function submitCreate() {
  await props.model.actions.createOutboundOrder({
    customerId: form.customerId || null,
    inboundOrderNos: form.inboundOrderNos,
    items: form.items.filter((item) => item.partId > 0 && item.plannedQty > 0 && item.warehouseName && item.zoneName),
  })
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
  if (!printOrder.value) return
  await props.model.actions.scanOutbound({
    barcode: printedOutboundScanForm.barcode,
    outboundOrderNo: printOrder.value.outboundNo,
  })
  printedOutboundScanForm.barcode = ''
  await focusScanInput()
}

async function submitPrintedOutboundScanByEnter() {
  if (!printedOutboundScanForm.barcode || !printOrder.value) return
  await submitPrintedOutboundScan()
}

function toggleExpanded(kanbanId: number) {
  expandedParents[kanbanId] = !expandedParents[kanbanId]
}

async function simulatePrintScan(kanban: Kanban) {
  if (!printOrder.value) return
  printedOutboundScanForm.barcode = kanban.qrContent || kanban.barcode
  await submitPrintedOutboundScan()
}

watch(viewMode, async (value) => {
  if (value === 'scan' || value === 'print') {
    await focusScanInput()
  }
})
</script>

<template>
  <WorkModePage
    v-model="viewMode"
    :modes="workModes"
    hint="出库必须绑定唯一出库单；出库单可绑定一个或多个入库单来源，扫码时默认优先父看板整批处理。"
    @select="handleModeSelect"
  >
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>出库筛选</h3>
            <p>先定位唯一出库单，再进行扫码出库或打印追踪。</p>
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
              <th>出库单号</th>
              <th>客户</th>
              <th>来源入库单</th>
              <th>状态</th>
              <th>明细摘要</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in rows" :key="order.id">
              <td>{{ order.outboundNo }}</td>
              <td>{{ order.customerName }}</td>
              <td class="source-cell">{{ sourceText(order) }}</td>
              <td>{{ formatStatus(order.status) }}</td>
              <td>{{ order.items.length }} 条</td>
              <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
              <td><button class="secondary-button" @click="openPrint(order)">打印</button></td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else-if="viewMode === 'scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码出库</h3>
          <p>先选出库单，再扫父看板或箱级子看板执行出库。父看板会联动处理全部子箱。</p>
        </div>
      </div>
      <div class="form-grid three">
        <select v-model="scanForm.outboundOrderNo" @change="handleScanOrderChange">
          <option value="">选择出库单</option>
          <option v-for="order in outboundOrderOptions" :key="order.id" :value="order.outboundNo">
            {{ order.outboundNo }} | 来源 {{ sourceText(order) }} | {{ formatStatus(order.status) }}
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
          <option value="">辅助选择待出库父看板</option>
          <option v-for="item in activeScanOrder ? outboundScanOptionsForOrder(activeScanOrder) : []" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <button class="secondary-button" :disabled="!scanForm.outboundOrderNo" @click="fillFirstOutboundScanCode">填充首个待出库码</button>
      </div>
    </section>

    <section v-else-if="viewMode === 'create'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>创建出库单</h3>
            <p>出库单可绑定一个或多个入库单来源，用于说明本次出库消耗了哪些入库批次。</p>
          </div>
        </div>
        <div class="form-grid three">
          <select v-model.number="form.customerId">
            <option :value="0">未绑定客户</option>
            <option v-for="item in model.state.customers" :key="item.id" :value="item.id">
              {{ item.customerCode }} | {{ item.customerName }}
            </option>
          </select>
          <select v-model="form.inboundOrderNos" multiple>
            <option v-for="item in model.state.inboundOrders" :key="item.id" :value="item.inboundNo">
              {{ inboundOrderLabel(item.inboundNo) }}
            </option>
          </select>
        </div>
      </section>

      <section class="panel">
        <div class="section-head">
          <div><h3>出库明细</h3></div>
          <div class="action-row"><button class="secondary-button" @click="addItem">新增一行</button></div>
        </div>
        <div class="detail-stack">
          <div v-for="(item, index) in form.items" :key="index" class="detail-row">
            <select v-model.number="item.partId">
              <option :value="0">选择零件</option>
              <option v-for="part in model.state.parts" :key="part.id" :value="part.id">
                {{ part.partCode }} | {{ part.partName }}
              </option>
            </select>
            <input v-model.number="item.plannedQty" type="number" min="0.001" step="0.001" placeholder="计划数量" />
            <select v-model="item.warehouseName">
              <option value="">仓库</option>
              <option v-for="warehouse in outboundWarehouseOptions" :key="warehouse" :value="warehouse">
                {{ warehouse }}
              </option>
            </select>
            <select v-model="item.zoneName">
              <option value="">库区</option>
              <option v-for="zone in outboundZoneOptions(item.warehouseName)" :key="zone" :value="zone">
                {{ zone }}
              </option>
            </select>
            <button class="secondary-button" @click="removeItem(index)">删除</button>
          </div>
        </div>
        <div class="footer-actions">
          <button @click="submitCreate">保存出库单</button>
          <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
        </div>
      </section>
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

    <section v-else-if="printOrder" class="panel print-panel">
      <div class="section-head print-toolbar">
        <div>
          <h3>出库打印</h3>
          <p>打印页可直接模拟扫码出库，并展开查看每个父看板下的箱级子看板。</p>
        </div>
        <div class="action-row">
          <button @click="browserPrint">浏览器打印</button>
          <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
        </div>
      </div>

      <section class="panel compact-scan-panel">
        <div class="form-grid two">
          <input
            ref="scanInputRef"
            v-model="printedOutboundScanForm.barcode"
            placeholder="打印后可直接模拟扫码出库"
            @keydown.enter.prevent="submitPrintedOutboundScanByEnter"
          />
          <button @click="submitPrintedOutboundScan">执行出库</button>
        </div>
      </section>

      <div class="print-grid">
        <article class="print-order-card">
          <header class="order-header">
            <div>
              <h4>{{ printOrder.outboundNo }}</h4>
              <p>来源入库单：{{ sourceText(printOrder) }}</p>
              <p>客户：{{ printOrder.customerName || '未绑定客户' }} | 状态：{{ printOrder.status }}</p>
            </div>
          </header>

          <div v-for="kanban in parentKanbansForOutbound(printOrder)" :key="kanban.id" class="kanban-card">
            <div class="kanban-main">
              <div class="kanban-meta">
                <strong>{{ kanban.kanbanNo }}</strong>
                <span>{{ kanban.partCode }} | {{ kanban.partName }}</span>
                <span>箱数 {{ kanban.boxCount }} / 每箱 {{ kanban.unitPerBox }}</span>
                <span>{{ kanban.warehouseName }} / {{ kanban.zoneName }}</span>
              </div>
              <div class="kanban-code">
                <QrCodeImage :text="kanban.qrContent" :size="120" />
                <p class="mono">{{ kanban.barcode }}</p>
              </div>
            </div>
            <div class="kanban-actions">
              <button class="secondary-button" @click="toggleExpanded(kanban.id)">
                {{ expandedParents[kanban.id] ? '收起子看板' : `展开子看板(${kanban.children.length})` }}
              </button>
              <button @click="simulatePrintScan(kanban)">模拟扫码出库</button>
            </div>
            <div v-if="expandedParents[kanban.id]" class="child-grid">
              <div v-for="child in kanban.children" :key="child.id" class="child-card">
                <QrCodeImage :text="child.qrContent" :size="92" />
                <div class="child-meta">
                  <strong>{{ child.kanbanNo }}</strong>
                  <span>第 {{ child.boxIndex }} 箱</span>
                  <span>数量 {{ child.qty }}</span>
                  <span>{{ formatStatus(child.status) }}</span>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>
  </WorkModePage>
</template>

<style scoped>
.detail-stack,
.print-grid,
.child-grid {
  display: grid;
  gap: 12px;
}

.detail-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.scan-assist-row {
  display: grid;
  margin-top: 12px;
  gap: 12px;
}

.two-col {
  grid-template-columns: minmax(0, 1fr) auto;
}

.print-grid {
  grid-template-columns: minmax(0, 1fr);
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

.kanban-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .detail-row,
  .kanban-main {
    grid-template-columns: 1fr;
  }
}
</style>
