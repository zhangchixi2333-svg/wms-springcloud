<!-- 本文件实现看板生命周期查询与打印，支持父子看板展开查看以及浏览器模拟扫码入库。 -->
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { formatStatus } from '../../../app/displayText'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { Kanban, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode?: 'kanban' | 'inbound-scan' }>()
const scanInputRef = ref<HTMLInputElement | null>(null)

const scanForm = reactive({
  barcode: '',
  locationCode: '',
})

const scanMatchHint = ref('')
const expandedParents = reactive<Record<number, boolean>>({})

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
const workModes = computed(() => [
  { key: 'query', label: '查看看板' },
  { key: 'print', label: '打印看板', disabled: !selectedKanban.value },
])

const statusOptions = [
  { value: '', label: '????' },
  { value: 'CREATED', label: formatStatus('CREATED') },
  { value: 'WAIT_SCAN', label: formatStatus('WAIT_SCAN') },
  { value: 'PARTIAL', label: formatStatus('PARTIAL') },
  { value: 'INBOUND', label: formatStatus('INBOUND') },
  { value: 'OUTBOUND', label: formatStatus('OUTBOUND') },
  { value: 'FROZEN', label: formatStatus('FROZEN') },
  { value: 'REPACK_OUTBOUND', label: formatStatus('REPACK_OUTBOUND') },
  { value: 'REPACK_INBOUND', label: formatStatus('REPACK_INBOUND') },
  { value: 'RETURNED', label: formatStatus('RETURNED') },
  { value: 'VOID', label: formatStatus('VOID') },
]
const filteredRows = computed(() =>
  props.model.state.kanbans
    .filter((item) => item.parentKanban)
    .filter((item) => {
      const statusMatch = !filters.status || item.status === filters.status
      const inboundMatch = !filters.inboundNo || item.inboundNo.toLowerCase().includes(filters.inboundNo.toLowerCase())
      const outboundMatch = !filters.outboundNo || item.outboundNo.toLowerCase().includes(filters.outboundNo.toLowerCase())
      const kanbanMatch = !filters.kanbanNo || item.kanbanNo.toLowerCase().includes(filters.kanbanNo.toLowerCase())
      const supplierMatch = !filters.supplierId || item.supplierId === filters.supplierId
      const partMatch = !filters.partCode || item.partCode.toLowerCase().includes(filters.partCode.toLowerCase())
      return statusMatch && inboundMatch && outboundMatch && kanbanMatch && supplierMatch && partMatch
    }),
)

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
    scanMatchHint.value = `已识别看板 ${kanban.kanbanNo}，但未找到规划库位`
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

async function submitScan() {
  if (!scanForm.locationCode) {
    scanForm.locationCode = findPlannedLocationCode(scanForm.barcode)
  }
  if (!scanForm.locationCode) {
    await focusScanInput()
    return
  }
  await props.model.actions.scanInbound(scanForm)
  scanForm.barcode = ''
  scanForm.locationCode = ''
  scanMatchHint.value = '入库成功，父看板会联动子看板状态。'
  await focusScanInput()
}

