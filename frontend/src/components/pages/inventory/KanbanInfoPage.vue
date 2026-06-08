<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { kanbanScanOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { Kanban, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode?: 'kanban' | 'inbound-scan' }>()

const scanForm = reactive({
  barcode: '',
  locationCode: '',
})

const filters = reactive({
  status: '',
  inboundNo: '',
  outboundNo: '',
  kanbanNo: '',
  supplierId: 0,
  partCode: '',
})

const viewMode = ref<'query' | 'print'>('query')
const selectedKanban = ref<Kanban | null>(null)
const inboundKanbanOptions = computed(() => kanbanScanOptions(props.model.state.kanbans, ['CREATED', 'WAIT_SCAN']))
const workModes = computed(() => [
  { key: 'query', label: '查看看板' },
  { key: 'print', label: '打印单', disabled: !selectedKanban.value },
])

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'NOT_INBOUND', label: '未入库' },
  { value: 'WAIT_SCAN', label: '待扫描' },
  { value: 'INBOUND', label: '入库' },
  { value: 'OUTBOUND', label: '出库' },
  { value: 'FROZEN', label: '封存' },
  { value: 'REPACK_OUTBOUND', label: '转包出库' },
  { value: 'REPACK_INBOUND', label: '转包入库' },
  { value: 'RETURNED', label: '已退库' },
  { value: 'VOID', label: '已作废' },
]

const filteredRows = computed(() =>
  props.model.state.kanbans.filter((item) => {
    const statusMatch =
      !filters.status ||
      (filters.status === 'NOT_INBOUND'
        ? item.status === 'WAIT_SCAN' || (!item.inboundTime && item.status !== 'INBOUND')
        : item.status === filters.status)
    const inboundMatch = !filters.inboundNo || item.inboundNo.toLowerCase().includes(filters.inboundNo.toLowerCase())
    const outboundMatch = !filters.outboundNo || item.outboundNo.toLowerCase().includes(filters.outboundNo.toLowerCase())
    const kanbanMatch = !filters.kanbanNo || item.kanbanNo.toLowerCase().includes(filters.kanbanNo.toLowerCase())
    const supplierMatch = !filters.supplierId || item.supplierId === filters.supplierId
    const partMatch = !filters.partCode || item.partCode.toLowerCase().includes(filters.partCode.toLowerCase())
    return statusMatch && inboundMatch && outboundMatch && kanbanMatch && supplierMatch && partMatch
  }),
)

async function submitScan() {
  await props.model.actions.scanInbound(scanForm)
  scanForm.barcode = ''
}

function openPrint(item: Kanban) {
  selectedKanban.value = item
  viewMode.value = 'print'
}

function resetFilters() {
  filters.status = ''
  filters.inboundNo = ''
  filters.outboundNo = ''
  filters.kanbanNo = ''
  filters.supplierId = 0
  filters.partCode = ''
}

function browserPrint() {
  window.print()
}

