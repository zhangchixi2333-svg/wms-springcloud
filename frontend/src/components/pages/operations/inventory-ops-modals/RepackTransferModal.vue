<!-- 本组件承载批量转包转出和第三方返还浮窗。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import './inventoryOpsModal.css'
import PageModal from '../../../shared/PageModal.vue'
import type { Kanban, Location, PageModel } from '../../../../types/app'
import KanbanBatchPicker from './KanbanBatchPicker.vue'
import { kanbanOptionValue, locationOptionLabel } from './inventoryOpsModalHelpers'

const props = defineProps<{
  open: boolean
  direction: 'out' | 'return'
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

const title = computed(() => (props.direction === 'out' ? '转包到第三方' : '第三方返还'))
const actionText = computed(() => (props.direction === 'out' ? '确认转包' : '确认返还'))
const pickerMode = computed(() => (props.direction === 'out' ? 'repackOut' : 'repackReturn'))
const locationOptions = computed(() =>
  props.model.state.locations.filter((item) =>
    props.direction === 'out' ? item.warehouseType === 'THIRD_PARTY' : item.warehouseType !== 'THIRD_PARTY',
  ),
)

watch(
  () => [props.open, props.direction] as const,
  ([open]) => {
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
    const payload = {
      barcodes: selectedKanbans.value.map(kanbanOptionValue),
      locationCode: form.locationCode,
      remark: form.remark,
    }
    if (props.direction === 'out') {
      await props.model.actions.repackOutboundBatch(payload)
    } else {
      await props.model.actions.repackInboundBatch(payload)
    }
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
        <h3>{{ title }}</h3>
        <div class="action-row">
          <select v-model="form.locationCode" class="ops-target-select">
            <option value="">{{ direction === 'out' ? '选择第三方库位' : '选择自有库位' }}</option>
            <option v-for="item in locationOptions" :key="item.id" :value="item.locationCode">
              {{ locationLabel(item) }}
            </option>
          </select>
          <input v-model="form.remark" class="ops-remark-input" placeholder="备注" />
          <button :disabled="!selectedKanbans.length || !form.locationCode || submitting" @click="submit">
            {{ actionText }} {{ selectedKanbans.length || '' }}
          </button>
        </div>
      </div>

      <KanbanBatchPicker
        :key="pickerMode"
        :open="open"
        :mode="pickerMode"
        :initial-kanban="initialKanban"
        :initial-kanbans="initialKanbans"
        @selection-change="selectedKanbans = $event"
      />
    </section>
  </PageModal>
</template>
