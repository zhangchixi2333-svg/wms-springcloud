<!-- 本组件用于展示出库单完整明细浮窗，主查询表只保留明细入口。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { formatStatus } from '../../../../app/displayText'
import { splitBusinessNos } from '../../../../app/kanbanHelpers'
import PageModal from '../../../shared/PageModal.vue'
import type { Kanban, OutboundOrder } from '../../../../types/app'

const props = defineProps<{
  open: boolean
  order: OutboundOrder | null
  kanbans: Kanban[]
}>()

const emit = defineEmits<{
  close: []
}>()

type DetailGroup = {
  key: string
  partText: string
  locationText: string
  boxes: number
  unitPerBox: number
  qty: number
  scannedQty: number
  kanbanNos: string[]
}

type AllocationLine = {
  key: string
  kanbanNo: string
  barcode: string
  qrContent: string
  partText: string
  inboundNo: string
  locationText: string
  allocatedQty: number
  outboundQty: number
  remainingQty: number
  status: string
  allocationDetail: string
}

const summary = computed(() => {
  if (!props.order) {
    return { boxCount: 0, plannedQty: 0, scannedQty: 0 }
  }
  return props.order.items.reduce(
    (acc, item) => ({
      boxCount: acc.boxCount + Number(item.boxCount ?? 0),
      plannedQty: acc.plannedQty + Number(item.plannedQty ?? 0),
      scannedQty: acc.scannedQty + Number(item.scannedQty ?? 0),
    }),
    { boxCount: 0, plannedQty: 0, scannedQty: 0 },
  )
})

const sourceText = computed(() => props.order?.inboundOrderNos.length ? props.order.inboundOrderNos.join('，') : '系统按 FIFO 自动分配')

const groupedItems = computed<DetailGroup[]>(() => {
  if (!props.order) return []
  const groups = new Map<string, DetailGroup>()
  props.order.items.forEach((item) => {
    const key = `${item.partId}:${item.warehouseName}:${item.zoneName}`
    const group = groups.get(key) ?? {
      key,
      partText: `${item.partCode} | ${item.partName}`,
      locationText: `${item.warehouseName} / ${item.zoneName}`,
      boxes: 0,
      unitPerBox: 0,
      qty: 0,
      scannedQty: 0,
      kanbanNos: [],
    }
    group.boxes += Number(item.boxCount ?? 1)
    group.unitPerBox = Number(item.unitPerBox ?? 0)
    group.qty += Number(item.plannedQty ?? 0)
    group.scannedQty += Number(item.scannedQty ?? 0)
    splitBusinessNos(item.kanbanNo).forEach((no) => {
      if (!group.kanbanNos.includes(no)) group.kanbanNos.push(no)
    })
    groups.set(key, group)
  })
  return Array.from(groups.values())
})

const allocationLines = computed<AllocationLine[]>(() => {
  if (!props.order) return []
  return props.order.items.flatMap((item) =>
    splitBusinessNos(item.kanbanNo).map((kanbanNo) => {
      const kanban = props.kanbans.find((box) => box.kanbanNo === kanbanNo)
      const qty = parseAllocationQtyText(item.allocationDetail, kanbanNo, Number(item.plannedQty ?? 0))
      return {
        key: `${item.id}:${kanbanNo}`,
        kanbanNo,
        barcode: kanban?.barcode ?? kanbanNo,
        qrContent: kanban?.qrContent ?? kanbanNo,
        partText: `${item.partCode} | ${item.partName}`,
        inboundNo: kanban?.inboundNo ?? '-',
        locationText: kanban ? `${kanban.warehouseName} / ${kanban.zoneName}` : `${item.warehouseName} / ${item.zoneName}`,
        allocatedQty: qty.allocatedQty,
        outboundQty: qty.outboundQty,
        remainingQty: qty.remainingQty,
        status: kanban?.status ?? (Number(item.scannedQty) >= Number(item.plannedQty) ? 'OUTBOUND' : 'ALLOCATED'),
        allocationDetail: item.allocationDetail || '-',
      }
    }),
  )
})

