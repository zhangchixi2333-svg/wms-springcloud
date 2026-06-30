<!-- 本文件实现箱级看板生命周期查询、扫码入库和看板打印。 -->
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatStatus, formatWarehouseType } from '../../../app/displayText'
import { findKanbanByScanCode, findLocationForKanban, formatDateTime, splitBusinessNos } from '../../../app/kanbanHelpers'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import KanbanDetailModal from './modals/KanbanDetailModal.vue'
import KanbanTraceModal from './modals/KanbanTraceModal.vue'
import type { Kanban, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode?: 'kanban' | 'inbound-scan' }>()
const scanInputRef = ref<HTMLInputElement | null>(null)

const scanForm = reactive({
  barcode: '',
  locationCode: '',
})

const scanMatchHint = ref('')
const rows = ref<Kanban[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')

const filters = reactive({
  status: '',
  inboundNo: '',
  outboundNo: '',
  kanbanNo: '',
  supplierId: 0,
  partCode: '',
  warehouseName: '',
  zoneName: '',
  warehouseType: '' as '' | 'OWN' | 'THIRD_PARTY',
})

const viewMode = ref<'query' | 'print'>('query')
const selectedKanban = ref<Kanban | null>(null)
const detailKanban = ref<Kanban | null>(null)
const traceKanban = ref<Kanban | null>(null)

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'WAIT_SCAN', label: formatStatus('WAIT_SCAN') },
  { value: 'INBOUND', label: formatStatus('INBOUND') },
  { value: 'ALLOCATED', label: formatStatus('ALLOCATED') },
  { value: 'PARTIAL_OUTBOUND', label: formatStatus('PARTIAL_OUTBOUND') },
  { value: 'FROZEN', label: formatStatus('FROZEN') },
  { value: 'THIRD_PARTY_STOCK', label: formatStatus('THIRD_PARTY_STOCK') },
  { value: 'OUTBOUND', label: formatStatus('OUTBOUND') },
  { value: 'RETURNED', label: formatStatus('RETURNED') },
]

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

async function loadKanbans(nextPage = page.value) {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listKanbansPage({
      status: filters.status,
      inboundNo: filters.inboundNo.trim(),
      outboundNo: filters.outboundNo.trim(),
      kanbanNo: filters.kanbanNo.trim(),
      supplierId: filters.supplierId,
      partCode: filters.partCode.trim(),
      warehouseName: filters.warehouseName.trim(),
      zoneName: filters.zoneName.trim(),
      warehouseType: filters.warehouseType,
      page: nextPage,
      size: pageSize.value,
    })
    rows.value = result.records
    page.value = result.page
    total.value = result.total
    mergeKanbanCache(result.records)
    if (!result.records.length && result.total > 0 && result.page > 1) {
      await loadKanbans(Math.max(1, result.totalPages))
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '看板查询失败'
  } finally {
    loading.value = false
  }
}

async function searchKanbans() {
  await goFirstAndLoad()
}

async function goFirstAndLoad() {
  if (page.value === 1) {
    await loadKanbans(1)
    return
  }
  page.value = 1
}

async function ensureKanbanForScan(scanCode: string) {
  const cached = findKanbanByScanCode(props.model.state.kanbans, scanCode)
  if (cached) return cached
  const result = await api.listKanbansPage({
    kanbanNo: scanCode.trim(),
    includeChildren: true,
    page: 1,
    size: 1,
  })
  mergeKanbanCache(result.records)
  return findKanbanByScanCode(result.records, scanCode)
}

const summary = computed(() => ({
  pageTotal: rows.value.length,
  total: total.value,
  inbound: rows.value.filter((item) => item.status === 'INBOUND').length,
  allocated: rows.value.filter((item) => item.status === 'ALLOCATED').length,
  partialOutbound: rows.value.filter((item) => item.status === 'PARTIAL_OUTBOUND').length,
  outbound: rows.value.filter((item) => item.status === 'OUTBOUND').length,
}))

