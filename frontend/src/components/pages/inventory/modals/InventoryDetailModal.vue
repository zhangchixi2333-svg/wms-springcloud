<!-- 本文件展示库存明细浮窗，承载库位明细和箱级看板分页表格。 -->
<script setup lang="ts">
import { formatStatus } from '../../../../app/displayText'
import CompactPager from '../../../shared/CompactPager.vue'
import PageModal from '../../../shared/PageModal.vue'
import QrCodeImage from '../../../shared/QrCodeImage.vue'
import type { InventoryPartSummary, InventoryRow, Kanban } from '../../../../types/app'

defineProps<{
  open: boolean
  activeRow: InventoryPartSummary | null
  locationRows: InventoryRow[]
  kanbans: Kanban[]
  locationPage: number
  locationPageSize: number
  locationTotal: number
  kanbanPage: number
  kanbanPageSize: number
  kanbanTotal: number
  loading: boolean
  errorMessage: string
}>()

const emit = defineEmits<{
  close: []
  'update:locationPage': [value: number]
  'update:locationPageSize': [value: number]
  'update:kanbanPage': [value: number]
  'update:kanbanPageSize': [value: number]
}>()
</script>

<template>
  <PageModal :open="open && !!activeRow" wide @close="emit('close')">
    <section class="panel inventory-detail-modal">
      <div class="section-head compact-modal-head">
        <div>
          <h3>库存明细：{{ activeRow?.partCode }} | {{ activeRow?.partName }}</h3>
        </div>
        <div class="detail-summary-strip">
          <span>供应商：{{ activeRow?.supplierName || '-' }}</span>
          <span>总量：{{ activeRow?.totalQty ?? 0 }}</span>
          <span>库位：{{ locationTotal }}</span>
          <span>看板：{{ kanbanTotal }}</span>
        </div>
      </div>
      <p v-if="errorMessage" class="form-error compact-modal-error">{{ errorMessage }}</p>
      <div class="inventory-detail-stack">
        <section class="sub-panel compact-sub-panel">
          <div class="detail-panel-head">
            <h4>库位明细</h4>
            <CompactPager
              :page="locationPage"
              :page-size="locationPageSize"
              :total="locationTotal"
              :page-size-options="[5, 10, 20, 50]"
              @update:page="emit('update:locationPage', $event)"
              @update:page-size="emit('update:locationPageSize', $event)"
            />
          </div>
          <div class="table-scroll modal-table-shell">
            <table class="table location-detail-table">
              <thead>
                <tr>
                  <th>库位</th>
                  <th>仓库 / 库区</th>
                  <th>数量</th>
                  <th>更新时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="location in locationRows" :key="location.id">
                  <td class="mono">{{ location.locationCode }}</td>
                  <td>{{ location.warehouseName }} / {{ location.zoneName }}</td>
                  <td>{{ location.qty }}</td>
                  <td>{{ new Date(location.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
                </tr>
                <tr v-if="!locationRows.length">
                  <td colspan="4" class="empty-cell">{{ loading ? '正在查询库位明细...' : '该零件暂无库位明细' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section class="sub-panel compact-sub-panel">
          <div class="detail-panel-head">
            <h4>对应看板</h4>
            <CompactPager
              :page="kanbanPage"
              :page-size="kanbanPageSize"
              :total="kanbanTotal"
              :page-size-options="[5, 10, 20, 50]"
              @update:page="emit('update:kanbanPage', $event)"
              @update:page-size="emit('update:kanbanPageSize', $event)"
            />
          </div>
          <div class="table-scroll modal-table-shell">
            <table class="table kanban-detail-table">
              <thead>
                <tr>
                  <th>看板号</th>
                  <th>状态</th>
                  <th>箱序</th>
                  <th>数量 / 可用</th>
                  <th>已出 / 锁定</th>
                  <th>入库单</th>
                  <th>库区</th>
                  <th>二维码</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="kanban in kanbans" :key="kanban.id">
                  <td class="mono">{{ kanban.kanbanNo }}</td>
                  <td>{{ formatStatus(kanban.status) }}</td>
                  <td>第 {{ kanban.boxIndex }} 箱</td>
                  <td>{{ kanban.qty }} / {{ kanban.availableQty }}</td>
                  <td>{{ kanban.outboundQty }} / {{ kanban.reservedQty }}</td>
                  <td class="mono">{{ kanban.inboundNo }}</td>
                  <td>{{ kanban.warehouseName }} / {{ kanban.zoneName }}</td>
                  <td class="qr-cell"><QrCodeImage :text="kanban.qrContent || kanban.barcode" :size="54" /></td>
                </tr>
                <tr v-if="!kanbans.length">
                  <td colspan="8" class="empty-cell">{{ loading ? '正在查询看板明细...' : '该零件暂无可展示看板' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </section>
  </PageModal>
</template>

<style scoped>
.inventory-detail-modal {
  display: grid;
  gap: 10px;
  padding: 14px;
  min-width: 0;
  max-width: 100%;
}

.compact-modal-head,
.detail-panel-head {
  align-items: center;
}

.compact-modal-head h3,
.compact-sub-panel h4 {
  margin: 0;
  white-space: nowrap;
}

.detail-summary-strip,
.detail-panel-head {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
}

.detail-summary-strip {
  color: var(--text-secondary);
  font-size: 12px;
}

.detail-summary-strip span {
  min-width: 0;
  padding: 4px 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inventory-detail-stack {
  display: grid;
  grid-template-columns: minmax(300px, 0.58fr) minmax(0, 1.42fr);
  gap: 10px;
  align-items: start;
  min-width: 0;
  max-width: 100%;
}

.compact-sub-panel {
  display: grid;
  gap: 6px;
  padding: 8px;
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.92);
}

.compact-sub-panel h4 {
  color: var(--text-secondary);
  font-size: 13px;
}

.compact-modal-error {
  margin: 0;
}

.table-scroll,
.modal-table-shell {
  min-width: 0;
  max-width: 100%;
}

.modal-table-shell {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
  overflow: auto;
}

.location-detail-table,
.kanban-detail-table {
  table-layout: fixed;
}

.location-detail-table {
  min-width: 520px;
}

.kanban-detail-table {
  min-width: 860px;
}

.location-detail-table th,
.location-detail-table td,
.kanban-detail-table th,
.kanban-detail-table td {
  padding: 6px 8px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.location-detail-table th:nth-child(1),
.location-detail-table td:nth-child(1) {
  width: 130px;
}

.location-detail-table th:nth-child(3),
.location-detail-table td:nth-child(3) {
  width: 92px;
}

.location-detail-table th:nth-child(4),
.location-detail-table td:nth-child(4) {
  width: 170px;
}

.kanban-detail-table th:nth-child(1),
.kanban-detail-table td:nth-child(1) {
  width: 156px;
}

.kanban-detail-table th:nth-child(8),
.kanban-detail-table td:nth-child(8) {
  width: 78px;
  text-align: center;
}

.qr-cell {
  text-align: center;
}

.qr-cell :deep(.qr-wrap) {
  padding: 2px;
}

@media (max-width: 920px) {
  .inventory-detail-stack {
    grid-template-columns: 1fr;
  }
}
</style>