async function submitScanByEnter() {
  if (!scanForm.barcode) return
  await submitScan()
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

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function toggleExpanded(kanbanId: number) {
  expandedParents[kanbanId] = !expandedParents[kanbanId]
}

watch(
  () => props.mode,
  async (value) => {
    if (value === 'inbound-scan') {
      await focusScanInput()
    }
  },
  { immediate: true },
)

watch(
  () => scanForm.barcode,
  (value) => {
    if (!value.trim()) {
      scanForm.locationCode = ''
      scanMatchHint.value = ''
      return
    }
    scanForm.locationCode = findPlannedLocationCode(value)
  },
)
</script>

<template>
  <section class="stack">
    <section v-if="mode === 'inbound-scan'" class="panel">
      <div class="section-head">
        <div>
          <h3>扫码入库</h3>
          <p>浏览器里可直接录入二维码内容或条码回车模拟扫码枪，父看板入库时会自动带上全部子箱。</p>
        </div>
      </div>
      <div class="scan-action-layout">
        <div class="form-grid three">
          <input
            ref="scanInputRef"
            v-model="scanForm.barcode"
            placeholder="扫描枪输入二维码内容或条码"
            @keydown.enter.prevent="submitScanByEnter"
          />
          <select v-model="scanForm.locationCode">
            <option value="">自动匹配目标库位</option>
            <option v-for="location in model.state.locations" :key="location.id" :value="location.locationCode">
              {{ location.locationCode }} | {{ location.warehouseName }} / {{ location.zoneName }}
            </option>
          </select>
          <button @click="submitScan">确认入库</button>
        </div>
        <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
        <div v-if="scanForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="scanForm.barcode" :size="160" />
          <p class="mono">{{ scanForm.barcode }}</p>
        </div>
      </div>
    </section>

    <WorkModePage
      v-model="viewMode"
      :modes="workModes"
      hint="查询时按父看板展示，可展开查看每个箱级子看板；打印时同时渲染父看板和全部子看板。"
    >
      <section v-if="viewMode === 'query'" class="stack">
        <section class="panel">
          <div class="section-head">
            <div>
              <h3>看板筛选</h3>
              <p>按状态、入库单、出库单、看板号、供应商与零件号追踪父子看板生命周期。</p>
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
                <th>状态</th>
                <th>总数量</th>
                <th>箱数</th>
                <th>仓库 / 库区</th>
                <th>器具</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in filteredRows" :key="item.id">
                <tr>
                  <td>{{ item.kanbanNo }}</td>
                  <td>{{ item.inboundNo }}</td>
                  <td>{{ item.outboundNo || '-' }}</td>
                  <td>{{ item.partCode }}</td>
                  <td>{{ item.supplierName }}</td>
                  <td>{{ formatStatus(item.status) }}</td>
                  <td>{{ item.qty }}</td>
                  <td>{{ item.boxCount }}</td>
                  <td>{{ item.warehouseName }} / {{ item.zoneName }}</td>
                  <td>{{ item.equipmentModel || item.equipmentCode || '-' }}</td>
                  <td>{{ formatTime(item.inboundTime || item.createdAt) }}</td>
                  <td class="action-row">
                    <button class="secondary-button" @click="toggleExpanded(item.id)">
                      {{ expandedParents[item.id] ? '收起' : `展开(${item.children.length})` }}
                    </button>
                    <button class="secondary-button" @click="openPrint(item)">打印</button>
                  </td>
                </tr>
                <tr v-if="expandedParents[item.id]">
                  <td colspan="12">
                    <div class="child-grid">
                      <div v-for="child in item.children" :key="child.id" class="child-row">
                        <strong>{{ child.kanbanNo }}</strong>
                        <span>第 {{ child.boxIndex }} 箱</span>
                        <span>数量 {{ child.qty }}</span>
                        <span>状态 {{ formatStatus(child.status) }}</span>
                        <span>条码 {{ child.barcode }}</span>
                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </section>
      </section>

      <section v-else-if="selectedKanban" class="panel">
        <div class="section-head">
          <div>
            <h3>看板打印</h3>
            <p>打印页同时展示父看板详情、二维码和所有箱级子看板。</p>
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
                <tr><th>总数量</th><td>{{ selectedKanban.qty }}</td></tr>
                <tr><th>箱数</th><td>{{ selectedKanban.boxCount }}</td></tr>
                <tr><th>每箱数量</th><td>{{ selectedKanban.unitPerBox }}</td></tr>
                <tr><th>是否转包</th><td>{{ selectedKanban.pendingRepack ? '是' : '否' }}</td></tr>
                <tr><th>器具编码</th><td>{{ selectedKanban.equipmentCode || '-' }}</td></tr>
                <tr><th>器具型号</th><td>{{ selectedKanban.equipmentModel || '-' }}</td></tr>
                <tr><th>仓库 / 库区</th><td>{{ selectedKanban.warehouseName }} / {{ selectedKanban.zoneName }}</td></tr>
                <tr><th>状态</th><td>{{ formatStatus(selectedKanban.status) }}</td></tr>
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

        <div class="child-grid print-child-grid">
          <div v-for="child in selectedKanban.children" :key="child.id" class="child-card">
            <QrCodeImage :text="child.qrContent" :size="108" />
            <strong>{{ child.kanbanNo }}</strong>
            <span>第 {{ child.boxIndex }} 箱</span>
            <span>数量 {{ child.qty }}</span>
            <span>状态 {{ formatStatus(child.status) }}</span>
            <span class="mono">{{ child.barcode }}</span>
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

.scan-hint {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.kanban-print {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) 280px;
  gap: 24px;
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

.child-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  align-items: start;
}

.child-row,
.child-card {
  display: grid;
  gap: 4px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
}

.child-row {
  min-height: 116px;
  align-content: start;
}

.print-child-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .kanban-print {
    grid-template-columns: 1fr;
  }
}
</style>