async function findPlannedLocationCode(scanCode: string) {
  const kanban = await ensureKanbanForScan(scanCode)
  if (!kanban) {
    scanMatchHint.value = '未识别到对应看板'
    return ''
  }
  const location = findLocationForKanban(props.model.state.locations, kanban)
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
    scanForm.locationCode = await findPlannedLocationCode(scanForm.barcode)
  }
  if (!scanForm.locationCode) {
    await focusScanInput()
    return
  }
  await props.model.actions.scanInbound(scanForm)
  scanForm.barcode = ''
  scanForm.locationCode = ''
  scanMatchHint.value = '箱看板入库成功。'
  await loadKanbans(page.value)
  await focusScanInput()
}

async function submitScanByEnter() {
  if (!scanForm.barcode) return
  await submitScan()
}

function selectKanban(item: Kanban) {
  selectedKanban.value = item
}

function openDetail(item: Kanban) {
  detailKanban.value = item
}

function closeDetail() {
  detailKanban.value = null
}

function openTrace(item: Kanban) {
  traceKanban.value = item
}

function closeTrace() {
  traceKanban.value = null
}

function openPrint(item: Kanban) {
  selectedKanban.value = item
  viewMode.value = 'print'
}

function closePrintModal() {
  viewMode.value = 'query'
}

function resetFilters() {
  filters.status = ''
  filters.inboundNo = ''
  filters.outboundNo = ''
  filters.kanbanNo = ''
  filters.supplierId = 0
  filters.partCode = ''
  filters.warehouseName = ''
  filters.zoneName = ''
  filters.warehouseType = ''
  void goFirstAndLoad()
}

function browserPrint() {
  document.body.classList.add('printing-operation-modal')
  window.addEventListener('afterprint', () => document.body.classList.remove('printing-operation-modal'), { once: true })
  window.print()
  window.setTimeout(() => document.body.classList.remove('printing-operation-modal'), 800)
}

function formatTime(value: string | null) {
  return formatDateTime(value)
}

function outboundNoList(value: string | null | undefined) {
  return splitBusinessNos(value)
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
  async (value) => {
    if (!value.trim()) {
      scanForm.locationCode = ''
      scanMatchHint.value = ''
      return
    }
    scanForm.locationCode = await findPlannedLocationCode(value)
  },
)

watch(page, async (value) => {
  await loadKanbans(value)
})

watch(pageSize, async () => {
  await goFirstAndLoad()
})

function handleBusinessChanged() {
  void loadKanbans(page.value)
}

onMounted(async () => {
  window.addEventListener('wms-business-changed', handleBusinessChanged)
  await loadKanbans(1)
})

onBeforeUnmount(() => {
  window.removeEventListener('wms-business-changed', handleBusinessChanged)
})
</script>

