<!-- 本文件提供通用页面浮窗容器，用于让创建、编辑、打印、扫码等操作停留在当前页面上完成。 -->
<script setup lang="ts">
withDefaults(
  defineProps<{
    open: boolean
    wide?: boolean
    xl?: boolean
    printMode?: boolean
    closeLabel?: string
  }>(),
  {
    wide: false,
    xl: false,
    printMode: false,
    closeLabel: '关闭',
  },
)

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <teleport to="body">
    <div v-if="open" class="page-modal-backdrop" @click.self="emit('close')">
      <div class="page-modal-frame" :class="{ wide, xl, 'print-mode': printMode }">
        <button class="page-modal-close" type="button" :aria-label="closeLabel" @click="emit('close')">×</button>
        <slot />
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.page-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.page-modal-frame {
  position: relative;
  width: min(1180px, 96vw);
  min-width: 0;
  max-height: 90vh;
  overflow: auto;
  overscroll-behavior: contain;
}

.page-modal-frame.wide {
  width: min(1320px, 98vw);
  max-height: 92vh;
}

.page-modal-frame.xl {
  width: min(1560px, 99vw);
  max-height: 94vh;
}

.page-modal-frame.print-mode {
  max-height: calc(100vh - 24px);
}

.page-modal-close {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  width: 34px;
  min-height: 34px;
  padding: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  border: 0;
  font-size: 22px;
  line-height: 1;
}

.page-modal-frame :deep(> .panel),
.page-modal-frame :deep(> .stack) {
  margin: 0;
  max-width: 100%;
  min-width: 0;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.28);
}

.page-modal-frame :deep(.section-head) {
  padding-right: 42px;
}

.page-modal-frame :deep(.section-head > div),
.page-modal-frame :deep(.table-scroll),
.page-modal-frame :deep(label),
.page-modal-frame :deep(dl),
.page-modal-frame :deep(dd) {
  min-width: 0;
}

.page-modal-frame :deep(input),
.page-modal-frame :deep(select),
.page-modal-frame :deep(textarea) {
  max-width: 100%;
}

.page-modal-frame :deep(.table-scroll),
.page-modal-frame :deep(.aligned-table-shell) {
  max-width: 100%;
  min-width: 0;
  overflow: auto;
}

.page-modal-frame :deep(td),
.page-modal-frame :deep(th),
.page-modal-frame :deep(dd),
.page-modal-frame :deep(.mono) {
  overflow-wrap: anywhere;
}

@media (max-width: 1100px) {
  .page-modal-backdrop {
    padding: 10px;
  }

  .page-modal-frame,
  .page-modal-frame.wide,
  .page-modal-frame.xl {
    width: min(100%, calc(100vw - 20px));
    max-height: calc(100vh - 20px);
  }
}

@media print {
  :global(body.printing-operation-modal #app) {
    display: none !important;
  }

  .page-modal-backdrop {
    position: static;
    display: block;
    padding: 0;
    background: transparent;
  }

  .page-modal-frame,
  .page-modal-frame.wide,
  .page-modal-frame.xl {
    width: 100%;
    max-height: none;
    overflow: visible;
  }

  .page-modal-close {
    display: none !important;
  }

  .page-modal-frame :deep(> .panel),
  .page-modal-frame :deep(> .stack) {
    box-shadow: none;
  }
}
</style>
