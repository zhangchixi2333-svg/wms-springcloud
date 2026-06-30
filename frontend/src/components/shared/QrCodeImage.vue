<!-- 本文件实现 QrCodeImage 共享组件。 -->
<script setup lang="ts">
import QRCode from 'qrcode'
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{
  text: string
  size?: number
}>()

const src = ref('')

async function renderQr() {
  src.value = props.text
    ? await QRCode.toDataURL(props.text, {
        width: props.size ?? 180,
        margin: 1,
        color: {
          dark: '#112031',
          light: '#ffffff',
        },
      })
    : ''
}

watch(() => [props.text, props.size] as const, renderQr, { immediate: true })
onMounted(renderQr)
</script>

<template>
  <div class="qr-wrap">
    <img v-if="src" :src="src" alt="QR Code" class="qr-image" :style="{ width: `${size ?? 180}px` }" />
  </div>
</template>

<style scoped>
.qr-wrap {
  width: fit-content;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.qr-image {
  display: block;
  max-width: 100%;
  aspect-ratio: 1;
}
</style>
