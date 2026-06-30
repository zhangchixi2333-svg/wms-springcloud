<!-- 本组件用于按看板关联的入库、出库、迁移单号追溯库存流水事件。 -->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../../../../api/wms'
import { formatBusinessType, formatStatus } from '../../../../app/displayText'
import { splitBusinessNos } from '../../../../app/kanbanHelpers'
import PageModal from '../../../shared/PageModal.vue'
import type { InventoryOperationOrderRow, Kanban, TransactionRow } from '../../../../types/app'

const props = defineProps<{
  open: boolean
  kanban: Kanban | null
}>()

const emit = defineEmits<{
  close: []
}>()

const rows = ref<TransactionRow[]>([])
const loading = ref(false)
const errorMessage = ref('')
const activeTransaction = ref<TransactionRow | null>(null)
const operationRows = ref<InventoryOperationOrderRow[]>([])
const operationLoading = ref(false)
const operationError = ref('')

const outboundNos = computed(() => splitBusinessNos(props.kanban?.outboundNo))
const transferNos = computed(() => splitBusinessNos(props.kanban?.transferOrderNo))
const traceKeys = computed(() => {
  const keys = new Set<string>()
  if (props.kanban?.kanbanNo) keys.add(props.kanban.kanbanNo)
  if (props.kanban?.barcode) keys.add(props.kanban.barcode)
  if (props.kanban?.qrContent) keys.add(props.kanban.qrContent)
  return Array.from(keys).filter(Boolean)
})

watch(
  () => [props.open, props.kanban?.id],
  async () => {
    rows.value = []
    activeTransaction.value = null
    operationRows.value = []
    errorMessage.value = ''
    if (!props.open || !props.kanban) return
    await loadTrace()
  },
  { immediate: true },
)

