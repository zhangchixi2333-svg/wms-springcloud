<!-- 本组件承载入库单退回浮窗，负责过滤可退回状态、默认勾选和提交退回。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '../../../../api/wms'
import { formatStatus } from '../../../../app/displayText'
import PageModal from '../../../shared/PageModal.vue'
import type { InboundOrder } from '../../../../types/app'

const props = defineProps<{
  open: boolean
  mode: 'single' | 'batch'
  orders: InboundOrder[]
  initialSelectedIds: number[]
}>()

const emit = defineEmits<{
  close: []
  completed: []
}>()

const selection = reactive<Record<number, boolean>>({})
const submitting = ref(false)
const resultMessage = ref('')

const title = computed(() => (props.mode === 'batch' ? '批量退回入库单' : '退回入库单'))
const returnableOrders = computed(() => props.orders.filter(canReturnInboundOrder))
const selectedOrders = computed(() => returnableOrders.value.filter((item) => selection[item.id]))
const selectedCount = computed(() => selectedOrders.value.length)

watch(
  () => [props.open, props.orders, props.initialSelectedIds] as const,
  ([open]) => {
    if (!open) return
    clearSelection()
    resultMessage.value = ''
    submitting.value = false
    const selected = new Set(props.initialSelectedIds)
    returnableOrders.value.forEach((order) => {
      if (selected.has(order.id)) selection[order.id] = true
    })
  },
  { immediate: true },
)

function clearSelection() {
  Object.keys(selection).forEach((key) => delete selection[Number(key)])
}

function canReturnInboundOrder(order: InboundOrder) {
  return order.status === 'CREATED'
}

function inboundReturnDisabledReason(order: InboundOrder) {
  if (canReturnInboundOrder(order)) return '可退回'
  if (order.status === 'PARTIAL') return '已有部分箱看板入库，不能退回'
  if (order.status === 'COMPLETED') return '已完成入库，不能退回'
  if (order.status === 'RETURNED') return '已退回'
  return `${formatStatus(order.status)} 状态不能退回`
}

function orderQty(order: InboundOrder, key: 'plannedQty' | 'receivedQty') {
  return order.items.reduce((sum, item) => sum + Number(item[key] ?? 0), 0)
}

function setSelected(order: InboundOrder, checked: boolean) {
  if (!canReturnInboundOrder(order)) return
  selection[order.id] = checked
}

function toggleSelected(order: InboundOrder) {
  if (!canReturnInboundOrder(order)) return
  selection[order.id] = !selection[order.id]
}

function selectAllReturnable() {
  returnableOrders.value.forEach((order) => {
    selection[order.id] = true
  })
}

async function submitReturn() {
  const targets = selectedOrders.value
  if (!targets.length || submitting.value) return
  submitting.value = true
  resultMessage.value = ''
  try {
    for (const order of targets) {
      await api.returnInboundOrder(order.id)
    }
    resultMessage.value = `退回成功：${targets.map((item) => item.inboundNo).join('、')}`
    emit('completed')
  } catch (error) {
    resultMessage.value = error instanceof Error ? error.message : '入库单退回失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PageModal :open="open" wide @close="emit('close')">
    <section class="panel return-operation-panel">
      <div class="section-head compact-modal-head">
        <div>
          <h3>{{ title }}</h3>
        </div>
        <div class="action-row return-actions">
          <button class="secondary-button" :disabled="!returnableOrders.length" @click="selectAllReturnable">全选可退回</button>
          <button class="secondary-button" :disabled="!selectedCount" @click="clearSelection">清空</button>
          <button class="danger-button" :disabled="!selectedCount || submitting" @click="submitReturn">
            {{ submitting ? '正在退回...' : '确认退回 ' + selectedCount + ' 单' }}
          </button>
        </div>
      </div>

      <div class="return-summary-strip">
        <span>可退回 {{ returnableOrders.length }} 单</span>
        <span>已选择 {{ selectedCount }} 单</span>
      </div>

      <div class="table-scroll return-table-wrap">
        <table class="table return-order-table">
          <thead>
            <tr>
              <th>选择</th>
              <th>入库单号</th>
              <th>供应商</th>
              <th>状态</th>
              <th>明细</th>
              <th>箱数</th>
              <th>计划数量</th>
              <th>已入库</th>
              <th>退回判断</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(order, index) in returnableOrders"
              :key="order.id"
              class="return-order-row"
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
              <td class="mono">{{ order.inboundNo }}</td>
              <td>{{ order.supplierName }}</td>
              <td>{{ formatStatus(order.status) }}</td>
              <td>{{ order.items.length }}</td>
              <td>{{ order.items.reduce((sum, item) => sum + Number(item.boxCount), 0) }}</td>
              <td>{{ orderQty(order, 'plannedQty').toFixed(3) }}</td>
              <td>{{ orderQty(order, 'receivedQty').toFixed(3) }}</td>
              <td>{{ inboundReturnDisabledReason(order) }}</td>
            </tr>
            <tr v-if="!returnableOrders.length">
              <td colspan="9" class="empty-cell">当前查询结果没有可退回的已创建入库单</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="resultMessage" class="scan-result-message" :class="{ error: !resultMessage.includes('成功') }">{{ resultMessage }}</p>
    </section>
  </PageModal>
</template>

<style scoped>
.return-operation-panel {
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

.return-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
}

.return-actions button {
  min-height: 32px;
  padding: 0 10px;
  white-space: nowrap;
}

.return-summary-strip {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-secondary);
  font-size: 13px;
}

.return-summary-strip span {
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  white-space: nowrap;
}

.return-table-wrap {
  max-height: min(520px, 58vh);
  min-width: 0;
  max-width: 100%;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.return-order-table {
  min-width: 1160px;
  table-layout: fixed;
}

.return-order-table th,
.return-order-table td {
  padding: 6px 8px;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.return-order-table th:first-child,
.return-order-table td:first-child {
  width: 44px;
  text-align: center;
}

.return-order-table th:nth-child(2),
.return-order-table td:nth-child(2) {
  width: 166px;
}

.return-order-table th:nth-child(3),
.return-order-table td:nth-child(3) {
  width: 190px;
}

.return-order-table th:nth-child(4),
.return-order-table td:nth-child(4),
.return-order-table th:nth-child(5),
.return-order-table td:nth-child(5),
.return-order-table th:nth-child(6),
.return-order-table td:nth-child(6),
.return-order-table th:nth-child(7),
.return-order-table td:nth-child(7),
.return-order-table th:nth-child(8),
.return-order-table td:nth-child(8) {
  width: 92px;
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

.return-order-row {
  cursor: pointer;
}

.return-order-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.return-order-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.return-order-row:hover {
  background: rgba(37, 99, 235, 0.1);
}

.return-order-row.selected {
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
