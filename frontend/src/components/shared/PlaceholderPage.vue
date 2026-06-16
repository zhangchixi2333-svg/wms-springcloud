<!-- 本文件实现 PlaceholderPage 共享组件。 -->
<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <section class="panel placeholder">
    <div>
      <p class="eyebrow">规划中</p>
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </div>
  </section>
</template>

<style scoped>
.placeholder {
  min-height: 240px;
  display: grid;
  place-items: center;
  text-align: center;
}

.eyebrow {
  margin: 0 0 10px;
  color: var(--accent);
  text-transform: uppercase;
  font-size: 12px;
}

h3 {
  margin: 0;
  font-size: 26px;
}

p {
  color: var(--muted);
}
</style>
