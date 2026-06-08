<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { kanbanScanOptions, warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { OutboundDraftItem, OutboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const viewMode = ref<'query' | 'create' | 'manual' | 'print' | 'scan'>('query')
const printOrder = ref<OutboundOrder | null>(null)

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
const outboundOrderOptions = computed(() =>
  props.model.state.outboundOrders.filter((order) => order.status !== 'COMPLETED'),
)
const outboundKanbanOptions = computed(() => (activeScanOrder.value ? outboundScanOptionsForOrder(activeScanOrder.value) : []))
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

async function submitScan() {
  await props.model.actions.scanOutbound(scanForm)
  scanForm.barcode = ''
}

async function submitCreate() {
  await props.model.actions.createOutboundOrder({
    customerId: form.customerId || null,
    inboundOrderNos: form.inboundOrderNos,
    items: form.items.filter((item) => item.partId > 0 && item.plannedQty > 0),
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

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function browserPrint() {
  window.print()
}

function sourceText(order: OutboundOrder) {
  return order.inboundOrderNos.length ? order.inboundOrderNos.join('、') : '-'
}

function inboundOrderLabel(inboundNo: string) {
  const order = props.model.state.inboundOrders.find((item) => item.inboundNo === inboundNo)
  return order ? `${order.inboundNo} | ${order.supplierName} | ${order.status}` : inboundNo
}

function handleScanOrderChange() {
  scanForm.barcode = ''
}

function kanbansForOutbound(order: OutboundOrder) {
  return props.model.state.kanbans.filter((item) => item.outboundNo === order.outboundNo)
}

function outboundScanOptionsForOrder(order: OutboundOrder) {
  const partCodes = new Set(order.items.map((item) => item.partCode))
  const inboundNos = new Set(order.inboundOrderNos)
  return kanbanScanOptions(
    props.model.state.kanbans.filter((item) => item.status === 'INBOUND' && partCodes.has(item.partCode) && inboundNos.has(item.inboundNo)),
  )
}

async function submitPrintedOutboundScan() {
  if (!printOrder.value) return
  await props.model.actions.scanOutbound({
    barcode: printedOutboundScanForm.barcode,
    outboundOrderNo: printOrder.value.outboundNo,
  })
  printedOutboundScanForm.barcode = ''
}
</script>

<template>
  <WorkModePage
    v-model="viewMode"
    :modes="workModes"
    hint="默认先查询；创建、打印、扫码出库和手工入账作为独立工作界面。"
    @select="handleModeSelect"
  >
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>出库筛选</h3>
          <p>先按条件找到出库单，再执行扫码出库。</p>
          </div>
        </div>
        <div class="form-grid four">
          <select v-model="filters.status">
            <option value="">全部状态</option>
            <option value="CREATED">CREATED</option>
            <option value="PARTIAL">PARTIAL</option>
            <option value="COMPLETED">COMPLETED</option>
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
              <td>{{ order.status }}</td>
              <td>
                <span v-for="detail in order.items" :key="detail.id" class="inline-tag">
                  {{ detail.partCode }} / {{ detail.plannedQty }} / 已扫 {{ detail.scannedQty }} / {{ detail.warehouseName }}-{{ detail.zoneName }}
                </span>
              </td>
              <td>{{ formatTime(order.createdAt) }}</td>
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
          <p>先选择唯一出库单，再扫描该出库单绑定入库来源下的看板。</p>
        </div>
      </div>
      <div class="scan-action-layout">
        <div class="form-grid three">
          <select v-model="scanForm.outboundOrderNo" @change="handleScanOrderChange">
            <option value="">选择出库单</option>
            <option v-for="order in outboundOrderOptions" :key="order.id" :value="order.outboundNo">
              {{ order.outboundNo }} | 来源 {{ sourceText(order) }} | {{ order.status }}
            </option>
          </select>
          <select v-model="scanForm.barcode" :disabled="!scanForm.outboundOrderNo">
            <option value="">选择看板条码 / 二维码</option>
            <option v-for="item in outboundKanbanOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <button :disabled="!scanForm.outboundOrderNo || !scanForm.barcode" @click="submitScan">确认出库</button>
        </div>
        <div v-if="scanForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="scanForm.barcode" :size="160" />
          <p class="mono">{{ scanForm.barcode }}</p>
        </div>
      </div>
    </section>

    <section v-else-if="viewMode === 'create'" class="panel">
      <div class="section-head">
        <div>
          <h3>创建出库单</h3>
          <p>录入出库零件、计划数量和目标仓库库区，后续扫码时会自动回写执行进度。</p>
        </div>
      </div>

      <div class="form-grid two">
        <select v-model.number="form.customerId">
          <option :value="0">不绑定客户</option>
          <option v-for="item in model.state.customers" :key="item.id" :value="item.id">
            {{ item.customerCode }} | {{ item.customerName }}
          </option>
        </select>
        <select v-model="form.inboundOrderNos" multiple size="4">
          <option v-for="order in model.state.inboundOrders" :key="order.id" :value="order.inboundNo">
            {{ order.inboundNo }} | {{ order.supplierName }} | {{ order.status }}
          </option>
        </select>
      </div>
      <div class="footer-actions compact">
        <span class="muted">已绑定来源：{{ form.inboundOrderNos.length ? form.inboundOrderNos.join('、') : '未选择' }}</span>
        <button class="secondary-button" @click="addItem">新增零件明细</button>
      </div>

      <div v-for="(item, index) in form.items" :key="index" class="detail-card">
        <div class="detail-grid">
          <select v-model.number="item.partId">
            <option :value="0" disabled>选择零件</option>
            <option v-for="part in model.state.parts" :key="part.id" :value="part.id">
              {{ part.partCode }} | {{ part.partName }}
            </option>
          </select>
          <input v-model.number="item.plannedQty" type="number" min="1" placeholder="计划数量" />
          <select v-model="item.warehouseName" @change="item.zoneName = ''">
            <option value="">选择仓库</option>
            <option v-for="warehouse in outboundWarehouseOptions" :key="warehouse" :value="warehouse">
              {{ warehouse }}
            </option>
          </select>
          <select v-model="item.zoneName">
            <option value="">选择库区</option>
            <option v-for="zone in outboundZoneOptions(item.warehouseName)" :key="zone" :value="zone">
              {{ zone }}
            </option>
          </select>
          <button class="secondary-button" @click="removeItem(index)" :disabled="form.items.length === 1">删除</button>
        </div>
      </div>

      <div class="footer-actions">
        <button @click="submitCreate">保存出库单</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>

    <section v-else-if="viewMode === 'print' && printOrder" class="panel">
      <div class="section-head">
        <div>
          <h3>出库单打印信息</h3>
          <p>打印出库单明细，并渲染已关联看板的二维码。</p>
        </div>
        <div class="action-row">
          <button @click="browserPrint">浏览器打印</button>
          <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
        </div>
      </div>

      <div class="print-sheet">
        <div class="print-header">
          <div>
            <h2>WMS 出库单</h2>
            <p>出库单号：{{ printOrder.outboundNo }}</p>
            <p>客户：{{ printOrder.customerName }}</p>
            <p>来源入库单：{{ sourceText(printOrder) }}</p>
          </div>
          <div>
            <p>状态：{{ printOrder.status }}</p>
            <p>创建时间：{{ formatTime(printOrder.createdAt) }}</p>
          </div>
        </div>

        <table class="table">
          <thead>
            <tr>
              <th>零件号</th>
              <th>零件名称</th>
              <th>单位</th>
              <th>计划数量</th>
              <th>已扫数量</th>
              <th>仓库</th>
              <th>库区</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in printOrder.items" :key="item.id">
              <td>{{ item.partCode }}</td>
              <td>{{ item.partName }}</td>
              <td>{{ item.unit }}</td>
              <td>{{ item.plannedQty }}</td>
              <td>{{ item.scannedQty }}</td>
              <td>{{ item.warehouseName }}</td>
              <td>{{ item.zoneName }}</td>
            </tr>
          </tbody>
        </table>

        <div class="print-qr-section">
          <h3>来源入库单</h3>
          <div class="tag-row">
            <span v-for="inboundNo in printOrder.inboundOrderNos" :key="inboundNo" class="inline-tag">
              {{ inboundOrderLabel(inboundNo) }}
            </span>
            <span v-if="!printOrder.inboundOrderNos.length" class="inline-tag">未绑定</span>
          </div>
        </div>

        <div v-if="kanbansForOutbound(printOrder).length" class="print-qr-section">
          <h3>看板二维码明细</h3>
          <table class="table qr-table">
            <thead>
              <tr>
                <th>二维码</th>
                <th>看板号</th>
                <th>条码</th>
                <th>零件</th>
                <th>数量</th>
                <th>库位</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="kanban in kanbansForOutbound(printOrder)" :key="kanban.id">
                <td><QrCodeImage :text="kanban.qrContent" :size="96" /></td>
                <td>{{ kanban.kanbanNo }}</td>
                <td class="mono">{{ kanban.barcode }}</td>
                <td>{{ kanban.partCode }} | {{ kanban.partName }}</td>
                <td>{{ kanban.qty }}</td>
                <td>{{ kanban.locationCode }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="print-scan-panel">
          <h3>根据二维码/条码执行出库</h3>
          <div class="scan-action-layout">
            <div class="form-grid two">
              <select v-model="printedOutboundScanForm.barcode">
                <option value="">选择看板二维码 / 条码</option>
                <option v-for="item in outboundScanOptionsForOrder(printOrder)" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <button @click="submitPrintedOutboundScan">确认出库</button>
            </div>
            <div v-if="printedOutboundScanForm.barcode" class="scan-qr-preview">
              <QrCodeImage :text="printedOutboundScanForm.barcode" :size="160" />
              <p class="mono">{{ printedOutboundScanForm.barcode }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="viewMode === 'manual'" class="panel">
      <div class="section-head">
        <div>
          <h3>手工入账</h3>
          <p>用于补录库存，不绑定看板条码，但会写入库存流水。</p>
        </div>
      </div>
      <div class="form-grid four">
        <select v-model.number="manualForm.partId">
          <option :value="0" disabled>选择零件</option>
          <option v-for="part in model.state.parts" :key="part.id" :value="part.id">
            {{ part.partCode }} | {{ part.partName }}
          </option>
        </select>
        <select v-model.number="manualForm.locationId">
          <option :value="0" disabled>选择库位</option>
          <option v-for="location in model.state.locations" :key="location.id" :value="location.id">
            {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
          </option>
        </select>
        <input v-model.number="manualForm.qty" type="number" min="0.001" step="0.001" placeholder="数量" />
        <input v-model="manualForm.remark" placeholder="备注" />
      </div>
      <div class="footer-actions">
        <button @click="submitManual">确认入账</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>
  </WorkModePage>
</template>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.print-sheet {
  display: grid;
  gap: 18px;
}

.print-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.print-header h2,
.print-header p {
  margin: 0 0 6px;
}

.print-qr-section {
  display: grid;
  gap: 12px;
}

.print-qr-section h3 {
  margin: 0;
}

.qr-table :deep(.qr-wrap) {
  padding: 6px;
}

@media (max-width: 1180px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
