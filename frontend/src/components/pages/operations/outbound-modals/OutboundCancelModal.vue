<!-- 本组件承载出库单取消浮窗，负责可取消状态过滤、默认勾选和提交取消。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '../../../../api/wms'
import { formatStatus } from '../../../../app/displayText'
import PageModal from '../../../shared/PageModal.vue'
import type { OutboundOrder } from '../../../../types/app'

const props = defineProps<{
  open: boolean
  mode: 'single' | 'batch'
  orders: OutboundOrder[]
  initialSelectedIds: number[]
}>()

const emit = defineEmits<{
  close: []
  completed: [orders: OutboundOrder[]]
}>()

const selection = reactive<Record<number, boolean>>({})
const submitting = ref(false)
const resultMessage = ref('')

const title = computed(() => (props.mode === 'batch' ? '批量取消出库单' : '取消出库单'))
const cancelableOrders = computed(() => props.orders.filter(canCancelOutboundOrder))
const selectedOrders = computed(() => cancelableOrders.value.filter((item) => selection[item.id]))
const selectedCount = computed(() => selectedOrders.value.length)

watch(
  () => [props.open, props.orders, props.initialSelectedIds] as const,
  ([open]) => {
    if (!open) return
    clearSelection()
    resultMessage.value = ''
    submitting.value = false
    const selected = new Set(props.initialSelectedIds)
    cancelableOrders.value.forEach((order) => {
      if (selected.has(order.id)) selection[order.id] = true
    })
  },
  { immediate: true },
)

function clearSelection() {
  Object.keys(selection).forEach((key) => delete selection[Number(key)])
}

function formatOutboundStatus(status: string | null | undefined) {
  if (status === 'PARTIAL' || status === 'PARTIAL_OUTBOUND') return '部分出库'
  return formatStatus(status)
}

function canCancelOutboundOrder(order: OutboundOrder) {
  return order.status === 'CREATED'
}

function outboundCancelDisabledReason(order: OutboundOrder) {
  if (canCancelOutboundOrder(order)) return '可取消'
  if (order.status === 'PARTIAL' || order.status === 'PARTIAL_OUTBOUND') return '已部分出库，不能取消'
  if (order.status === 'COMPLETED') return '已完成出库，不能取消'
  if (order.status === 'CANCELLED' || order.status === 'CANCELED') return '已取消'
  return `${formatOutboundStatus(order.status)} 状态不能取消`
}

function setSelected(order: OutboundOrder, checked: boolean) {
  if (!canCancelOutboundOrder(order)) return
  selection[order.id] = checked
}

function toggleSelected(order: OutboundOrder) {
  if (!canCancelOutboundOrder(order)) return
  selection[order.id] = !selection[order.id]
}

function selectAllCancelable() {
  cancelableOrders.value.forEach((order) => {
    selection[order.id] = true
  })
}