<template>
  <section class="stack kanban-info-page">
    <section v-if="mode === 'inbound-scan'" class="panel scan-panel">
      <div class="section-head compact-head">
        <div>
          <h3>扫码入库</h3>
        </div>
        <div class="action-row">
          <button @click="submitScan">确认入库</button>
        </div>
      </div>
      <div class="scan-action-layout">
        <div class="scan-main">
          <div class="scan-control-row">
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
          </div>
          <p v-if="scanMatchHint" class="scan-hint">{{ scanMatchHint }}</p>
        </div>
        <div v-if="scanForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="scanForm.barcode" :size="112" />
          <p class="mono">{{ scanForm.barcode }}</p>
        </div>
      </div>
    </section>

    <section class="panel kanban-filter-panel">
      <div class="kanban-filter-line">
        <h3>看板信息</h3>
        <div class="kanban-filter-row">
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
          <input v-model="filters.warehouseName" placeholder="仓库" />
          <input v-model="filters.zoneName" placeholder="库区" />
          <select v-model="filters.warehouseType">
            <option value="">全部性质</option>
            <option value="OWN">{{ formatWarehouseType('OWN') }}</option>
            <option value="THIRD_PARTY">{{ formatWarehouseType('THIRD_PARTY') }}</option>
          </select>
          <button class="secondary-button compact-filter-button" :disabled="loading" @click="searchKanbans">查询</button>
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row kanban-filter-actions">
          <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="total" />
        </div>
      </div>
    </section>

    <section class="panel kanban-table-panel">
      <div class="table-toolbar">
        <span>{{ loading ? '正在查询看板...' : `共 ${summary.total} 个，本页 ${summary.pageTotal} 个，已入 ${summary.inbound}，已锁定 ${summary.allocated}，部分出库 ${summary.partialOutbound}，已出 ${summary.outbound}` }}</span>
        <span v-if="errorMessage" class="form-error">{{ errorMessage }}</span>
      </div>
      <div class="table-scroll aligned-table-shell">
        <table class="table kanban-table">
          <thead>
            <tr>
              <th>看板号</th>
              <th>入库单号</th>
              <th>出库单号</th>
              <th>零件号</th>
              <th>零件名称</th>
              <th>供应商</th>
              <th>状态</th>
              <th>数量</th>
              <th>可用 / 已出 / 锁定</th>
              <th>转包锁定</th>
              <th>仓库 / 库区 / 性质</th>
              <th>迁移单</th>
              <th>器具</th>
              <th>时间</th>
              <th class="action-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in rows"
              :key="item.id"
              class="kanban-row"
              :class="{ selected: selectedKanban?.id === item.id, 'tone-a': Math.floor(index / 2) % 2 === 0, 'tone-b': Math.floor(index / 2) % 2 === 1 }"
              @click="selectKanban(item)"
            >
              <td class="mono">{{ item.kanbanNo }}</td>
              <td class="mono">{{ item.inboundNo }}</td>
              <td class="business-no-cell" :title="outboundNoList(item.outboundNo).join('，') || '-'">
                <span v-if="outboundNoList(item.outboundNo).length" class="cell-ellipsis">
                  {{ outboundNoList(item.outboundNo).join('，') }}
                </span>
                <span v-else>-</span>
              </td>
              <td class="mono">{{ item.partCode }}</td>
              <td>{{ item.partName }}</td>
              <td>{{ item.supplierName }}</td>
              <td><span class="status-badge">{{ formatStatus(item.status) }}</span></td>
              <td>{{ item.qty }}</td>
              <td>{{ item.availableQty }} / {{ item.outboundQty }} / {{ item.reservedQty }}</td>
              <td>{{ item.reservedTransferQty }}</td>
              <td>{{ item.warehouseName }} / {{ item.zoneName }} / {{ formatWarehouseType(item.warehouseType) }}</td>
              <td class="mono transfer-no-cell" :title="item.transferOrderNo || '-'">
                <span class="cell-ellipsis">{{ item.transferOrderNo || '-' }}</span>
              </td>
              <td>{{ item.equipmentModel || item.equipmentCode || '-' }}</td>
              <td>{{ formatTime(item.inboundTime || item.createdAt) }}</td>
              <td class="action-cell" @click.stop>
                <div class="row-actions">
                  <button class="secondary-button" @click="openDetail(item)">明细</button>
                  <button class="secondary-button" @click="openTrace(item)">追溯</button>
                  <button class="secondary-button" @click="openPrint(item)">打印</button>
                </div>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="15" class="empty-cell">{{ loading ? '正在查询看板...' : '没有匹配的看板' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <PageModal :open="viewMode === 'print' && !!selectedKanban" wide print-mode @close="closePrintModal">
      <section v-if="selectedKanban" class="panel kanban-print-panel">
        <div class="section-head compact-head print-toolbar">
          <div>
            <h3>看板打印</h3>
          </div>
          <div class="action-row">
            <button @click="browserPrint">浏览器打印</button>
          </div>
        </div>

        <div class="kanban-print-card">
          <div class="print-card-title">
            <strong>{{ selectedKanban.kanbanNo }}</strong>
            <span>{{ formatStatus(selectedKanban.status) }}</span>
          </div>
          <div class="print-label-body">
            <table class="print-info-table">
              <tbody>
                <tr><th>条码</th><td class="mono">{{ selectedKanban.barcode }}</td></tr>
                <tr><th>入库单</th><td class="mono">{{ selectedKanban.inboundNo }}</td></tr>
                <tr>
                  <th>出库单</th>
                  <td>
                    <span v-if="outboundNoList(selectedKanban.outboundNo).length" class="tag-list">
                      <span v-for="no in outboundNoList(selectedKanban.outboundNo)" :key="no" class="tag-pill">{{ no }}</span>
                    </span>
                    <span v-else>-</span>
                  </td>
                </tr>
                <tr><th>供应商</th><td>{{ selectedKanban.supplierName }}</td></tr>
                <tr><th>零件号</th><td class="mono">{{ selectedKanban.partCode }}</td></tr>
                <tr><th>零件名</th><td>{{ selectedKanban.partName }}</td></tr>
                <tr><th>批次</th><td>{{ selectedKanban.batchNo }}</td></tr>
                <tr><th>数量</th><td>{{ selectedKanban.qty }} {{ selectedKanban.unit }}</td></tr>
                <tr><th>可用</th><td>{{ selectedKanban.availableQty }}</td></tr>
                <tr><th>已出</th><td>{{ selectedKanban.outboundQty }}</td></tr>
                <tr><th>锁定</th><td>{{ selectedKanban.reservedQty }}</td></tr>
                <tr><th>转包锁定</th><td>{{ selectedKanban.reservedTransferQty }}</td></tr>
                <tr><th>迁移单</th><td class="mono">{{ selectedKanban.transferOrderNo || '-' }}</td></tr>
                <tr><th>来源看板</th><td class="mono">{{ selectedKanban.sourceKanbanId || '-' }}</td></tr>
                <tr><th>仓库性质</th><td>{{ formatWarehouseType(selectedKanban.warehouseType) }}</td></tr>
                <tr><th>转包</th><td>{{ selectedKanban.pendingRepack ? '是' : '否' }}</td></tr>
                <tr><th>器具</th><td>{{ selectedKanban.equipmentCode || '-' }} / {{ selectedKanban.equipmentModel || '-' }}</td></tr>
                <tr><th>仓区</th><td>{{ selectedKanban.warehouseName }} / {{ selectedKanban.zoneName }}</td></tr>
                <tr><th>库位</th><td>{{ selectedKanban.locationCode || '-' }}</td></tr>
                <tr><th>创建</th><td>{{ formatTime(selectedKanban.createdAt) }}</td></tr>
                <tr><th>入库</th><td>{{ formatTime(selectedKanban.inboundTime) }}</td></tr>
                <tr><th>出库</th><td>{{ formatTime(selectedKanban.outboundTime) }}</td></tr>
              </tbody>
            </table>
            <div class="print-qr-box">
              <QrCodeImage :text="selectedKanban.qrContent || selectedKanban.barcode" :size="176" />
              <span class="mono">{{ selectedKanban.qrContent || selectedKanban.barcode }}</span>
            </div>
          </div>
        </div>
      </section>
    </PageModal>

    <KanbanDetailModal :open="!!detailKanban" :kanban="detailKanban" @close="closeDetail" />
    <KanbanTraceModal :open="!!traceKanban" :kanban="traceKanban" @close="closeTrace" />
  </section>
</template>

<style scoped>
.compact-head {
  align-items: center;
  margin-bottom: 0;
}

.compact-head h3,
.kanban-filter-line h3 {
  margin: 0;
  white-space: nowrap;
  font-size: 16px;
}

.kanban-info-page,
.kanban-info-page > .panel {
  min-width: 0;
  max-width: 100%;
}

.kanban-info-page {
  gap: 12px;
}

.scan-panel {
  padding: 10px 12px;
}

.scan-action-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 10px;
  align-items: start;
  margin-top: 8px;
}

.scan-main {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.scan-control-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.3fr) minmax(220px, 1fr);
  gap: 6px;
}

