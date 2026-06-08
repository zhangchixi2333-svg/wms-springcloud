<script setup lang="ts">
import { computed, reactive } from 'vue'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const filters = reactive({
  filterType: 'warehouse',
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

function resetFilters() {
  filters.filterType = 'warehouse'
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
          <h3>库存看板</h3>
          <p>先选筛选类型，再补充仓库、库区、物料和供应商等详细条件。</p>
        </div>
      </div>
      <div class="form-grid five">
        <select v-model="filters.filterType">
          <option value="warehouse">仓库</option>
          <option value="zone">库区</option>
          <option value="material">物料</option>
        </select>
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
</style>
