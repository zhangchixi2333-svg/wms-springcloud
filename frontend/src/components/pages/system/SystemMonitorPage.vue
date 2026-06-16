<!-- 本文件实现 SystemMonitorPage 页面组件。 -->
<script setup lang="ts">
import { computed } from 'vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const metrics = computed(() => [
  { label: '供应商', value: props.model.state.suppliers.length },
  { label: '客户', value: props.model.state.customers.length },
  { label: '零件', value: props.model.state.parts.length },
  { label: '库位', value: props.model.state.locations.length },
  { label: '入库单', value: props.model.state.inboundOrders.length },
  { label: '出库单', value: props.model.state.outboundOrders.length },
  { label: '看板', value: props.model.state.kanbans.length },
  { label: '库存行', value: props.model.state.inventory.length },
])

const statusRows = computed(() => {
  const map = new Map<string, number>()
  props.model.state.kanbans.forEach((item) => map.set(item.status, (map.get(item.status) ?? 0) + 1))
  return Array.from(map.entries()).map(([status, count]) => ({ status, count }))
})
</script>

<template>
  <section class="stack">
    <section class="stats-grid monitor-grid">
      <article v-for="item in metrics" :key="item.label" class="stat">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>看板状态分布</h3>
          <p>用于快速检查待入库、在库、封存、转包和出库状态。</p>
        </div>
        <button class="secondary-button" @click="model.actions.refreshAll">刷新</button>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>状态</th>
            <th>数量</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in statusRows" :key="row.status">
            <td>{{ row.status }}</td>
            <td>{{ row.count }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>最近流水</h3>
          <p>展示最近 10 条库存变更记录。</p>
        </div>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>流水号</th>
            <th>类型</th>
            <th>条码</th>
            <th>数量变化</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in model.state.transactions.slice(0, 10)" :key="row.id">
            <td class="mono">{{ row.transactionNo }}</td>
            <td>{{ row.businessType }}</td>
            <td class="mono">{{ row.barcode }}</td>
            <td :class="{ inbound: Number(row.qtyChange) > 0, outbound: Number(row.qtyChange) < 0 }">{{ row.qtyChange }}</td>
            <td>{{ new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.monitor-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

@media (max-width: 1180px) {
  .monitor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
