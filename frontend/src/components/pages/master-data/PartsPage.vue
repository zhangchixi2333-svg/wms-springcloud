<!-- 本文件实现零件主数据页面，统一维护供应商、默认器具和每箱数量。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const viewMode = ref<'query' | 'create'>('query')
const workModes = [
  { key: 'query', label: '查询零件' },
  { key: 'create', label: '新建零件' },
]

const filters = reactive({
  keyword: '',
  unit: '',
  supplierId: 0,
})

const form = reactive({
  partCode: '',
  partName: '',
  unit: 'PCS',
  supplierId: 0,
  defaultEquipmentCode: '',
  defaultUnitPerBox: 1,
})

const rows = computed(() =>
  props.model.state.parts.filter((item) => {
    const keywordMatch = !filters.keyword || `${item.partCode} ${item.partName}`.toLowerCase().includes(filters.keyword.toLowerCase())
    const unitMatch = !filters.unit || item.unit.toLowerCase().includes(filters.unit.toLowerCase())
    const supplierMatch = !filters.supplierId || item.supplierId === filters.supplierId
    return keywordMatch && unitMatch && supplierMatch
  }),
)

function resetFilters() {
  filters.keyword = ''
  filters.unit = ''
  filters.supplierId = 0
}

async function submit() {
  await props.model.actions.createPart({
    partCode: form.partCode,
    partName: form.partName,
    unit: form.unit,
    supplierId: form.supplierId || null,
    defaultEquipmentCode: form.defaultEquipmentCode || null,
    defaultUnitPerBox: form.defaultUnitPerBox || null,
  })
  form.partCode = ''
  form.partName = ''
  form.unit = 'PCS'
  form.supplierId = 0
  form.defaultEquipmentCode = ''
  form.defaultUnitPerBox = 1
  viewMode.value = 'query'
}

function supplierName(supplierId: number | null) {
  return props.model.state.suppliers.find((item) => item.id === supplierId)?.supplierName || '-'
}
</script>

<template>
  <WorkModePage v-model="viewMode" :modes="workModes" hint="这里维护零件与供应商、器具编码、每箱数量的默认绑定关系。">
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>零件筛选</h3>
            <p>可按编码、名称、单位和供应商快速查询，入库时会自动带出默认每箱数量与器具编码。</p>
          </div>
        </div>
        <div class="form-grid four">
          <input v-model="filters.keyword" placeholder="零件编码 / 名称" />
          <input v-model="filters.unit" placeholder="单位" />
          <select v-model.number="filters.supplierId">
            <option :value="0">全部供应商</option>
            <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
              {{ item.supplierCode }} | {{ item.supplierName }}
            </option>
          </select>
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>单位</th>
              <th>供应商</th>
              <th>默认器具编码</th>
              <th>默认每箱数量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.partCode }}</td>
              <td>{{ item.partName }}</td>
              <td>{{ item.unit }}</td>
              <td>{{ supplierName(item.supplierId) }}</td>
              <td>{{ item.defaultEquipmentCode || '-' }}</td>
              <td>{{ item.defaultUnitPerBox || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else class="panel">
      <div class="section-head">
        <div>
          <h3>新建零件</h3>
          <p>创建后会被入库批量选件、出库计划、箱级看板生成与库存统计共同使用。</p>
        </div>
      </div>
      <div class="form-grid three">
        <input v-model="form.partCode" placeholder="零件编码" />
        <input v-model="form.partName" placeholder="零件名称" />
        <input v-model="form.unit" placeholder="单位" />
        <select v-model.number="form.supplierId">
          <option :value="0">不绑定供应商</option>
          <option v-for="item in model.state.suppliers" :key="item.id" :value="item.id">
            {{ item.supplierCode }} | {{ item.supplierName }}
          </option>
        </select>
        <select v-model="form.defaultEquipmentCode">
          <option value="">不绑定默认器具</option>
          <option v-for="item in model.state.equipment" :key="item.id" :value="item.equipmentCode">
            {{ item.equipmentCode }} | {{ item.equipmentName }} | {{ item.equipmentModel }}
          </option>
        </select>
        <input v-model.number="form.defaultUnitPerBox" type="number" min="0.001" step="0.001" placeholder="每箱数量" />
      </div>
      <div class="footer-actions">
        <button @click="submit">保存零件</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>
  </WorkModePage>
</template>
