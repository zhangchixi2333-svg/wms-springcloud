<!-- 本文件实现出入库流水历史页，支持按流水类型、单号、条码、零件和库位过滤。 -->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatBusinessType } from '../../../app/displayText'
import CompactPager from '../../shared/CompactPager.vue'
import TransactionDetailModal from '../../shared/TransactionDetailModal.vue'
import type { PageModel, TransactionRow } from '../../../types/app'

defineProps<{ model: PageModel }>()

const filters = reactive({
  businessType: '',
  businessNo: '',
  operationNo: '',
  barcode: '',
  partCode: '',
  locationCode: '',
})
const transactionDetail = ref<TransactionRow | null>(null)
const rows = ref<TransactionRow[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const businessTypes = [
  'INBOUND_SCAN',
  'OUTBOUND_SCAN',
  'MANUAL_ENTRY',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'FREEZE',
  'UNFREEZE',
  'OUTSOURCE_TRANSFER_OUT',
  'OUTSOURCE_TRANSFER_IN',
  'OUTSOURCE_RETURN_OUT',
  'OUTSOURCE_RETURN_IN',
]

async function loadRecords(nextPage = page.value) {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listTransactionsPage({
      businessType: filters.businessType,
      businessNo: filters.businessNo.trim(),
      operationNo: filters.operationNo.trim(),
      barcode: filters.barcode.trim(),
      partCode: filters.partCode.trim(),
      locationCode: filters.locationCode.trim(),
      page: nextPage,
      size: pageSize.value,
    })
    rows.value = result.records
    page.value = result.page
    total.value = result.total
    if (!result.records.length && result.total > 0 && result.page > 1) {
      await loadRecords(Math.max(1, result.totalPages))
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '流水记录查询失败'
  } finally {
    loading.value = false
  }
}

async function searchRecords() {
  if (page.value === 1) {
    await loadRecords(1)
    return
  }
  page.value = 1
}

function resetFilters() {
  filters.businessType = ''
  filters.businessNo = ''
  filters.operationNo = ''
  filters.barcode = ''
  filters.partCode = ''
  filters.locationCode = ''
  void searchRecords()
}

function openTransactionDetail(row: TransactionRow) {
  transactionDetail.value = row
}

function closeTransactionDetail() {
  transactionDetail.value = null
}

watch(page, async (value) => {
  await loadRecords(value)
})

watch(pageSize, async () => {
  await searchRecords()
})

function handleBusinessChanged() {
  void loadRecords(page.value)
}

onMounted(() => {
  window.addEventListener('wms-business-changed', handleBusinessChanged)
  void loadRecords(1)
})

onBeforeUnmount(() => {
  window.removeEventListener('wms-business-changed', handleBusinessChanged)
})
</script>

<template>
  <section class="stack records-page">
    <section class="panel records-filter-panel">
      <div class="records-filter-line">
        <h3>出入库历史</h3>
        <div class="records-filter-row">
          <select v-model="filters.businessType">
            <option value="">全部流水类型</option>
            <option v-for="item in businessTypes" :key="item" :value="item">{{ formatBusinessType(item) }}</option>
          </select>
          <input v-model="filters.operationNo" placeholder="操作号 / 迁移单号" />
          <input v-model="filters.businessNo" placeholder="业务单号 / 看板号" />
          <input v-model="filters.barcode" placeholder="条码" />
          <input v-model="filters.partCode" placeholder="零件编码" />
          <input v-model="filters.locationCode" placeholder="库位编码" />
          <button class="secondary-button compact-filter-button" :disabled="loading" @click="searchRecords">查询</button>
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </div>
      <p v-if="errorMessage" class="form-error records-error">{{ errorMessage }}</p>
    </section>

    <section class="panel records-table-panel">
      <div class="table-scroll">
      <table class="table records-table">
        <thead>
          <tr>
            <th>流水号</th>
            <th>流水类型</th>
            <th>操作号</th>
            <th>业务单号</th>
            <th>条码</th>
            <th>零件</th>
            <th>库位</th>
            <th>数量变化</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id" @dblclick="openTransactionDetail(row)">
            <td class="mono">{{ row.transactionNo }}</td>
            <td>{{ formatBusinessType(row.businessType) }}</td>
            <td class="mono compact-text" :title="row.operationNo">{{ row.operationNo || '-' }}</td>
            <td class="mono compact-text">{{ row.businessNo || '-' }}</td>
            <td class="mono compact-text" :title="row.barcode">{{ row.barcode || '-' }}</td>
            <td class="mono">{{ row.partCode || '-' }}</td>
            <td>{{ row.locationCode }}</td>
            <td :class="{ outbound: Number(row.qtyChange) < 0, inbound: Number(row.qtyChange) > 0 }">{{ row.qtyChange }}</td>
            <td>{{ new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
            <td><button class="secondary-button compact-row-button" @click="openTransactionDetail(row)">详情</button></td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="10" class="empty-cell">{{ loading ? '正在查询流水记录...' : '没有匹配的流水记录' }}</td>
          </tr>
        </tbody>
      </table>
      </div>
    </section>

    <TransactionDetailModal :transaction="transactionDetail" @close="closeTransactionDetail" />
  </section>
</template>

<style scoped>
.records-filter-panel,
.records-table-panel {
  padding: 10px 12px;
}

.records-filter-line {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr) max-content;
  gap: 10px;
  align-items: center;
}

.records-filter-line h3,
.compact-modal-head h3 {
  margin: 0;
  white-space: nowrap;
}

.records-filter-row {
  display: grid;
  grid-template-columns: minmax(132px, 0.8fr) minmax(150px, 1fr) minmax(150px, 1fr) minmax(140px, 0.9fr) minmax(112px, 0.7fr) minmax(112px, 0.7fr) 58px 58px;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.records-filter-row input,
.records-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.records-error {
  margin: 8px 0 0;
}

.table-scroll {
  overflow-x: auto;
}

.records-table {
  min-width: 1320px;
  table-layout: fixed;
}

.records-table th,
.records-table td {
  padding: 6px 8px;
  vertical-align: middle;
}

.records-table th:nth-child(1),
.records-table td:nth-child(1) {
  width: 150px;
}

.records-table th:nth-child(2),
.records-table td:nth-child(2) {
  width: 132px;
}

.records-table th:nth-child(3),
.records-table td:nth-child(3) {
  width: 170px;
}

.records-table th:nth-child(4),
.records-table td:nth-child(4) {
  width: 170px;
}

.records-table th:nth-child(5),
.records-table td:nth-child(5) {
  width: 180px;
}

.records-table th:nth-child(6),
.records-table td:nth-child(6) {
  width: 110px;
}

.records-table th:nth-child(7),
.records-table td:nth-child(7),
.records-table th:nth-child(8),
.records-table td:nth-child(8) {
  width: 100px;
}

.records-table th:nth-child(9),
.records-table td:nth-child(9) {
  width: 170px;
}

.records-table th:nth-child(10),
.records-table td:nth-child(10) {
  width: 78px;
  text-align: right;
}

.records-table tbody tr {
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

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

@media (max-width: 1100px) {
  .records-filter-line {
    grid-template-columns: 1fr;
  }

  .records-filter-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
