<!-- 本组件承载自有仓批量移库浮窗，分页选择箱级看板后统一提交。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import './inventoryOpsModal.css'
import PageModal from '../../../shared/PageModal.vue'
import type { Kanban, Location, PageModel } from '../../../../types/app'
import KanbanBatchPicker from './KanbanBatchPicker.vue'
import { kanbanOptionValue, locationOptionLabel } from './inventoryOpsModalHelpers'

const props = defineProps<{
  open: boolean
  model: PageModel
  initialKanban?: Kanban | null
  initialKanbans?: Kanban[]
}>()

const emit = defineEmits<{
  close: []
  completed: []
}>()

const selectedKanbans = ref<Kanban[]>([])
const submitting = ref(false)
const form = reactive({ locationCode: '', remark: '' })

const ownLocations = computed(() => props.model.state.locations.filter((item) => item.warehouseType !== 'THIRD_PARTY'))

watch(
  () => props.open,
  (open) => {
    if (!open) return
    form.locationCode = ''
    form.remark = ''
    selectedKanbans.value = []
  },
)

async function submit() {
  if (!selectedKanbans.value.length || !form.locationCode || submitting.value) return
  submitting.value = true
  try {
    await props.model.actions.transferKanbans({
      barcodes: selectedKanbans.value.map(kanbanOptionValue),
      locationCode: form.locationCode,
      remark: form.remark,
    })
    emit('completed')
  } finally {
    submitting.value = false
  }
}

function locationLabel(location: Location) {
  return locationOptionLabel(location)
}
</script>

<template>
  <PageModal :open="open" wide @close="emit('close')">
    <section class="panel ops-modal-panel batch-ops-panel">
      <div class="section-head compact-head">
        <h3>自有移库</h3>
        <div class="action-row">
          <select v-model="form.locationCode" class="ops-target-select">
            <option value="">选择自有目标库位</option>
            <option v-for="item in ownLocations" :key="item.id" :value="item.locationCode">
              {{ locationLabel(item) }}
            </option>
          </select>
          <input v-model="form.remark" class="ops-remark-input" placeholder="备注" />
          <button :disabled="!selectedKanbans.length || !form.locationCode || submitting" @click="submit">
            确认移库 {{ selectedKanbans.length || '' }}
          </button>
        </div>
      </div>

      <KanbanBatchPicker
        :open="open"
        mode="transfer"
        :initial-kanban="initialKanban"
        :initial-kanbans="initialKanbans"
        @selection-change="selectedKanbans = $event"
      />
    </section>
  </PageModal>
</template>
