<script setup lang="ts">
import { computed } from 'vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const totalStockQty = computed(() => props.model.state.inventory.reduce((sum, row) => sum + Number(row.qty), 0))
const inStockKanbans = computed(() => props.model.state.kanbans.filter((item) => item.status === 'IN_STOCK').length)
const pendingKanbans = computed(() => props.model.state.kanbans.filter((item) => item.status === 'CREATED').length)
</script>

<template>
  <section class="stack">
    <div class="stats-grid">
      <article class="stat">
        <span>供应商</span>
        <strong>{{ model.state.suppliers.length }}</strong>
      </article>
      <article class="stat">
        <span>客户</span>
        <strong>{{ model.state.customers.length }}</strong>
      </article>
      <article class="stat">
        <span>零件</span>
        <strong>{{ model.state.parts.length }}</strong>
      </article>
      <article class="stat">
        <span>在库看板</span>
        <strong>{{ inStockKanbans }}</strong>
      </article>
      <article class="stat">
        <span>待入库看板</span>
        <strong>{{ pendingKanbans }}</strong>
      </article>
      <article class="stat">
        <span>库存总量</span>
        <strong>{{ totalStockQty }}</strong>
      </article>
    </div>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>最近库存变化</h3>
          <p>首页优先展示当前库存和最近流水。</p>
        </div>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>零件</th>
            <th>库位</th>
            <th>数量</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in model.state.inventory.slice(0, 8)" :key="row.id">
            <td>{{ row.partCode }} | {{ row.partName }}</td>
            <td>{{ row.locationCode }}</td>
            <td>{{ row.qty }}</td>
            <td>{{ new Date(row.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>