async function submitCancel() {
  const targets = selectedOrders.value
  if (!targets.length || submitting.value) return
  submitting.value = true
  resultMessage.value = ''
  try {
    const cancelledOrders: OutboundOrder[] = []
    for (const order of targets) {
      cancelledOrders.push(await api.cancelOutboundOrder(order.id))
    }
    resultMessage.value = `取消成功：${targets.map((item) => item.outboundNo).join('、')}`
    emit('completed', cancelledOrders)
  } catch (error) {
    resultMessage.value = error instanceof Error ? error.message : '出库单取消失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PageModal :open="open" wide @close="emit('close')">
    <section class="panel cancel-operation-panel">
      <div class="section-head compact-modal-head">
        <div>
          <h3>{{ title }}</h3>
        </div>
        <div class="action-row cancel-actions">
          <button class="secondary-button" :disabled="!cancelableOrders.length" @click="selectAllCancelable">全选可取消</button>
          <button class="secondary-button" :disabled="!selectedCount" @click="clearSelection">清空</button>
          <button class="danger-button" :disabled="!selectedCount || submitting" @click="submitCancel">
            {{ submitting ? '正在取消...' : `确认取消 ${selectedCount} 单` }}
          </button>
        </div>
      </div>

      <div class="cancel-summary-strip">
        <span>可取消 {{ cancelableOrders.length }} 单</span>
        <span>已选择 {{ selectedCount }} 单</span>
      </div>

      <div class="table-scroll cancel-table-wrap">
        <table class="table cancel-order-table">
          <thead>
            <tr>
              <th>选择</th>
              <th>出库单号</th>
              <th>客户</th>
              <th>状态</th>
              <th>箱数</th>
              <th>计划数量</th>
              <th>创建时间</th>
              <th>取消判断</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(order, index) in cancelableOrders"
              :key="order.id"
              class="cancel-order-row"
              :class="{ selected: selection[order.id], 'tone-a': Math.floor(index / 2) % 2 === 0, 'tone-b': Math.floor(index / 2) % 2 === 1 }"
              @click="toggleSelected(order)"
            >
              <td>
                <input
                  class="compact-check"
                  type="checkbox"
                  :checked="!!selection[order.id]"
                  @click.stop
                  @change.stop="setSelected(order, ($event.target as HTMLInputElement).checked)"
                />
              </td>
              <td class="mono">{{ order.outboundNo }}</td>
              <td>{{ order.customerName || '-' }}</td>
              <td>{{ formatOutboundStatus(order.status) }}</td>
              <td>{{ order.items.reduce((sum, item) => sum + Number(item.boxCount ?? 0), 0) }}</td>
              <td>{{ order.items.reduce((sum, item) => sum + Number(item.plannedQty), 0).toFixed(3) }}</td>
              <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
              <td>{{ outboundCancelDisabledReason(order) }}</td>
            </tr>
            <tr v-if="!cancelableOrders.length">
              <td colspan="8" class="empty-cell">当前查询结果没有可取消的已创建出库单</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="resultMessage" class="scan-result-message" :class="{ error: !resultMessage.includes('成功') }">{{ resultMessage }}</p>
    </section>
  </PageModal>
</template>

<style scoped>
.cancel-operation-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
  min-width: 0;
}

.compact-modal-head {
  align-items: center;
  margin-bottom: 0;
}

.compact-modal-head h3 {
  margin: 0;
}

.cancel-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
}

.cancel-actions button {
  min-height: 32px;
  padding: 0 10px;
  white-space: nowrap;
}

.cancel-summary-strip {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-secondary);
  font-size: 13px;
}

.cancel-summary-strip span {
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  white-space: nowrap;
}

.cancel-table-wrap {
  max-height: min(520px, 58vh);
  min-width: 0;
  max-width: 100%;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.cancel-order-table {
  min-width: 1220px;
  table-layout: fixed;
}

.cancel-order-table th,
.cancel-order-table td {
  padding: 6px 8px;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cancel-order-table th:first-child,
.cancel-order-table td:first-child {
  width: 44px;
  text-align: center;
}

.cancel-order-table th:nth-child(2),
.cancel-order-table td:nth-child(2) {
  width: 190px;
}

.cancel-order-table th:nth-child(3),
.cancel-order-table td:nth-child(3) {
  width: 220px;
}

.cancel-order-table th:nth-child(4),
.cancel-order-table td:nth-child(4),
.cancel-order-table th:nth-child(5),
.cancel-order-table td:nth-child(5),
.cancel-order-table th:nth-child(6),
.cancel-order-table td:nth-child(6) {
  width: 96px;
}

.cancel-order-table th:nth-child(7),
.cancel-order-table td:nth-child(7) {
  width: 210px;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
}

.compact-check:hover {
  filter: brightness(0.92);
}

.cancel-order-row {
  cursor: pointer;
}

.cancel-order-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.cancel-order-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.cancel-order-row:hover {
  background: rgba(37, 99, 235, 0.1);
}

.cancel-order-row.selected {
  background: rgba(20, 184, 166, 0.15);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.scan-result-message {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid rgba(22, 163, 74, 0.24);
  border-radius: 8px;
  background: rgba(22, 163, 74, 0.08);
  color: #15803d;
}

.scan-result-message.error {
  border-color: rgba(220, 38, 38, 0.24);
  background: rgba(220, 38, 38, 0.08);
  color: #b91c1c;
}
</style>
