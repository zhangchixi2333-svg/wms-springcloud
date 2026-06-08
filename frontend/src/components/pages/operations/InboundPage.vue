<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { equipmentCodeOptions, kanbanScanOptions, warehouseZoneOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { InboundDraftItem, InboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const mode = ref<'query' | 'edit' | 'print' | 'scan'>('query')
const printOrder = ref<InboundOrder | null>(null)

const filters = reactive({
  status: '',
  supplierId: 0,
  inboundNo: '',
})

const inboundWarehouseZoneOptions = computed(() => warehouseZoneOptions(props.model.state.locations))
const inboundEquipmentOptions = computed(() => equipmentCodeOptions(props.model.state.equipment))
const allInboundScanOptions = computed(() => kanbanScanOptions(props.model.state.kanbans, ['CREATED', 'WAIT_SCAN']))
const workModes = computed(() => [
  { key: 'query', label: '查看入库' },
  { key: 'edit', label: '创建入库单' },
  { key: 'print', label: '打印入库单', disabled: !printOrder.value },
  { key: 'scan', label: '扫码入库' },
])

const form = reactive({
  supplierId: 0,
  items: [createDraftItem()],
})

const printedInboundScanForm = reactive({
  barcode: '',
  locationCode: '',
})

function createDraftItem(): InboundDraftItem {
  return {
    partId: 0,
    plannedQty: 1,
    boxCount: 1,
    pendingRepack: false,
    equipmentCode: '',
    packageCapacity: 1,
    warehouseZone: '',
  }
}

const filteredOrders = computed(() =>
  props.model.state.inboundOrders.filter((order) => {
    const statusMatch = !filters.status || order.status === filters.status
    const supplierMatch = !filters.supplierId || order.supplierId === filters.supplierId
    const orderNoMatch = !filters.inboundNo || order.inboundNo.toLowerCase().includes(filters.inboundNo.toLowerCase())
    return statusMatch && supplierMatch && orderNoMatch
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
  filters.supplierId = 0
  filters.inboundNo = ''
}

function openCreate() {
  form.supplierId = props.model.state.suppliers[0]?.id ?? 0
  form.items = [createDraftItem()]
  mode.value = 'edit'
}

function handleModeSelect(nextMode: string) {
  if (nextMode === 'edit') {
    openCreate()
  }
}

function openPrint(order: InboundOrder) {
  printOrder.value = order
  mode.value = 'print'
}

async function submit() {
  await props.model.actions.createInboundOrder({
    supplierId: form.supplierId,
    items: form.items.filter((item) => item.partId > 0 && item.plannedQty > 0),
  })
  mode.value = 'query'
}

function browserPrint() {
  window.print()
}

function kanbansForInbound(order: InboundOrder) {
  return props.model.state.kanbans.filter((item) => item.inboundNo === order.inboundNo)
}

function inboundScanOptionsForOrder(order: InboundOrder) {
  return kanbanScanOptions(
    kanbansForInbound(order).filter((item) => ['CREATED', 'WAIT_SCAN'].includes(item.status)),
  )
}

async function submitPrintedInboundScan() {
  await props.model.actions.scanInbound(printedInboundScanForm)
  printedInboundScanForm.barcode = ''
  printedInboundScanForm.locationCode = ''
}
</script>

<template>
  <WorkModePage
    v-model="mode"
    :modes="workModes"
    hint="默认先查询，创建和打印进入独立工作界面；扫码入库作为自定义操作界面挂载。"
    @select="handleModeSelect"
  >
    <section v-if="mode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>入库单查询</h3>
            <p>支持按状态、供应商、订单号筛选。</p>
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
          <input v-model="filters.inboundNo" placeholder="输入订单号筛选" />
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>供应商</th>
              <th>状态</th>
              <th>明细摘要</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in filteredOrders" :key="order.id">
              <td>{{ order.inboundNo }}</td>
              <td>{{ order.supplierName }}</td>
              <td>{{ order.status }}</td>
              <td>
                <span v-for="detail in order.items" :key="detail.id" class="inline-tag">
                  {{ detail.partCode }} / {{ detail.plannedQty }} / {{ detail.boxCount }}箱 / {{ detail.warehouseZone }}
                </span>
              </td>
              <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
              <td>
                <div class="action-row">
                  <button class="secondary-button" @click="model.actions.generateKanbans(order.id)">生成看板</button>
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
            <h3>编辑入库单</h3>
            <p>录入零件明细、箱数、待转包状态、器具、包装容量和仓库/库区。</p>
          </div>
        </div>

        <div class="form-grid two">
          <select v-model.number="form.supplierId">
            <option :value="0" disabled>选择供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <button class="secondary-button" @click="addItem">新增零件明细</button>
        </div>

        <div v-for="(item, index) in form.items" :key="index" class="detail-card">
          <div class="detail-grid">
            <select v-model.number="item.partId">
              <option :value="0" disabled>选择零件</option>
              <option v-for="part in model.state.parts" :key="part.id" :value="part.id">
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
            <button class="secondary-button" @click="removeItem(index)" :disabled="form.items.length === 1">删除</button>
          </div>
        </div>

        <div class="footer-actions">
          <button @click="submit">保存入库单</button>
          <button class="secondary-button" @click="mode = 'query'">返回查询</button>
        </div>
      </section>
    </section>

    <section v-else-if="mode === 'print'" class="stack">
      <section class="panel" v-if="printOrder">
        <div class="section-head">
          <div>
            <h3>入库单打印信息</h3>
            <p>当前为打印预览界面，先保留浏览器打印和真实打印机接口扩展位。</p>
          </div>
          <div class="action-row">
            <button @click="browserPrint">浏览器打印</button>
            <button class="secondary-button" @click="mode = 'query'">返回查询</button>
          </div>
        </div>

        <div class="print-sheet">
          <div class="print-header">
            <div>
              <h2>WMS 入库单</h2>
              <p>订单号：{{ printOrder.inboundNo }}</p>
              <p>供应商：{{ printOrder.supplierName }}</p>
            </div>
            <div>
              <p>状态：{{ printOrder.status }}</p>
              <p>创建时间：{{ new Date(printOrder.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</p>
            </div>
          </div>

          <table class="table">
            <thead>
              <tr>
                <th>零件号</th>
                <th>零件名称</th>
                <th>单位</th>
                <th>入库数量</th>
                <th>箱数</th>
                <th>待转包</th>
                <th>器具编码</th>
                <th>包装容量</th>
                <th>仓库/库区</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in printOrder.items" :key="item.id">
                <td>{{ item.partCode }}</td>
                <td>{{ item.partName }}</td>
                <td>{{ item.unit }}</td>
                <td>{{ item.plannedQty }}</td>
                <td>{{ item.boxCount }}</td>
                <td>{{ item.pendingRepack ? '是' : '否' }}</td>
                <td>{{ item.equipmentCode || '-' }}</td>
                <td>{{ item.packageCapacity }}</td>
                <td>{{ item.warehouseZone }}</td>
              </tr>
            </tbody>
          </table>

          <div v-if="kanbansForInbound(printOrder).length" class="print-qr-section">
            <h3>看板二维码明细</h3>
            <table class="table qr-table">
              <thead>
                <tr>
                  <th>二维码</th>
                  <th>看板号</th>
                  <th>条码</th>
                  <th>零件</th>
                  <th>数量</th>
                  <th>仓库/库区</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="kanban in kanbansForInbound(printOrder)" :key="kanban.id">
                  <td><QrCodeImage :text="kanban.qrContent" :size="96" /></td>
                  <td>{{ kanban.kanbanNo }}</td>
                  <td class="mono">{{ kanban.barcode }}</td>
                  <td>{{ kanban.partCode }} | {{ kanban.partName }}</td>
                  <td>{{ kanban.qty }}</td>
                  <td>{{ kanban.warehouseName }} / {{ kanban.zoneName }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="kanbansForInbound(printOrder).length" class="print-scan-panel">
            <h3>根据二维码/条码执行入库</h3>
            <div class="scan-action-layout">
              <div class="form-grid three">
                <select v-model="printedInboundScanForm.barcode">
                  <option value="">选择看板二维码 / 条码</option>
                  <option v-for="item in inboundScanOptionsForOrder(printOrder)" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </option>
                </select>
                <select v-model="printedInboundScanForm.locationCode">
                  <option value="">选择入库库位</option>
                  <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
                    {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
                  </option>
                </select>
                <button @click="submitPrintedInboundScan">确认入库</button>
              </div>
              <div v-if="printedInboundScanForm.barcode" class="scan-qr-preview">
                <QrCodeImage :text="printedInboundScanForm.barcode" :size="160" />
                <p class="mono">{{ printedInboundScanForm.barcode }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </section>

    <section v-else-if="mode === 'scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码入库</h3>
          <p>选择打印单上的看板二维码或条码，再选择目标库位执行入库。</p>
        </div>
      </div>
      <div class="scan-action-layout">
        <div class="form-grid three">
          <select v-model="printedInboundScanForm.barcode">
            <option value="">选择看板二维码 / 条码</option>
            <option v-for="item in allInboundScanOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <select v-model="printedInboundScanForm.locationCode">
            <option value="">选择入库库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitPrintedInboundScan">确认入库</button>
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

.print-sheet {
  display: grid;
  gap: 18px;
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

.print-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.print-header h2,
.print-header p {
  margin: 0 0 6px;
}

@media (max-width: 1180px) {
  .filters-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .print-header {
    flex-direction: column;
  }
}
</style>
