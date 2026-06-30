<!-- 本组件用于展示移库、转包、封存页面中的单箱看板完整明细。 -->
<script setup lang="ts">
import { formatStatus, formatWarehouseType } from '../../../../app/displayText'
import PageModal from '../../../shared/PageModal.vue'
import type { Kanban } from '../../../../types/app'

defineProps<{
  open: boolean
  kanban: Kanban | null
}>()

const emit = defineEmits<{
  close: []
}>()

function formatTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
</script>

<template>
  <PageModal :open="open" wide @close="emit('close')">
    <section class="panel kanban-detail-modal" v-if="kanban">
      <div class="section-head compact-modal-head">
        <div>
          <h3>看板明细：<span class="mono">{{ kanban.kanbanNo }}</span></h3>
        </div>
      </div>

      <table class="table detail-table">
        <tbody>
          <tr>
            <th>看板号</th>
            <td class="mono wrap-cell">{{ kanban.kanbanNo }}</td>
            <th>条码</th>
            <td class="mono wrap-cell">{{ kanban.barcode || '-' }}</td>
          </tr>
          <tr>
            <th>二维码内容</th>
            <td class="mono wrap-cell" colspan="3">{{ kanban.qrContent || '-' }}</td>
          </tr>
          <tr>
            <th>零件</th>
            <td class="wrap-cell">{{ kanban.partCode }} | {{ kanban.partName }}</td>
            <th>供应商</th>
            <td class="wrap-cell">{{ kanban.supplierName || '-' }}</td>
          </tr>
          <tr>
            <th>当前状态</th>
            <td>{{ formatStatus(kanban.status) }}</td>
            <th>封存前状态</th>
            <td>{{ formatStatus(kanban.frozenPreviousStatus) }}</td>
          </tr>
          <tr>
            <th>箱数量</th>
            <td>{{ Number(kanban.qty).toFixed(3) }} {{ kanban.unit }}</td>
            <th>可用 / 已出</th>
            <td>{{ Number(kanban.availableQty).toFixed(3) }} / {{ Number(kanban.outboundQty).toFixed(3) }}</td>
          </tr>
          <tr>
            <th>出库锁定</th>
            <td>{{ Number(kanban.reservedQty).toFixed(3) }}</td>
            <th>转移锁定</th>
            <td>{{ Number(kanban.reservedTransferQty).toFixed(3) }}</td>
          </tr>
          <tr>
            <th>仓库 / 库区</th>
            <td class="wrap-cell">{{ kanban.warehouseName }} / {{ kanban.zoneName }}</td>
            <th>仓库性质</th>
            <td>{{ formatWarehouseType(kanban.warehouseType) }}</td>
          </tr>
          <tr>
            <th>库位编码</th>
            <td class="mono wrap-cell">{{ kanban.locationCode || '-' }}</td>
            <th>库位ID</th>
            <td>{{ kanban.locationId ?? '-' }}</td>
          </tr>
          <tr>
            <th>入库单号</th>
            <td class="mono wrap-cell">{{ kanban.inboundNo || '-' }}</td>
            <th>出库单号</th>
            <td class="mono wrap-cell">{{ kanban.outboundNo || '-' }}</td>
          </tr>
          <tr>
            <th>迁移单号</th>
            <td class="mono wrap-cell">{{ kanban.transferOrderNo || '-' }}</td>
            <th>源看板ID</th>
            <td>{{ kanban.sourceKanbanId ?? '-' }}</td>
          </tr>
          <tr>
            <th>箱序 / 箱数</th>
            <td>第 {{ kanban.boxIndex }} 箱 / 共 {{ kanban.boxCount }} 箱</td>
            <th>批次</th>
            <td class="mono wrap-cell">{{ kanban.batchNo || '-' }}</td>
          </tr>
          <tr>
            <th>器具编码</th>
            <td class="mono wrap-cell">{{ kanban.equipmentCode || '-' }}</td>
            <th>器具型号</th>
            <td class="wrap-cell">{{ kanban.equipmentModel || '-' }}</td>
          </tr>
          <tr>
            <th>每箱数量</th>
            <td>{{ Number(kanban.unitPerBox).toFixed(3) }}</td>
            <th>转包标识</th>
            <td>{{ kanban.pendingRepack ? '需要转包' : '常规库存' }}</td>
          </tr>
          <tr>
            <th>创建时间</th>
            <td>{{ formatTime(kanban.createdAt) }}</td>
            <th>入库时间</th>
            <td>{{ formatTime(kanban.inboundTime) }}</td>
          </tr>
          <tr>
            <th>出库时间</th>
            <td colspan="3">{{ formatTime(kanban.outboundTime) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </PageModal>
</template>

<style scoped>
.kanban-detail-modal {
  display: grid;
  gap: 10px;
  padding: 14px;
  min-width: 0;
}

.compact-modal-head {
  margin-bottom: 0;
  align-items: center;
}

.compact-modal-head h3 {
  margin: 0;
}

.detail-table {
  table-layout: fixed;
}

.detail-table th,
.detail-table td {
  padding: 6px 8px;
  vertical-align: top;
}

.detail-table th {
  width: 116px;
  color: var(--text-secondary);
  background: rgba(148, 163, 184, 0.08);
}

.wrap-cell {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}
</style>
