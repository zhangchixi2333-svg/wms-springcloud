<!-- 本组件提供库存操作浮窗内的分页看板表格选择器。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../../api/wms'
import { formatStatus, formatWarehouseType } from '../../../../app/displayText'
import type { Kanban } from '../../../../types/app'
import CompactPager from '../../../shared/CompactPager.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    initialKanban?: Kanban | null
    initialKanbans?: Kanban[]
    mode: 'transfer' | 'freeze' | 'unfreeze' | 'repackOut' | 'repackReturn'
  }>(),
  {
    initialKanban: null,
    initialKanbans: () => [],
  },
)

const emit = defineEmits<{
  selectionChange: [items: Kanban[]]
}>()

const rows = ref<Kanban[]>([])
const selectedIds = ref<Set<number>>(new Set())
const selectedItems = ref<Map<number, Kanban>>(new Map())
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({
  kanbanNo: '',
  partCode: '',
  warehouseName: '',
  zoneName: '',
})

const selectedRows = computed(() => Array.from(selectedItems.value.values()))

const modeConfig = computed(() => {
  if (props.mode === 'transfer') {
    return {
      status: 'INBOUND,PARTIAL_OUTBOUND',
      warehouseType: 'OWN' as const,
      rule: '仅自有仓、已入库或部分出库、未封存、未锁定且有可用数量的看板可移库；目标必须是自有库位。',
    }
  }
  if (props.mode === 'freeze') {
    return {
      status: 'INBOUND,PARTIAL_OUTBOUND,THIRD_PARTY_STOCK',
      warehouseType: '' as const,
      rule: '封存允许已入库、部分出库或第三方在库，且未被出库/迁移锁定的看板。',
    }
  }
  if (props.mode === 'unfreeze') {
    return {
      status: 'FROZEN',
      warehouseType: '' as const,
      rule: '解封仅允许已封存状态的看板。',
    }
  }
  if (props.mode === 'repackOut') {
    return {
      status: 'INBOUND,PARTIAL_OUTBOUND',
      warehouseType: 'OWN' as const,
      rule: '转包转出仅允许自有仓、已入库或部分出库、未封存、未锁定且有可用数量的看板；目标必须是第三方库位。',
    }
  }
  return {
    status: 'THIRD_PARTY_STOCK',
    warehouseType: 'THIRD_PARTY' as const,
    rule: '转包转入仅允许第三方在库看板；目标必须是自有库位。',
  }
})

watch(selectedRows, (items) => emit('selectionChange', items), { immediate: true })

watch(
  () => [props.open, props.mode] as const,
  ([open]) => {
    if (!open) return
    const initialRows = props.initialKanbans.length ? props.initialKanbans : props.initialKanban ? [props.initialKanban] : []
    selectedIds.value = new Set(initialRows.map((item) => item.id))
    selectedItems.value = new Map(initialRows.map((item) => [item.id, item]))
    filters.kanbanNo = initialRows.length === 1 ? initialRows[0].kanbanNo : ''
    filters.partCode = ''
    filters.warehouseName = ''
    filters.zoneName = ''
    page.value = 1
    void fetchRows()
  },
)

watch([page, pageSize], () => {
  if (props.open) void fetchRows()
})

onMounted(() => {
  if (props.open) void fetchRows()
})

