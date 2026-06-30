<!-- 本组件用于展示入库单完整明细浮窗，避免主查询表格内联展开造成拥挤。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { formatStatus } from '../../../../app/displayText'
import PageModal from '../../../shared/PageModal.vue'
import type { InboundOrder, Kanban } from '../../../../types/app'

const props = defineProps<{
  open: boolean
  order: InboundOrder | null
  kanbans: Kanban[]
  loading?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const summary = computed(() => {
  if (!props.order) {
    return { itemCount: 0, boxCount: 0, plannedQty: 0, receivedQty: 0 }
  }
  return props.order.items.reduce(
    (acc, item) => ({
      itemCount: acc.itemCount + 1,
      boxCount: acc.boxCount + Number(item.boxCount ?? 0),
      plannedQty: acc.plannedQty + Number(item.plannedQty ?? 0),
      receivedQty: acc.receivedQty + Number(item.receivedQty ?? 0),
    }),
    { itemCount: 0, boxCount: 0, plannedQty: 0, receivedQty: 0 },
  )
})
</script>

<template>
  <PageModal :open="open" xl @close="emit('close')">
    <section class="panel detail-modal" v-if="order">
      <div class="section-head compact-modal-head">
        <div>
          <h3>入库单明细：<span class="mono">{{ order.inboundNo }}</span></h3>
        </div>
      </div>

      <table class="table summary-table">
        <tbody>
          <tr>
            <th>入库单号</th>
            <td class="mono wrap-cell">{{ order.inboundNo }}</td>
            <th>供应商</th>
            <td class="wrap-cell">{{ order.supplierName }}</td>
          </tr>
          <tr>
            <th>状态</th>
            <td>{{ formatStatus(order.status) }}</td>
            <th>创建时间</th>
            <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          </tr>
          <tr>
            <th>明细 / 箱数</th>
            <td>{{ summary.itemCount }} 项 / {{ summary.boxCount }} 箱</td>
            <th>计划 / 已入库</th>
            <td>{{ summary.plannedQty.toFixed(3) }} / {{ summary.receivedQty.toFixed(3) }}</td>
          </tr>
          <tr>
            <th>入库二维码内容</th>
            <td class="mono wrap-cell" colspan="3">{{ order.qrContent || `WMS-INBOUND|${order.inboundNo}` }}</td>
          </tr>
        </tbody>
      </table>

      <section class="detail-section">
        <div class="section-head compact-sub-head">
          <h4>入库明细</h4>
          <span>{{ order.items.length }} 项</span>
        </div>
        <div class="table-scroll">
          <table class="table full-detail-table inbound-item-table">
            <thead>
              <tr>
                <th>零件</th>
                <th>计划数量</th>
                <th>已入库</th>
                <th>箱数</th>
                <th>每箱数量</th>
                <th>器具</th>
                <th>目标仓区</th>
                <th>转包标识</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in order.items" :key="item.id">
                <td class="wrap-cell">{{ item.partCode }} | {{ item.partName }}</td>
                <td>{{ Number(item.plannedQty).toFixed(3) }} {{ item.unit }}</td>
                <td>{{ Number(item.receivedQty).toFixed(3) }} {{ item.unit }}</td>
                <td>{{ item.boxCount }}</td>
                <td>{{ Number(item.unitPerBox).toFixed(3) }}</td>
                <td class="mono wrap-cell">{{ item.equipmentCode || '-' }}</td>
                <td class="wrap-cell">{{ item.warehouseZone || '-' }}</td>
                <td>{{ item.pendingRepack ? '需要转包' : '常规入库' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="detail-section">
        <div class="section-head compact-sub-head">
          <h4>箱级看板</h4>
          <span>{{ loading ? '正在加载...' : `${kanbans.length} 个` }}</span>
        </div>
        <div class="table-scroll">
          <table class="table full-detail-table inbound-kanban-table">
            <thead>
              <tr>
                <th>箱看板</th>
                <th>箱序</th>
                <th>条码</th>
                <th>二维码内容</th>
                <th>零件</th>
                <th>数量</th>
                <th>状态</th>
                <th>仓库 / 库区</th>
                <th>入库时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="kanban in kanbans" :key="kanban.id">
                <td class="mono wrap-cell">{{ kanban.kanbanNo }}</td>
                <td>第 {{ kanban.boxIndex }} 箱</td>
                <td class="mono wrap-cell">{{ kanban.barcode || '-' }}</td>
                <td class="mono wrap-cell">{{ kanban.qrContent || '-' }}</td>
                <td class="wrap-cell">{{ kanban.partCode }} | {{ kanban.partName }}</td>
                <td>{{ Number(kanban.qty).toFixed(3) }} {{ kanban.unit }}</td>
                <td>{{ formatStatus(kanban.status) }}</td>
                <td class="wrap-cell">{{ kanban.warehouseName }} / {{ kanban.zoneName }}</td>
                <td>{{ kanban.inboundTime ? new Date(kanban.inboundTime).toLocaleString('zh-CN', { hour12: false }) : '-' }}</td>
              </tr>
              <tr v-if="!kanbans.length">
                <td colspan="9" class="empty-cell">{{ loading ? '正在加载箱看板...' : '该入库单暂无箱看板' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  </PageModal>
</template>

<style scoped>
.detail-modal {
  display: grid;
  gap: 10px;
  padding: 14px;
  min-width: 0;
}

.compact-modal-head,
.compact-sub-head {
  margin-bottom: 0;
  align-items: center;
}

.compact-modal-head h3,
.compact-sub-head h4 {
  margin: 0;
}

.compact-sub-head span {
  color: var(--text-secondary);
  font-size: 12px;
}

.summary-table {
  table-layout: fixed;
}

.summary-table th,
.summary-table td,
.full-detail-table th,
.full-detail-table td {
  padding: 6px 8px;
  vertical-align: top;
}

.summary-table th {
  width: 118px;
  color: var(--text-secondary);
  background: rgba(148, 163, 184, 0.08);
}

.detail-section {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.table-scroll {
  overflow: auto;
}

.full-detail-table {
  table-layout: fixed;
}

.inbound-item-table {
  min-width: 1180px;
}

.inbound-kanban-table {
  min-width: 1480px;
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
