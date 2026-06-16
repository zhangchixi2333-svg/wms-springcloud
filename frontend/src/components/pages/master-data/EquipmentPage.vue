<!-- 本文件实现 EquipmentPage 页面组件。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { formatEquipmentType, formatStatus } from '../../../app/displayText'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode: 'normal' | 'repack' }>()

const viewMode = ref<'query' | 'create'>('query')
const workModes = [
  { key: 'query', label: '查询器具' },
  { key: 'create', label: '新建器具' },
]
const filters = reactive({
  keyword: '',
  warehouseName: '',
  status: '',
})

const form = reactive({
  equipmentCode: '',
  equipmentName: '',
  equipmentModel: '',
  capacity: 1,
  warehouseName: '',
  zoneName: '',
  status: 'ENABLED',
})

const equipmentType = computed(() => (props.mode === 'repack' ? 'REPACK' : 'NORMAL'))
const equipmentWarehouseOptions = computed(() => warehouseOptions(props.model.state.locations))

const rows = computed(() =>
  props.model.state.equipment
    .filter((item) => item.equipmentType === equipmentType.value)
    .filter((item) => !filters.keyword || `${item.equipmentCode} ${item.equipmentName} ${item.equipmentModel}`.toLowerCase().includes(filters.keyword.toLowerCase()))
    .filter((item) => !filters.warehouseName || item.warehouseName === filters.warehouseName)
    .filter((item) => !filters.status || item.status === filters.status),
)

function resetFilters() {
  filters.keyword = ''
  filters.warehouseName = ''
  filters.status = ''
}

function equipmentZoneOptions(warehouseName: string) {
  return zoneOptions(props.model.state.locations, warehouseName)
}

async function submit() {
  await props.model.actions.createEquipment({
    ...form,
    equipmentType: equipmentType.value,
  })
  form.equipmentCode = ''
  form.equipmentName = ''
  form.equipmentModel = ''
  form.capacity = 1
  form.warehouseName = ''
  form.zoneName = ''
  form.status = 'ENABLED'
  viewMode.value = 'query'
}
</script>

<template>
  <WorkModePage v-model="viewMode" :modes="workModes">
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>{{ mode === 'repack' ? '转包器具' : '普通器具' }}</h3>
            <p>支持按编码、名称、型号、仓库和状态快速筛选。</p>
          </div>
        </div>
        <div class="form-grid four">
          <input v-model="filters.keyword" placeholder="器具编码 / 名称 / 型号" />
          <select v-model="filters.warehouseName">
            <option value="">全部仓库</option>
            <option v-for="warehouse in equipmentWarehouseOptions" :key="warehouse" :value="warehouse">
              {{ warehouse }}
            </option>
          </select>
          <select v-model="filters.status">
            <option value="">全部状态</option>
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>器具编码</th>
              <th>名称</th>
              <th>类型</th>
              <th>型号</th>
              <th>容量</th>
              <th>仓库</th>
              <th>库区</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.equipmentCode }}</td>
              <td>{{ item.equipmentName }}</td>
              <td>{{ formatEquipmentType(item.equipmentType) }}</td>
              <td>{{ item.equipmentModel }}</td>
              <td>{{ item.capacity }}</td>
              <td>{{ item.warehouseName || '-' }}</td>
              <td>{{ item.zoneName || '-' }}</td>
              <td>{{ formatStatus(item.status) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else class="panel">
      <div class="section-head">
        <div>
          <h3>{{ mode === 'repack' ? '新建转包器具' : '新建普通器具' }}</h3>
          <p>器具会同步用于入库明细、看板打印和库存追溯。</p>
        </div>
      </div>
      <div class="form-grid four">
        <input v-model="form.equipmentCode" placeholder="器具编码" />
        <input v-model="form.equipmentName" placeholder="器具名称" />
        <input v-model="form.equipmentModel" placeholder="器具型号" />
        <input v-model.number="form.capacity" type="number" min="0.001" step="0.001" placeholder="容量" />
        <select v-model="form.warehouseName" @change="form.zoneName = ''">
          <option value="">选择仓库</option>
          <option v-for="warehouse in equipmentWarehouseOptions" :key="warehouse" :value="warehouse">
            {{ warehouse }}
          </option>
        </select>
        <select v-model="form.zoneName">
          <option value="">选择库区</option>
          <option v-for="zone in equipmentZoneOptions(form.warehouseName)" :key="zone" :value="zone">
            {{ zone }}
          </option>
        </select>
        <select v-model="form.status">
          <option value="ENABLED">启用</option>
          <option value="DISABLED">停用</option>
        </select>
      </div>
      <div class="footer-actions">
        <button @click="submit">保存器具</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>
  </WorkModePage>
</template>

<style scoped>
</style>