async function fetchRows() {
  loading.value = true
  try {
    const result = await api.listKanbansPage({
      status: modeConfig.value.status,
      warehouseType: modeConfig.value.warehouseType,
      kanbanNo: filters.kanbanNo || undefined,
      partCode: filters.partCode || undefined,
      warehouseName: filters.warehouseName || undefined,
      zoneName: filters.zoneName || undefined,
      page: page.value,
      size: pageSize.value,
    })
    rows.value = result.records
    total.value = result.total
    const selected = new Map(selectedItems.value)
    rows.value.forEach((item) => {
      if (selected.has(item.id)) selected.set(item.id, item)
    })
    selectedItems.value = selected
    selectedIds.value = new Set(selected.keys())
    if (!selected.size) {
      const initialRows = props.initialKanbans.length ? props.initialKanbans : props.initialKanban ? [props.initialKanban] : []
      const selectableInitialRows = initialRows.filter(isSelectable)
      selectedIds.value = new Set(selectableInitialRows.map((item) => item.id))
      selectedItems.value = new Map(selectableInitialRows.map((item) => [item.id, item]))
    }
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void fetchRows()
}

function rowReason(item: Kanban) {
  if (props.mode === 'freeze') {
    if (!['INBOUND', 'PARTIAL_OUTBOUND', 'THIRD_PARTY_STOCK'].includes(item.status)) return '只有已入库、部分出库或第三方在库可封存'
    if (item.reservedQty > 0 || item.reservedTransferQty > 0) return '已锁定不能封存'
    return ''
  }
  if (props.mode === 'unfreeze') {
    return item.status === 'FROZEN' ? '' : '只有已封存可解封'
  }
  if (props.mode === 'repackReturn') {
    if (item.status !== 'THIRD_PARTY_STOCK') return '只有第三方在库可返还'
    if (item.warehouseType !== 'THIRD_PARTY') return '来源必须是第三方仓'
    return ''
  }
  if (!['INBOUND', 'PARTIAL_OUTBOUND'].includes(item.status)) return '只有已入库或部分出库可操作'
  if (item.warehouseType === 'THIRD_PARTY') return '来源必须是自有仓'
  if (item.availableQty <= 0) return '没有可操作数量'
  if (item.reservedQty > 0 || item.reservedTransferQty > 0) return '已锁定'
  return ''
}

function isSelectable(item: Kanban) {
  return rowReason(item) === ''
}

function toggle(item: Kanban) {
  if (!isSelectable(item)) return
  setSelected(item, !selectedIds.value.has(item.id))
}

function setSelected(item: Kanban, selected: boolean) {
  if (!isSelectable(item)) return
  const next = new Set(selectedIds.value)
  const nextItems = new Map(selectedItems.value)
  if (selected) {
    next.add(item.id)
    nextItems.set(item.id, item)
  } else {
    next.delete(item.id)
    nextItems.delete(item.id)
  }
  selectedIds.value = next
  selectedItems.value = nextItems
}

function togglePage() {
  const selectable = rows.value.filter(isSelectable)
  const allSelected = selectable.length > 0 && selectable.every((item) => selectedIds.value.has(item.id))
  const next = new Set(selectedIds.value)
  const nextItems = new Map(selectedItems.value)
  selectable.forEach((item) => {
    if (allSelected) {
      next.delete(item.id)
      nextItems.delete(item.id)
    } else {
      next.add(item.id)
      nextItems.set(item.id, item)
    }
  })
  selectedIds.value = next
  selectedItems.value = nextItems
}
</script>

<template>
  <div class="kanban-picker">
    <div class="picker-toolbar">
      <span class="rule-chip">{{ modeConfig.rule }}</span>
      <input v-model="filters.kanbanNo" placeholder="看板号/条码" @keyup.enter="search" />
      <input v-model="filters.partCode" placeholder="零件号" @keyup.enter="search" />
      <input v-model="filters.warehouseName" placeholder="仓库" @keyup.enter="search" />
      <input v-model="filters.zoneName" placeholder="库区" @keyup.enter="search" />
      <button class="secondary-button" @click="search">查询</button>
    </div>

    <div class="picker-table-wrap">
      <table class="table picker-table">
        <thead>
          <tr>
            <th class="select-col"><input type="checkbox" @change="togglePage" /></th>
            <th>看板号</th>
            <th>零件</th>
            <th>数量</th>
            <th>可用/锁定</th>
            <th>仓库/库区</th>
            <th>性质</th>
            <th>状态</th>
            <th>移库单</th>
            <th>规则</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in rows"
            :key="item.id"
            :class="{ selected: selectedIds.has(item.id), disabled: !isSelectable(item) }"
            @click="toggle(item)"
          >
            <td class="select-col">
              <input
                type="checkbox"
                :checked="selectedIds.has(item.id)"
                :disabled="!isSelectable(item)"
                @click.stop
                @change.stop="setSelected(item, ($event.target as HTMLInputElement).checked)"
              />
            </td>
            <td class="mono">{{ item.kanbanNo }}</td>
            <td>{{ item.partCode }} | {{ item.partName }}</td>
            <td>{{ item.qty }}</td>
            <td>{{ item.availableQty }} / {{ item.reservedQty }}</td>
            <td>{{ item.warehouseName }} / {{ item.zoneName }}</td>
            <td>{{ formatWarehouseType(item.warehouseType) }}</td>
            <td><span class="status-badge">{{ formatStatus(item.status) }}</span></td>
            <td class="mono">{{ item.transferOrderNo || '-' }}</td>
            <td>{{ rowReason(item) || '可操作' }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="10" class="empty-cell">{{ loading ? '正在查询...' : '暂无符合条件的看板' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="total" :selected="selectedIds.size" />
  </div>
</template>