function parseAllocationQtyText(detail: string | null | undefined, kanbanNo: string, fallbackQty: number) {
  const segment = (detail || '')
    .split('；')
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${kanbanNo}：`))
  if (!segment) {
    return {
      allocatedQty: fallbackQty,
      outboundQty: 0,
      remainingQty: fallbackQty,
    }
  }
  const allocatedQty = Number(segment.match(/分配\s*([0-9.]+)/)?.[1] ?? fallbackQty)
  const outboundQty = Number(segment.match(/已出\s*([0-9.]+)/)?.[1] ?? 0)
  const remainingQty = Number(segment.match(/剩余\s*([0-9.]+)/)?.[1] ?? Math.max(0, allocatedQty - outboundQty))
  return {
    allocatedQty: Number.isFinite(allocatedQty) ? allocatedQty : fallbackQty,
    outboundQty: Number.isFinite(outboundQty) ? outboundQty : 0,
    remainingQty: Number.isFinite(remainingQty) ? remainingQty : Math.max(0, fallbackQty),
  }
}
</script>

<template>
  <PageModal :open="open" xl @close="emit('close')">
    <section class="panel detail-modal" v-if="order">
      <div class="section-head compact-modal-head">
        <div>
          <h3>出库单明细：<span class="mono">{{ order.outboundNo }}</span></h3>
        </div>
      </div>

      <table class="table summary-table">
        <tbody>
          <tr>
            <th>出库单号</th>
            <td class="mono wrap-cell">{{ order.outboundNo }}</td>
            <th>客户</th>
            <td class="wrap-cell">{{ order.customerName || '-' }}</td>
          </tr>
          <tr>
            <th>状态</th>
            <td>{{ order.status === 'PARTIAL' ? '部分出库' : formatStatus(order.status) }}</td>
            <th>创建时间</th>
            <td>{{ new Date(order.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          </tr>
          <tr>
            <th>箱数</th>
            <td>{{ summary.boxCount }}</td>
            <th>计划 / 已扫</th>
            <td>{{ summary.plannedQty.toFixed(3) }} / {{ summary.scannedQty.toFixed(3) }}</td>
          </tr>
          <tr>
            <th>FIFO 来源</th>
            <td class="wrap-cell" colspan="3">{{ sourceText }}</td>
          </tr>
          <tr>
            <th>出库二维码内容</th>
            <td class="mono wrap-cell" colspan="3">{{ order.qrContent || `WMS-OUTBOUND|${order.outboundNo}` }}</td>
          </tr>
        </tbody>
      </table>

      <section class="detail-section">
        <div class="section-head compact-sub-head">
          <h4>按零件汇总</h4>
          <span>{{ groupedItems.length }} 组</span>
        </div>
        <div class="table-scroll">
          <table class="table full-detail-table outbound-group-table">
            <thead>
              <tr>
                <th>零件</th>
                <th>仓库 / 库区</th>
                <th>箱数</th>
                <th>每箱数量</th>
                <th>计划数量</th>
                <th>已扫数量</th>
                <th>分配看板</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="group in groupedItems" :key="group.key">
                <td class="wrap-cell">{{ group.partText }}</td>
                <td class="wrap-cell">{{ group.locationText }}</td>
                <td>{{ group.boxes }}</td>
                <td>{{ group.unitPerBox.toFixed(3) }}</td>
                <td>{{ group.qty.toFixed(3) }}</td>
                <td>{{ group.scannedQty.toFixed(3) }}</td>
                <td class="wrap-cell mono">{{ group.kanbanNos.join('，') || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="detail-section">
        <div class="section-head compact-sub-head">
          <h4>看板分配明细</h4>
          <span>{{ allocationLines.length }} 条</span>
        </div>
        <div class="table-scroll">
          <table class="table full-detail-table allocation-table">
            <thead>
              <tr>
                <th>看板号</th>
                <th>条码</th>
                <th>二维码内容</th>
                <th>来源入库单</th>
                <th>零件</th>
                <th>仓库 / 库区</th>
                <th>分配数量</th>
                <th>已出数量</th>
                <th>剩余数量</th>
                <th>看板状态</th>
                <th>分配说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in allocationLines" :key="line.key">
                <td class="mono wrap-cell">{{ line.kanbanNo }}</td>
                <td class="mono wrap-cell">{{ line.barcode }}</td>
                <td class="mono wrap-cell">{{ line.qrContent }}</td>
                <td class="mono wrap-cell">{{ line.inboundNo }}</td>
                <td class="wrap-cell">{{ line.partText }}</td>
                <td class="wrap-cell">{{ line.locationText }}</td>
                <td>{{ line.allocatedQty.toFixed(3) }}</td>
                <td>{{ line.outboundQty.toFixed(3) }}</td>
                <td>{{ line.remainingQty.toFixed(3) }}</td>
                <td>{{ formatStatus(line.status) }}</td>
                <td class="wrap-cell">{{ line.allocationDetail }}</td>
              </tr>
              <tr v-if="!allocationLines.length">
                <td colspan="11" class="empty-cell">暂无分配明细</td>
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

.outbound-group-table {
  min-width: 1180px;
}

.allocation-table {
  min-width: 1720px;
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
