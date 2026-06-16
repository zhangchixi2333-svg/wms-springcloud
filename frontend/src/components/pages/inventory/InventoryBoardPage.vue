<!-- 本文件实现库存监控页面，提供筛选、汇总和明细联动查看。 -->
<script setup lang="ts">
import { computed, reactive } from 'vue'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const filters = reactive({
  warehouseName: '',
  zoneName: '',
  materialKeyword: '',
  supplierId: 0,
})

const inventoryWarehouseOptions = computed(() => warehouseOptions(props.model.state.locations))
const inventoryZoneOptions = computed(() => zoneOptions(props.model.state.locations, filters.warehouseName))

const rows = computed(() =>
  props.model.state.inventory.filter((row) => {
    const warehouseMatch = !filters.warehouseName || row.warehouseName === filters.warehouseName
    const zoneMatch = !filters.zoneName || row.zoneName === filters.zoneName
    const materialMatch =
      !filters.materialKeyword ||
      `${row.partCode} ${row.partName}`.toLowerCase().includes(filters.materialKeyword.toLowerCase())
    const supplierMatch =
      !filters.supplierId ||
      props.model.state.suppliers.find((item) => item.id === filters.supplierId)?.supplierName === row.supplierName
    return warehouseMatch && zoneMatch && materialMatch && supplierMatch
  }),
)

const summary = computed(() => ({
  rowCount: rows.value.length,
  totalQty: rows.value.reduce((total, item) => total + Number(item.qty), 0),
  warehouseCount: new Set(rows.value.map((item) => item.warehouseName)).size,
  partCount: new Set(rows.value.map((item) => item.partCode)).size,
}))

function resetFilters() {
  filters.warehouseName = ''
  filters.zoneName = ''
  filters.materialKeyword = ''
  filters.supplierId = 0
}
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>库存监控</h3>
          <p>按仓库、库区、物料和供应商查看当前库存，支持总量与明细联动。</p>
        </div>
      </div>
      <div class="form-grid four">
        <select v-model="filters.warehouseName" @change="filters.zoneName = ''">
          <option value="">全部仓库</option>
          <option v-for="warehouse in inventoryWarehouseOptions" :key="warehouse" :value="warehouse">
            {{ warehouse }}
          </option>
        </select>
        <select v-model="filters.zoneName">
          <option value="">全部库区</option>
          <option v-for="zone in inventoryZoneOptions" :key="zone" :value="zone">
            {{ zone }}
          </option>
        </select>
        <input v-model="filters.materialKeyword" placeholder="物料 / 零件号" />
        <select v-model.number="filters.supplierId">
          <option :value="0">全部供应商</option>
          <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
            {{ item.supplierCode }} | {{ item.supplierName }}
          </option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="secondary-button" @click="resetFilters">重置筛选</button>
      </div>
    </section>

    <section class="summary-grid">
      <div class="panel summary-card">
        <span class="summary-label">库存记录数</span>
        <strong>{{ summary.rowCount }}</strong>
      </div>
      <div class="panel summary-card">
        <span class="summary-label">库存总量</span>
        <strong>{{ summary.totalQty }}</strong>
      </div>
      <div class="panel summary-card">
        <span class="summary-label">涉及仓库</span>
        <strong>{{ summary.warehouseCount }}</strong>
      </div>
      <div class="panel summary-card">
        <span class="summary-label">涉及零件</span>
        <strong>{{ summary.partCount }}</strong>
      </div>
    </section>

    <section class="panel">
      <table class="table">
        <thead>
          <tr>
            <th>零件编码</th>
            <th>零件名称</th>
            <th>供应商</th>
            <th>仓库</th>
            <th>库区</th>
            <th>库位</th>
            <th>数量</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.partCode }}</td>
            <td>{{ row.partName }}</td>
            <td>{{ row.supplierName }}</td>
            <td>{{ row.warehouseName }}</td>
            <td>{{ row.zoneName }}</td>
            <td>{{ row.locationCode }}</td>
            <td>{{ row.qty }}</td>
            <td>{{ new Date(row.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.filter-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: grid;
  gap: 8px;
}

.summary-label {
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