function showStatus(status: string) {
  return (
    {
      CREATED: '未入库',
      WAIT_SCAN: '待扫描',
      INBOUND: '入库',
      OUTBOUND: '出库',
      FROZEN: '封存',
      REPACK_OUTBOUND: '转包出库',
      REPACK_INBOUND: '转包入库',
      RETURNED: '已退库',
      VOID: '已作废',
    }[status] ?? status
  )
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
</script>

<template>
  <section class="stack">
    <section v-if="mode === 'inbound-scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码入库</h3>
          <p>扫描或输入条码后写入目标库位。</p>
        </div>
      </div>
      <div class="scan-action-layout">
        <div class="form-grid three">
          <select v-model="scanForm.barcode">
            <option value="">选择看板条码 / 二维码</option>
            <option v-for="item in inboundKanbanOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <select v-model="scanForm.locationCode">
            <option value="">选择目标库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitScan">确认入库</button>
        </div>
        <div v-if="scanForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="scanForm.barcode" :size="160" />
          <p class="mono">{{ scanForm.barcode }}</p>
        </div>
      </div>
    </section>

    <WorkModePage
      v-model="viewMode"
      :modes="workModes"
      hint="二维码内容与条码保持一一对应，后续接手持终端和打印机时不用重做编码规则。"
    >
      <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>看板筛选</h3>
            <p>按状态、入库单、出库单、看板号、供应商和零件号追踪每一张条码的过程状态。</p>
          </div>
        </div>
        <div class="form-grid six">
          <select v-model="filters.status">
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <input v-model="filters.inboundNo" placeholder="入库单号" />
          <input v-model="filters.outboundNo" placeholder="出库单号" />
          <input v-model="filters.kanbanNo" placeholder="看板号" />
          <select v-model.number="filters.supplierId">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <input v-model="filters.partCode" placeholder="零件号" />
        </div>
        <div class="filters-actions">
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>看板号</th>
              <th>入库单号</th>
              <th>出库单号</th>
              <th>零件号</th>
              <th>供应商</th>
              <th>看板状态</th>
              <th>数量</th>
              <th>仓库</th>
              <th>库区</th>
              <th>器具型号</th>
              <th>是否转包</th>
              <th>创建/入库时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredRows" :key="item.id">
              <td>{{ item.kanbanNo }}</td>
              <td>{{ item.inboundNo }}</td>
              <td>{{ item.outboundNo || '-' }}</td>
              <td>{{ item.partCode }}</td>
              <td>{{ item.supplierName }}</td>
              <td>{{ showStatus(item.status) }}</td>
              <td>{{ item.qty }}</td>
              <td>{{ item.warehouseName }}</td>
              <td>{{ item.zoneName }}</td>
              <td>{{ item.equipmentModel || item.equipmentCode || '-' }}</td>
              <td>{{ item.pendingRepack ? '是' : '否' }}</td>
              <td>{{ formatTime(item.inboundTime || item.createdAt) }}</td>
              <td>
                <button class="secondary-button" @click="openPrint(item)">打印</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
      </section>

      <section v-else-if="selectedKanban" class="panel">
      <div class="section-head">
        <div>
          <h3>看板打印单</h3>
          <p>先保留浏览器打印入口，后面可以直接挂真实打印机接口。</p>
        </div>
        <div class="action-row">
          <button @click="browserPrint">浏览器打印</button>
          <button class="secondary-button" @click="viewMode = 'query'">返回列表</button>
        </div>
      </div>

      <div class="kanban-print">
        <div class="print-left">
          <h2>{{ selectedKanban.kanbanNo }}</h2>
          <table class="table print-detail-table">
            <tbody>
              <tr><th>条码</th><td class="mono">{{ selectedKanban.barcode }}</td></tr>
              <tr><th>入库单号</th><td>{{ selectedKanban.inboundNo }}</td></tr>
              <tr><th>出库单号</th><td>{{ selectedKanban.outboundNo || '-' }}</td></tr>
              <tr><th>供应商</th><td>{{ selectedKanban.supplierName }}</td></tr>
              <tr><th>零件号</th><td>{{ selectedKanban.partCode }}</td></tr>
              <tr><th>零件名称</th><td>{{ selectedKanban.partName }}</td></tr>
              <tr><th>单位</th><td>{{ selectedKanban.unit }}</td></tr>
              <tr><th>批次</th><td>{{ selectedKanban.batchNo }}</td></tr>
              <tr><th>数量</th><td>{{ selectedKanban.qty }}</td></tr>
              <tr><th>箱数</th><td>{{ selectedKanban.boxCount }}</td></tr>
              <tr><th>是否转包</th><td>{{ selectedKanban.pendingRepack ? '是' : '否' }}</td></tr>
              <tr><th>器具编码</th><td>{{ selectedKanban.equipmentCode || '-' }}</td></tr>
              <tr><th>器具型号</th><td>{{ selectedKanban.equipmentModel || '-' }}</td></tr>
              <tr><th>包装容量</th><td>{{ selectedKanban.packageCapacity }}</td></tr>
              <tr><th>仓库 / 库区</th><td>{{ selectedKanban.warehouseName }} / {{ selectedKanban.zoneName }}</td></tr>
              <tr><th>状态</th><td>{{ showStatus(selectedKanban.status) }}</td></tr>
              <tr><th>库位</th><td>{{ selectedKanban.locationCode }}</td></tr>
              <tr><th>创建时间</th><td>{{ formatTime(selectedKanban.createdAt) }}</td></tr>
              <tr><th>入库时间</th><td>{{ formatTime(selectedKanban.inboundTime) }}</td></tr>
              <tr><th>出库时间</th><td>{{ formatTime(selectedKanban.outboundTime) }}</td></tr>
            </tbody>
          </table>
        </div>
        <div class="print-right">
          <QrCodeImage :text="selectedKanban.qrContent" :size="220" />
          <p class="mono qr-text">{{ selectedKanban.qrContent }}</p>
        </div>
      </div>
      </section>
    </WorkModePage>
  </section>
</template>

<style scoped>
.filters-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.kanban-print {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) 280px;
  gap: 24px;
}

.print-left h2,
.print-left p {
  margin: 0 0 8px;
}

.print-detail-table th {
  width: 120px;
  white-space: nowrap;
}

.print-right {
  display: grid;
  justify-items: center;
  align-content: start;
  gap: 12px;
}

.qr-text {
  word-break: break-all;
  text-align: center;
}

@media (max-width: 1100px) {
  .kanban-print {
    grid-template-columns: 1fr;
  }
}
</style>
