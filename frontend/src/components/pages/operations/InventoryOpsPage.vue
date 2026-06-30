<!-- 本文件实现库存操作列表页，具体移库、封存和转包操作由浮窗组件承载。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatStatus, formatWarehouseType } from '../../../app/displayText'
import type { Kanban, PageModel } from '../../../types/app'
import CompactPager from '../../shared/CompactPager.vue'
import FreezeModal from './inventory-ops-modals/FreezeModal.vue'
import KanbanOperationDetailModal from './inventory-ops-modals/KanbanOperationDetailModal.vue'
import RepackTransferModal from './inventory-ops-modals/RepackTransferModal.vue'
import TransferModal from './inventory-ops-modals/TransferModal.vue'

const props = defineProps<{ model: PageModel; mode: 'repack' | 'transfer' | 'freeze' }>()

const activeModal = ref<'transfer' | 'freeze' | 'unfreeze' | 'repackOut' | 'repackReturn' | null>(null)
const selectedKanban = ref<Kanban | null>(null)
const detailKanban = ref<Kanban | null>(null)
const modalInitialKanbans = ref<Kanban[]>([])
const visibleKanbans = ref<Kanban[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const selectedKanbanMap = ref<Map<number, Kanban>>(new Map())
const filters = reactive({ status: '', kanbanNo: '', partCode: '', warehouseName: '', zoneName: '' })

const pageTitle = computed(() => ({
  repack: '转包（库存迁移到第三方仓库）',
  transfer: '自有移库',
  freeze: '封存 / 解封',
}[props.mode]))

const pageQuery = computed(() => {
  if (props.mode === 'repack') {
    return { status: 'INBOUND,PARTIAL_OUTBOUND,THIRD_PARTY_STOCK', warehouseType: '' as const }
  }
  if (props.mode === 'freeze') {
    return { status: 'INBOUND,PARTIAL_OUTBOUND,THIRD_PARTY_STOCK,FROZEN', warehouseType: '' as const }
  }
  return { status: 'INBOUND,PARTIAL_OUTBOUND', warehouseType: 'OWN' as const }
})

const statusOptions = computed(() => {
  if (props.mode === 'repack') {
    return ['INBOUND', 'PARTIAL_OUTBOUND', 'THIRD_PARTY_STOCK']
  }
  if (props.mode === 'freeze') {
    return ['INBOUND', 'PARTIAL_OUTBOUND', 'THIRD_PARTY_STOCK', 'FROZEN']
  }
  return ['INBOUND', 'PARTIAL_OUTBOUND']
})

const summary = computed(() => ({
  total: visibleKanbans.value.length,
  own: visibleKanbans.value.filter((item) => item.warehouseType !== 'THIRD_PARTY').length,
  thirdParty: visibleKanbans.value.filter((item) => item.warehouseType === 'THIRD_PARTY').length,
  frozen: visibleKanbans.value.filter((item) => item.status === 'FROZEN').length,
}))
const selectedKanbans = computed(() => Array.from(selectedKanbanMap.value.values()))
const selectedCount = computed(() => selectedKanbans.value.length)
const selectedTransferKanbans = computed(() => selectedKanbans.value.filter(canTransfer))
const selectedFreezeKanbans = computed(() => selectedKanbans.value.filter(canFreeze))
const selectedUnfreezeKanbans = computed(() => selectedKanbans.value.filter(canUnfreeze))
const selectedRepackOutKanbans = computed(() => selectedKanbans.value.filter(canRepackOut))
const selectedRepackReturnKanbans = computed(() => selectedKanbans.value.filter(canRepackReturn))
const pageSelectableKanbans = computed(() => visibleKanbans.value.filter(isPageSelectable))
const pageSelectionChecked = computed(() =>
  pageSelectableKanbans.value.length > 0 && pageSelectableKanbans.value.every((item) => selectedKanbanMap.value.has(item.id)),
)

watch([page, pageSize], () => void fetchKanbans())
watch(
  () => props.mode,
  () => {
    filters.status = ''
    page.value = 1
    void fetchKanbans()
  },
)
onMounted(() => void fetchKanbans())

async function fetchKanbans() {
  loading.value = true
  try {
    const result = await api.listKanbansPage({
      status: filters.status || pageQuery.value.status,
      warehouseType: pageQuery.value.warehouseType,
      kanbanNo: filters.kanbanNo || undefined,
      partCode: filters.partCode || undefined,
      warehouseName: filters.warehouseName || undefined,
      zoneName: filters.zoneName || undefined,
      page: page.value,
      size: pageSize.value,
    })
    visibleKanbans.value = result.records
    total.value = result.total
    reconcileSelectedKanbans(result.records)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void fetchKanbans()
}

function resetFilters() {
  filters.status = ''
  filters.kanbanNo = ''
  filters.partCode = ''
  filters.warehouseName = ''
  filters.zoneName = ''
  page.value = 1
  void fetchKanbans()
}

function openModal(type: 'transfer' | 'freeze' | 'unfreeze' | 'repackOut' | 'repackReturn', kanban: Kanban | null = null) {
  const base = kanban ? [kanban] : selectedKanbans.value
  const eligible = base.filter((item) => canOpenWithMode(type, item))
  modalInitialKanbans.value = eligible
  selectedKanban.value = eligible[0] ?? null
  activeModal.value = type
}

function closeModal() {
  activeModal.value = null
  selectedKanban.value = null
  modalInitialKanbans.value = []
}

function openDetail(item: Kanban) {
  detailKanban.value = item
}

function closeDetail() {
  detailKanban.value = null
}

function completeModal() {
  closeModal()
  selectedKanbanMap.value = new Map()
  void fetchKanbans()
}

function canTransfer(item: Kanban) {
  return ['INBOUND', 'PARTIAL_OUTBOUND'].includes(item.status)
    && item.warehouseType !== 'THIRD_PARTY'
    && Number(item.availableQty) > 0
    && Number(item.reservedQty) <= 0
    && Number(item.reservedTransferQty) <= 0
}

function canFreeze(item: Kanban) {
  return ['INBOUND', 'PARTIAL_OUTBOUND', 'THIRD_PARTY_STOCK'].includes(item.status)
    && Number(item.reservedQty) <= 0
    && Number(item.reservedTransferQty) <= 0
}

function canUnfreeze(item: Kanban) {
  return item.status === 'FROZEN'
}

function canRepackOut(item: Kanban) {
  return canTransfer(item)
}

function canRepackReturn(item: Kanban) {
  return item.status === 'THIRD_PARTY_STOCK'
    && item.warehouseType === 'THIRD_PARTY'
    && Number(item.availableQty) > 0
    && Number(item.reservedQty) <= 0
    && Number(item.reservedTransferQty) <= 0
}

function canOpenWithMode(type: 'transfer' | 'freeze' | 'unfreeze' | 'repackOut' | 'repackReturn', item: Kanban) {
  if (type === 'transfer') return canTransfer(item)
  if (type === 'freeze') return canFreeze(item)
  if (type === 'unfreeze') return canUnfreeze(item)
  if (type === 'repackOut') return canRepackOut(item)
  return canRepackReturn(item)
}

function isPageSelectable(item: Kanban) {
  if (props.mode === 'transfer') return canTransfer(item)
  if (props.mode === 'freeze') return canFreeze(item) || canUnfreeze(item)
  return canRepackOut(item) || canRepackReturn(item)
}

function setSelected(item: Kanban, selected: boolean) {
  if (!isPageSelectable(item)) return
  const next = new Map(selectedKanbanMap.value)
  if (selected) {
    next.set(item.id, item)
  } else {
    next.delete(item.id)
  }
  selectedKanbanMap.value = next
}

function toggleSelected(item: Kanban) {
  setSelected(item, !selectedKanbanMap.value.has(item.id))
}

function togglePageSelected() {
  const selectable = pageSelectableKanbans.value
  const allSelected = selectable.length > 0 && selectable.every((item) => selectedKanbanMap.value.has(item.id))
  const next = new Map(selectedKanbanMap.value)
  selectable.forEach((item) => {
    if (allSelected) {
      next.delete(item.id)
    } else {
      next.set(item.id, item)
    }
  })
  selectedKanbanMap.value = next
}

function clearSelection() {
  selectedKanbanMap.value = new Map()
}

function reconcileSelectedKanbans(records: Kanban[]) {
  if (!selectedKanbanMap.value.size) return
  const next = new Map(selectedKanbanMap.value)
  records.forEach((item) => {
    if (next.has(item.id)) next.set(item.id, item)
  })
  selectedKanbanMap.value = next
}
</script>

<template>
  <section class="stack inventory-ops-page">
    <section class="panel ops-head-panel">
      <div class="ops-filter-line">
        <div class="ops-filter-title">
          <h3>{{ pageTitle }}</h3>
        </div>
        <div class="ops-filter-row">
          <select v-model="filters.status" title="状态">
            <option value="">全部状态</option>
            <option v-for="status in statusOptions" :key="status" :value="status">{{ formatStatus(status) }}</option>
          </select>
          <input v-model="filters.kanbanNo" placeholder="看板号/条码" @keyup.enter="search" />
          <input v-model="filters.partCode" placeholder="零件号" @keyup.enter="search" />
          <input v-model="filters.warehouseName" placeholder="仓库" @keyup.enter="search" />
          <input v-model="filters.zoneName" placeholder="库区" @keyup.enter="search" />
          <select v-model.number="pageSize" title="每页数量">
            <option :value="10">10条/页</option>
            <option :value="20">20条/页</option>
            <option :value="50">50条/页</option>
            <option :value="100">100条/页</option>
          </select>
          <button class="secondary-button compact-filter-button" @click="search">查询</button>
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row ops-filter-actions">
          <button v-if="props.mode === 'transfer'" class="secondary-button batch-action-button" :disabled="!selectedTransferKanbans.length" @click="openModal('transfer')">批量移库 {{ selectedTransferKanbans.length || '' }}</button>
          <button v-if="props.mode === 'freeze'" class="secondary-button batch-action-button" :disabled="!selectedFreezeKanbans.length" @click="openModal('freeze')">批量封存 {{ selectedFreezeKanbans.length || '' }}</button>
          <button v-if="props.mode === 'freeze'" class="secondary-button batch-action-button" :disabled="!selectedUnfreezeKanbans.length" @click="openModal('unfreeze')">批量解封 {{ selectedUnfreezeKanbans.length || '' }}</button>
          <button v-if="props.mode === 'repack'" class="secondary-button batch-action-button" :disabled="!selectedRepackOutKanbans.length" @click="openModal('repackOut')">批量转包 {{ selectedRepackOutKanbans.length || '' }}</button>
          <button v-if="props.mode === 'repack'" class="secondary-button batch-action-button" :disabled="!selectedRepackReturnKanbans.length" @click="openModal('repackReturn')">批量返还 {{ selectedRepackReturnKanbans.length || '' }}</button>
          <button class="secondary-button" :disabled="!selectedCount" @click="clearSelection">清空选择</button>
        </div>
      </div>
    </section>

    <section class="panel table-panel">
      <div class="table-toolbar">
        <div class="summary-line">
          <span>共 {{ total }}</span>
          <span>本页 {{ summary.total }}</span>
          <span>自有 {{ summary.own }}</span>
          <span>第三方 {{ summary.thirdParty }}</span>
          <span>封存 {{ summary.frozen }}</span>
          <span>已选 {{ selectedCount }}</span>
        </div>
        <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </div>
      <div class="table-scroll">
        <table class="table compact-ops-table">
          <thead>
            <tr>
              <th class="select-col"><input class="compact-check" type="checkbox" :checked="pageSelectionChecked" @change="togglePageSelected" /></th>
              <th>看板号</th>
              <th>零件</th>
              <th>数量</th>
              <th>可用/出库锁定/转包锁定</th>
              <th>仓库/库区</th>
              <th>性质</th>
              <th>状态</th>
              <th>迁移单</th>
              <th class="action-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in visibleKanbans"
              :key="item.id"
              :class="{ selected: selectedKanbanMap.has(item.id), disabled: !isPageSelectable(item) }"
              @click="toggleSelected(item)"
            >
              <td class="select-col">
                <input
                  class="compact-check"
                  type="checkbox"
                  :checked="selectedKanbanMap.has(item.id)"
                  :disabled="!isPageSelectable(item)"
                  @click.stop
                  @change.stop="setSelected(item, ($event.target as HTMLInputElement).checked)"
                />
              </td>
              <td class="mono">{{ item.kanbanNo }}</td>
              <td>{{ item.partCode }} | {{ item.partName }}</td>
              <td>{{ item.qty }}</td>
              <td>{{ item.availableQty }} / {{ item.reservedQty }} / {{ item.reservedTransferQty }}</td>
              <td>{{ item.warehouseName }} / {{ item.zoneName }}</td>
              <td>{{ formatWarehouseType(item.warehouseType) }}</td>
              <td><span class="status-badge">{{ formatStatus(item.status) }}</span></td>
              <td class="mono transfer-no-cell" :title="item.transferOrderNo || '-'">
                <span class="cell-ellipsis">{{ item.transferOrderNo || '-' }}</span>
              </td>
              <td class="action-cell" @click.stop>
                <div class="row-actions">
                  <button class="secondary-button" @click="openDetail(item)">明细</button>
                  <button v-if="props.mode === 'transfer' && canTransfer(item)" class="secondary-button" @click="openModal('transfer', item)">移库</button>
                  <button v-if="props.mode === 'freeze' && canFreeze(item)" class="secondary-button" @click="openModal('freeze', item)">封存</button>
                  <button v-if="props.mode === 'freeze' && canUnfreeze(item)" class="secondary-button" @click="openModal('unfreeze', item)">解封</button>
                  <button v-if="props.mode === 'repack' && canRepackOut(item)" class="secondary-button" @click="openModal('repackOut', item)">转包</button>
                  <button v-if="props.mode === 'repack' && canRepackReturn(item)" class="secondary-button" @click="openModal('repackReturn', item)">返还</button>
                </div>
              </td>
            </tr>
            <tr v-if="!visibleKanbans.length">
              <td colspan="10" class="empty-cell">{{ loading ? '正在查询...' : '暂无可操作看板' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <TransferModal
      :open="activeModal === 'transfer'"
      :model="model"
      :initial-kanban="selectedKanban"
      :initial-kanbans="modalInitialKanbans"
      @close="closeModal"
      @completed="completeModal"
    />
    <FreezeModal
      :open="activeModal === 'freeze'"
      :model="model"
      :frozen="true"
      :initial-kanban="selectedKanban"
      :initial-kanbans="modalInitialKanbans"
      @close="closeModal"
      @completed="completeModal"
    />
    <FreezeModal
      :open="activeModal === 'unfreeze'"
      :model="model"
      :frozen="false"
      :initial-kanban="selectedKanban"
      :initial-kanbans="modalInitialKanbans"
      @close="closeModal"
      @completed="completeModal"
    />
    <RepackTransferModal
      :open="activeModal === 'repackOut'"
      direction="out"
      :model="model"
      :initial-kanban="selectedKanban"
      :initial-kanbans="modalInitialKanbans"
      @close="closeModal"
      @completed="completeModal"
    />
    <RepackTransferModal
      :open="activeModal === 'repackReturn'"
      direction="return"
      :model="model"
      :initial-kanban="selectedKanban"
      :initial-kanbans="modalInitialKanbans"
      @close="closeModal"
      @completed="completeModal"
    />
    <KanbanOperationDetailModal
      :open="!!detailKanban"
      :kanban="detailKanban"
      @close="closeDetail"
    />
  </section>
</template>

<style scoped>
.inventory-ops-page {
  gap: 10px;
}

.ops-head-panel,
.table-panel {
  padding: 10px 12px;
  min-width: 0;
  overflow: hidden;
}

.ops-filter-line {
  display: grid;
  grid-template-columns: auto minmax(620px, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.ops-filter-title h3 {
  margin: 0;
  white-space: nowrap;
}

.summary-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.summary-line span {
  padding: 2px 7px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: #fff;
  line-height: 18px;
}

.ops-filter-row {
  display: grid;
  grid-template-columns: 108px minmax(150px, 1.05fr) minmax(110px, 0.82fr) minmax(110px, 0.82fr) minmax(100px, 0.76fr) 92px 58px 58px;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.ops-filter-row input,
.ops-filter-row select,
.compact-filter-button {
  min-width: 0;
  min-height: 34px;
}

.compact-filter-button {
  padding: 0 10px;
  white-space: nowrap;
}

.ops-filter-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
  min-width: 0;
}

.ops-filter-actions button {
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 0 8px;
  color: var(--text-secondary);
}

.table-scroll {
  max-width: 100%;
  overflow: auto;
}

.compact-ops-table {
  min-width: 1340px;
  table-layout: fixed;
}

.compact-ops-table th,
.compact-ops-table td {
  padding: 8px 10px;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-ops-table .select-col {
  width: 36px;
  padding-inline: 6px;
  text-align: center;
}

.compact-check {
  width: 13px;
  height: 13px;
  min-width: 13px;
  min-height: 13px;
  margin: 0;
  cursor: pointer;
  accent-color: var(--primary-color);
  vertical-align: middle;
}

.compact-check:hover {
  filter: brightness(0.92);
}

.compact-ops-table th:nth-child(2),
.compact-ops-table td:nth-child(2) {
  width: 168px;
}

.compact-ops-table th:nth-child(3),
.compact-ops-table td:nth-child(3) {
  width: 216px;
}

.compact-ops-table th:nth-child(4),
.compact-ops-table td:nth-child(4) {
  width: 82px;
}

.compact-ops-table th:nth-child(5),
.compact-ops-table td:nth-child(5) {
  width: 188px;
}

.compact-ops-table th:nth-child(6),
.compact-ops-table td:nth-child(6) {
  width: 204px;
}

.compact-ops-table th:nth-child(7),
.compact-ops-table td:nth-child(7) {
  width: 96px;
}

.compact-ops-table th:nth-child(8),
.compact-ops-table td:nth-child(8) {
  width: 112px;
}

.compact-ops-table th:nth-child(9),
.compact-ops-table td:nth-child(9) {
  width: 176px;
}

.compact-ops-table th:nth-child(10),
.compact-ops-table td:nth-child(10) {
  width: 254px;
  text-align: right;
}

.compact-ops-table .mono {
  word-break: normal;
}

.compact-ops-table tbody tr {
  cursor: pointer;
}

.compact-ops-table tbody tr:nth-child(even) {
  background: #fbfdff;
}

.compact-ops-table tbody tr:hover {
  background: rgba(37, 99, 235, 0.11);
}

.compact-ops-table tbody tr.selected {
  background: rgba(20, 184, 166, 0.16);
  box-shadow: inset 3px 0 0 rgba(20, 184, 166, 0.78);
}

.compact-ops-table tbody tr.selected:hover {
  background: rgba(20, 184, 166, 0.24);
}

.compact-ops-table tbody tr.disabled {
  color: var(--text-secondary);
  cursor: default;
}

.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  flex-wrap: nowrap;
  align-items: center;
  min-width: 0;
}

.row-actions button {
  min-height: 30px;
  padding: 0 8px;
  white-space: nowrap;
}

.compact-ops-table td.action-cell {
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

@media (max-width: 980px) {
  .ops-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .ops-filter-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .ops-filter-row {
    grid-template-columns: 1fr 1fr;
  }

  .compact-filter-button {
    grid-column: auto;
  }
}
</style>
