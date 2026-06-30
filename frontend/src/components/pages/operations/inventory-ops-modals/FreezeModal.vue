<!-- 本组件承载箱看板批量封存和解封浮窗。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import './inventoryOpsModal.css'
import PageModal from '../../../shared/PageModal.vue'
import type { Kanban, PageModel } from '../../../../types/app'
import KanbanBatchPicker from './KanbanBatchPicker.vue'
import { kanbanOptionValue } from './inventoryOpsModalHelpers'

const props = defineProps<{
  open: boolean
  model: PageModel
  frozen: boolean
  initialKanban?: Kanban | null
  initialKanbans?: Kanban[]
}>()

const emit = defineEmits<{
  close: []
  completed: []
}>()

const selectedKanbans = ref<Kanban[]>([])
const submitting = ref(false)
const form = reactive({ remark: '' })
const pickerMode = computed(() => (props.frozen ? 'freeze' : 'unfreeze'))
const title = computed(() => (props.frozen ? '封存看板' : '解封看板'))
const actionText = computed(() => (props.frozen ? '确认封存' : '确认解封'))

watch(
  () => props.open,
  (open) => {
    if (!open) return
    form.remark = ''
    selectedKanbans.value = []
  },
)

async function submit() {
  if (!selectedKanbans.value.length || submitting.value) return
  submitting.value = true
  try {
    await props.model.actions.freezeKanbans({
      barcodes: selectedKanbans.value.map(kanbanOptionValue),
      frozen: props.frozen,
      remark: form.remark,
    })
    emit('completed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PageModal :open="open" wide @close="emit('close')">
    <section class="panel ops-modal-panel batch-ops-panel">
      <div class="section-head compact-head">
        <h3>{{ title }}</h3>
        <div class="action-row">
          <input v-model="form.remark" class="ops-remark-input" placeholder="原因 / 备注" />
          <button :disabled="!selectedKanbans.length || submitting" @click="submit">
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
