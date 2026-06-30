<!-- 本组件用于展示库存流水详情浮窗，避免各页面重复维护同一套详情表格。 -->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '../../api/wms'
import { formatBusinessType, formatStatus } from '../../app/displayText'
import PageModal from './PageModal.vue'
import type { InventoryOperationOrderRow, TransactionRow } from '../../types/app'

const props = defineProps<{
  transaction: TransactionRow | null
}>()

const emit = defineEmits<{
  close: []
}>()

const operationRows = ref<InventoryOperationOrderRow[]>([])
const operationLoading = ref(false)
const operationError = ref('')

watch(
  () => [props.transaction?.operationNo, props.transaction?.barcode],
  async ([operationNo, barcode]) => {
    operationRows.value = []
    operationError.value = ''
    if (!operationNo) {
      return
    }
    operationLoading.value = true
    try {
      operationRows.value = await api.listInventoryOperationOrders(operationNo, barcode)
    } catch (error) {
      operationError.value = error instanceof Error ? error.message : '操作明细查询失败'
    } finally {
      operationLoading.value = false
    }
  },
  { immediate: true }
)
</script>

<template>
  <PageModal :open="!!transaction" xl @close="emit('close')">
    <section class="panel transaction-detail-modal">
      <div class="section-head compact-modal-head">
        <div>
          <h3>流水详情：{{ transaction?.transactionNo }}</h3>
        </div>
      </div>
      <table v-if="transaction" class="table compact-info-table">
        <tbody>
          <tr><th>流水类型</th><td>{{ formatBusinessType(transaction.businessType) }}</td></tr>
          <tr><th>操作号</th><td class="mono wrap-cell">{{ transaction.operationNo || '-' }}</td></tr>
          <tr><th>业务单号</th><td class="mono wrap-cell">{{ transaction.businessNo || '-' }}</td></tr>
          <tr><th>条码 / 二维码</th><td class="mono wrap-cell">{{ transaction.barcode || '-' }}</td></tr>
          <tr><th>零件编码</th><td class="mono">{{ transaction.partCode || '-' }}</td></tr>
          <tr><th>库位编码</th><td>{{ transaction.locationCode || '-' }}</td></tr>
          <tr><th>数量变化</th><td :class="{ outbound: Number(transaction.qtyChange) < 0, inbound: Number(transaction.qtyChange) > 0 }">{{ transaction.qtyChange }}</td></tr>
          <tr><th>发生时间</th><td>{{ new Date(transaction.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td></tr>
          <tr><th>备注</th><td class="wrap-cell">{{ transaction.remark || '-' }}</td></tr>
        </tbody>
      </table>
      <section v-if="transaction?.operationNo" class="operation-section">
        <div class="section-head compact-sub-head">
          <h4>操作明细</h4>
          <span>{{ operationLoading ? '正在加载...' : `${operationRows.length} 条` }}</span>
        </div>
        <p v-if="operationError" class="form-error operation-error">{{ operationError }}</p>
        <div class="table-scroll">
          <table class="table operation-table">
            <thead>
              <tr>
                <th>操作类型</th>
                <th>业务单号</th>
                <th>源看板</th>
                <th>目标看板</th>
                <th>零件</th>
                <th>源库位</th>
                <th>目标库位</th>
                <th>数量</th>
                <th>状态变化</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in operationRows" :key="item.id">
                <td>{{ formatBusinessType(item.operationType) }}</td>
                <td class="mono wrap-cell">{{ item.businessNo || '-' }}</td>
                <td class="mono wrap-cell">{{ item.sourceKanbanNo || '-' }}</td>
                <td class="mono wrap-cell">{{ item.targetKanbanNo || '-' }}</td>
                <td class="mono">{{ item.partCode || '-' }}</td>
                <td>{{ item.sourceLocationCode || '-' }}</td>
                <td>{{ item.targetLocationCode || '-' }}</td>
                <td>{{ item.qty }}</td>
                <td class="wrap-cell">
                  {{ formatStatus(item.sourceStatus) }} -> {{ formatStatus(item.targetStatus) }}
                </td>
                <td class="wrap-cell">{{ item.remark || '-' }}</td>
              </tr>
              <tr v-if="!operationRows.length">
                <td colspan="10" class="empty-cell">{{ operationLoading ? '正在查询操作明细...' : '没有操作明细' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  </PageModal>
</template>

<style scoped>
.transaction-detail-modal {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.compact-modal-head {
  align-items: center;
  margin-bottom: 0;
}

.compact-modal-head h3 {
  margin: 0;
  white-space: nowrap;
}

.compact-info-table {
  table-layout: fixed;
}

.compact-info-table th,
.compact-info-table td {
  padding: 7px 9px;
  vertical-align: top;
}

.compact-info-table th {
  width: 112px;
  color: var(--text-secondary);
  background: rgba(148, 163, 184, 0.08);
}

.operation-section {
  display: grid;
  gap: 8px;
}

.compact-sub-head {
  margin-bottom: 0;
}

.compact-sub-head h4 {
  margin: 0;
}

.compact-sub-head span {
  color: var(--text-secondary);
  font-size: 12px;
}

.table-scroll {
  overflow-x: auto;
}

.operation-table {
  min-width: 1080px;
  table-layout: fixed;
}

.operation-table th,
.operation-table td {
  padding: 6px 8px;
  vertical-align: middle;
}

.operation-table th:nth-child(1),
.operation-table td:nth-child(1) {
  width: 116px;
}

.operation-table th:nth-child(2),
.operation-table td:nth-child(2),
.operation-table th:nth-child(3),
.operation-table td:nth-child(3),
.operation-table th:nth-child(4),
.operation-table td:nth-child(4) {
  width: 142px;
}

.operation-table th:nth-child(8),
.operation-table td:nth-child(8) {
  width: 86px;
}

.operation-table th:nth-child(9),
.operation-table td:nth-child(9) {
  width: 150px;
}

.operation-error {
  margin: 0;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

.wrap-cell {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}
</style>