.scan-control-row input,
.scan-control-row select {
  min-height: 32px;
}

.scan-hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.scan-qr-preview {
  display: grid;
  justify-items: center;
  gap: 4px;
  padding: 6px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.92);
}

.scan-qr-preview p {
  max-width: 100%;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 11px;
}

.kanban-filter-panel {
  padding: 10px 12px;
  overflow: hidden;
}

.kanban-filter-line {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) max-content;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.kanban-filter-line > h3 {
  width: 96px;
}

.kanban-filter-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.kanban-filter-row input,
.kanban-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.kanban-filter-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
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

.kanban-table-panel {
  padding: 10px 12px;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  padding: 0 0 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.table-toolbar .form-error {
  margin: 0;
}

.kanban-table {
  min-width: 1360px;
  table-layout: fixed;
}

.kanban-table th {
  background: rgba(148, 163, 184, 0.08);
}

.kanban-table tbody tr:last-child td {
  border-bottom: 0;
}

.kanban-table th,
.kanban-table td {
  padding: 6px 8px;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kanban-table th:nth-child(1),
.kanban-table td:nth-child(1) {
  width: 152px;
}

.kanban-table th:nth-child(2),
.kanban-table td:nth-child(2),
.kanban-table th:nth-child(3),
.kanban-table td:nth-child(3) {
  width: 132px;
}

.kanban-table th:nth-child(4),
.kanban-table td:nth-child(4) {
  width: 112px;
}

.kanban-table th:nth-child(7),
.kanban-table td:nth-child(7) {
  width: 104px;
}

.kanban-table th:nth-child(8),
.kanban-table td:nth-child(8) {
  width: 72px;
}

.kanban-table th:nth-child(9),
.kanban-table td:nth-child(9) {
  width: 118px;
}

.kanban-table th:nth-child(12),
.kanban-table td:nth-child(12) {
  width: 176px;
}

.kanban-table .action-col,
.kanban-table td:last-child {
  width: 188px;
}

.kanban-row {
  cursor: pointer;
  transition:
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.kanban-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.kanban-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.kanban-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.kanban-row.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.row-actions button {
  min-height: 28px;
  padding: 0 8px;
  white-space: nowrap;
}

.kanban-table td.action-cell {
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

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.tag-list {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;
}

.tag-pill {
  max-width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 1px 6px;
  background: rgba(148, 163, 184, 0.12);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

.kanban-print-panel {
  padding: 14px;
}

.print-toolbar {
  margin-bottom: 10px;
}

.kanban-print-card {
  display: grid;
  width: min(720px, 100%);
  gap: 0;
  border: 1px solid #334155;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.print-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-bottom: 1px solid #334155;
  background: #e2e8f0;
  color: #0f172a;
}

.print-label-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 210px;
  gap: 4px;
  padding: 4px;
  align-items: stretch;
}

.print-info-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 11px;
  line-height: 1.2;
}

.print-info-table th,
.print-info-table td {
  border: 1px solid #cbd5e1;
  padding: 2px 4px;
  text-align: left;
  vertical-align: middle;
  word-break: break-word;
}

.print-info-table th {
  width: 58px;
  background: #f1f5f9;
  color: #475569;
  font-weight: 700;
}

.print-info-table td {
  color: #0f172a;
}

.print-qr-box {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 4px;
  min-width: 0;
  border: 1px solid #cbd5e1;
  padding: 4px;
}

.print-qr-box :deep(.qr-wrap) {
  padding: 0;
  border: 0;
  border-radius: 0;
}

.print-qr-box span {
  max-width: 100%;
  overflow-wrap: anywhere;
  font-size: 10px;
  line-height: 1.2;
  text-align: center;
}

@media (max-width: 1280px) {
  .kanban-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .kanban-filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 900px) {
  .scan-action-layout,
  .scan-control-row,
  .kanban-filter-row,
  .print-label-body {
    grid-template-columns: 1fr;
  }
}

@media print {
  .kanban-print-panel {
    padding: 0;
    box-shadow: none;
  }

  .print-toolbar {
    display: none !important;
  }

  .kanban-print-card {
    width: 100%;
    border-radius: 0;
    break-inside: avoid;
  }

  .print-card-title {
    padding: 4px 6px;
    font-size: 12px;
  }

  .print-label-body {
    grid-template-columns: minmax(0, 1fr) max-content;
    gap: 2px;
    padding: 2px;
  }

  .print-info-table {
    font-size: 9px;
  }

  .print-info-table th,
  .print-info-table td {
    padding: 1px 2px;
  }

  .print-info-table th {
    width: 42px;
  }

  .print-qr-box {
    padding: 1px;
  }

  .print-qr-box :deep(.qr-image) {
    width: 96px !important;
  }
}
</style>
