<script setup lang="ts">
export type WorkMode = {
  key: string
  label: string
  disabled?: boolean
}

defineProps<{
  modelValue: string
  modes: WorkMode[]
  hint?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [value: string]
}>()

function selectMode(mode: WorkMode) {
  if (mode.disabled) return
  emit('update:modelValue', mode.key)
  emit('select', mode.key)
}
</script>

<template>
  <section class="stack">
    <section class="panel toolbar-panel">
      <div class="toolbar">
        <div class="segmented">
          <button
            v-for="mode in modes"
            :key="mode.key"
            :class="{ active: modelValue === mode.key }"
            :disabled="mode.disabled"
            @click="selectMode(mode)"
          >
            {{ mode.label }}
          </button>
        </div>
        <span v-if="hint" class="hint">{{ hint }}</span>
      </div>
    </section>

    <slot />
  </section>
</template>