async function loadTrace() {
  if (!props.kanban) return
  loading.value = true
  try {
    const queries: Promise<TransactionRow[]>[] = []
    const exactKeys = new Set(traceKeys.value)
    const addQuery = (filters: Parameters<typeof api.listTransactionsPage>[0]) => {
      queries.push(api.listTransactionsPage({ ...filters, page: 1, size: 200 }).then((result) => result.records))
    }
    traceKeys.value.forEach((key) => addQuery({ barcode: key }))
    if (props.kanban.kanbanNo) addQuery({ businessNo: props.kanban.kanbanNo })

    const records = (await Promise.all(queries)).flat().filter((row) => belongsToCurrentKanban(row, exactKeys))
    const map = new Map<number, TransactionRow>()
    records.forEach((row) => map.set(row.id, row))
    rows.value = Array.from(map.values()).sort((left, right) => {
      const timeDiff = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
      return timeDiff || Number(left.id) - Number(right.id)
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '看板追溯查询失败'
  } finally {
    loading.value = false
  }
}

function belongsToCurrentKanban(row: TransactionRow, exactKeys: Set<string>) {
  return Boolean(
    (row.barcode && exactKeys.has(row.barcode)) ||
      (row.businessNo && props.kanban?.kanbanNo && row.businessNo === props.kanban.kanbanNo),
  )
}

async function selectTransaction(row: TransactionRow) {
  activeTransaction.value = row
  operationRows.value = []
  operationError.value = ''
  if (!row.operationNo) return
  operationLoading.value = true
  try {
    operationRows.value = await api.listInventoryOperationOrders(row.operationNo, props.kanban?.barcode || row.barcode)
  } catch (error) {
    operationError.value = error instanceof Error ? error.message : '操作明细查询失败'
  } finally {
    operationLoading.value = false
  }
}

function formatTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
</script>

<template>
  <PageModal :open="open" xl @close="emit('close')">
    <section v-if="kanban" class="panel trace-modal">
      <div class="section-head compact-modal-head">
        <div>
          <h3>看板追溯：<span class="mono">{{ kanban.kanbanNo }}</span></h3>
        </div>
      </div>

      <div class="trace-key-strip">
        <span>入库 <strong class="mono">{{ kanban.inboundNo || '-' }}</strong></span>
        <span>出库 <strong class="mono">{{ outboundNos.join('，') || '-' }}</strong></span>
        <span>迁移 <strong class="mono">{{ transferNos.join('，') || '-' }}</strong></span>
        <span>线索 {{ traceKeys.length }}</span>
      </div>

      <p v-if="errorMessage" class="form-error trace-error">{{ errorMessage }}</p>

      <section class="trace-section">
        <div class="section-head compact-sub-head">
          <h4>流水事件</h4>
          <span>{{ loading ? '正在查询...' : `${rows.length} 条` }}</span>
        </div>
        <div class="table-scroll">
          <table class="table trace-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>流水号</th>
                <th>类型</th>
                <th>业务单号</th>
                <th>操作号</th>
                <th>条码</th>
                <th>零件</th>
                <th>库位</th>
                <th>数量变化</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="row.id"
                :class="{ selected: activeTransaction?.id === row.id }"
                @click="selectTransaction(row)"
              >
                <td>{{ formatTime(row.createdAt) }}</td>
                <td class="mono wrap-cell">{{ row.transactionNo }}</td>
                <td>{{ formatBusinessType(row.businessType) }}</td>
                <td class="mono wrap-cell">{{ row.businessNo || '-' }}</td>
                <td class="mono wrap-cell">{{ row.operationNo || '-' }}</td>
                <td class="mono wrap-cell">{{ row.barcode || '-' }}</td>
                <td class="mono">{{ row.partCode || '-' }}</td>
                <td>{{ row.locationCode || '-' }}</td>
                <td :class="{ outbound: Number(row.qtyChange) < 0, inbound: Number(row.qtyChange) > 0 }">{{ row.qtyChange }}</td>
                <td class="wrap-cell">{{ row.remark || '-' }}</td>
              </tr>
              <tr v-if="!rows.length">
                <td colspan="10" class="empty-cell">{{ loading ? '正在查询流水事件...' : '没有匹配的流水事件' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="activeTransaction" class="trace-section">
        <div class="section-head compact-sub-head">
          <h4>操作明细：<span class="mono">{{ activeTransaction.operationNo || '-' }}</span></h4>
          <span>{{ operationLoading ? '正在加载...' : `${operationRows.length} 条` }}</span>
        </div>
        <p v-if="operationError" class="form-error trace-error">{{ operationError }}</p>
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
                <td class="wrap-cell">{{ formatStatus(item.sourceStatus) }} -> {{ formatStatus(item.targetStatus) }}</td>
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
.trace-modal {
  display: grid;
  gap: 10px;
  padding: 14px;
  min-width: 0;
}

.compact-modal-head,
.compact-sub-head {
  align-items: center;
  margin-bottom: 0;
}

.compact-modal-head h3,
.compact-sub-head h4 {
  margin: 0;
}

.compact-sub-head h4 {
  overflow-wrap: anywhere;
}

.compact-sub-head span {
  color: var(--text-secondary);
  font-size: 12px;
}

.trace-key-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.trace-key-strip span {
  min-width: 0;
  padding: 3px 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  color: var(--text-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.trace-section {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.table-scroll {
  overflow: auto;
}

.trace-table,
.operation-table {
  table-layout: fixed;
}

.trace-table {
  min-width: 1580px;
}

.operation-table {
  min-width: 1180px;
}

.trace-table th,
.trace-table td,
.operation-table th,
.operation-table td {
  padding: 6px 8px;
  vertical-align: top;
}

.trace-table tbody tr {
  cursor: pointer;
}

.trace-table tbody tr:hover {
  background: rgba(37, 99, 235, 0.1);
}

.trace-table tbody tr.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.trace-table th:nth-child(1),
.trace-table td:nth-child(1) {
  width: 170px;
}

.trace-table th:nth-child(2),
.trace-table td:nth-child(2),
.trace-table th:nth-child(4),
.trace-table td:nth-child(4),
.trace-table th:nth-child(5),
.trace-table td:nth-child(5),
.trace-table th:nth-child(6),
.trace-table td:nth-child(6) {
  width: 150px;
}

.trace-table th:nth-child(3),
.trace-table td:nth-child(3) {
  width: 126px;
}

.trace-table th:nth-child(9),
.trace-table td:nth-child(9) {
  width: 90px;
}

.trace-error {
  margin: 0;
}

.wrap-cell {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}
</style>
