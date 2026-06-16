<!-- 本文件实现出入库流水历史页，支持按业务类型、单号、条码、零件和库位过滤。 -->
<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const filters = reactive({
  businessType: '',
  businessNo: '',
  barcode: '',
  partCode: '',
  locationCode: '',
})

const rows = computed(() =>
  props.model.state.transactions.filter((row) => {
    const typeMatch = !filters.businessType || row.businessType === filters.businessType
    const noMatch = !filters.businessNo || row.businessNo.toLowerCase().includes(filters.businessNo.toLowerCase())
    const barcodeMatch = !filters.barcode || row.barcode.toLowerCase().includes(filters.barcode.toLowerCase())
    const partMatch = !filters.partCode || row.partCode.toLowerCase().includes(filters.partCode.toLowerCase())
    const locationMatch = !filters.locationCode || row.locationCode.toLowerCase().includes(filters.locationCode.toLowerCase())
    return typeMatch && noMatch && barcodeMatch && partMatch && locationMatch
  }),
)

const businessTypes = computed(() => Array.from(new Set(props.model.state.transactions.map((item) => item.businessType))).sort())

function resetFilters() {
  filters.businessType = ''
  filters.businessNo = ''
  filters.barcode = ''
  filters.partCode = ''
  filters.locationCode = ''
}
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>出入库历史</h3>
          <p>每次入库、出库、转包、冻结、盘点调整都会在这里留下流水，可按多条件查询。</p>
        </div>
      </div>
      <div class="form-grid five">
        <select v-model="filters.businessType">
          <option value="">全部业务类型</option>
          <option v-for="item in businessTypes" :key="item" :value="item">{{ item }}</option>
        </select>
        <input v-model="filters.businessNo" placeholder="业务单号 / 看板号" />
        <input v-model="filters.barcode" placeholder="条码 / 二维码内容" />
        <input v-model="filters.partCode" placeholder="零件编码" />
        <input v-model="filters.locationCode" placeholder="库位编码" />
      </div>
      <div class="filter-actions">
        <button class="secondary-button" @click="resetFilters">重置筛选</button>
      </div>
    </section>

    <section class="panel">
      <table class="table">
        <thead>
          <tr>
            <th>流水号</th>
            <th>类型</th>
            <th>业务单号</th>
            <th>条码</th>
            <th>零件</th>
            <th>库位</th>
            <th>数量变化</th>
            <th>备注</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td class="mono">{{ row.transactionNo }}</td>
            <td>{{ row.businessType }}</td>
            <td>{{ row.businessNo }}</td>
            <td class="mono">{{ row.barcode }}</td>
            <td>{{ row.partCode }}</td>
            <td>{{ row.locationCode }}</td>
            <td :class="{ outbound: Number(row.qtyChange) < 0, inbound: Number(row.qtyChange) > 0 }">{{ row.qtyChange }}</td>
            <td>{{ row.remark }}</td>
            <td>{{ new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
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
