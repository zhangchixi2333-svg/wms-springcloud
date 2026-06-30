<!-- 本文件实现器具管理页面，按仓库性质区分普通器具和转包器具。 -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { formatEquipmentType, formatStatus, formatWarehouseType } from '../../../app/displayText'
import { warehouseOptions, zoneOptions } from '../../../app/optionHelpers'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import type { Equipment, Location, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode: 'normal' | 'repack' }>()

const createOpen = ref(false)
const page = ref(1)
const pageSize = ref(20)

const filters = reactive({
  keyword: '',
  equipmentType: '',
  warehouseName: '',
  zoneName: '',
  status: '',
})

const form = reactive({
  equipmentCode: '',
  equipmentName: '',
  equipmentType: 'BOX',
  equipmentModel: '',
  capacity: 1,
  warehouseName: '',
  zoneName: '',
  status: 'ENABLED',
})

const expectedWarehouseType = computed(() => (props.mode === 'repack' ? 'THIRD_PARTY' : 'OWN'))
const titleText = computed(() => (props.mode === 'repack' ? '转包器具' : '普通器具'))
const availableLocations = computed(() =>
  props.model.state.locations.filter((item) => item.warehouseType === expectedWarehouseType.value),
)
const equipmentWarehouseOptions = computed(() => warehouseOptions(availableLocations.value))
const filterZoneOptions = computed(() => zoneOptions(availableLocations.value, filters.warehouseName))
const formZoneOptions = computed(() => zoneOptions(availableLocations.value, form.warehouseName))

const rows = computed(() =>
  props.model.state.equipment
    .filter((item) => locationForEquipment(item)?.warehouseType === expectedWarehouseType.value)
    .filter((item) => !filters.keyword || `${item.equipmentCode} ${item.equipmentName} ${item.equipmentModel}`.toLowerCase().includes(filters.keyword.toLowerCase()))
    .filter((item) => !filters.equipmentType || item.equipmentType === filters.equipmentType)
    .filter((item) => !filters.warehouseName || item.warehouseName === filters.warehouseName)
    .filter((item) => !filters.zoneName || item.zoneName === filters.zoneName)
    .filter((item) => !filters.status || item.status === filters.status),
)

const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / pageSize.value)))
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

watch(
  () => filters.warehouseName,
  () => {
    filters.zoneName = ''
    page.value = 1
  },
)

watch(rows, () => {
  if (page.value > totalPages.value) page.value = totalPages.value
})

function locationForEquipment(item: Pick<Equipment, 'warehouseName' | 'zoneName'>): Location | undefined {
  return props.model.state.locations.find((location) =>
    location.warehouseName === item.warehouseName && location.zoneName === item.zoneName,
  )
}

function resetFilters() {
  filters.keyword = ''
  filters.equipmentType = ''
  filters.warehouseName = ''
  filters.zoneName = ''
  filters.status = ''
  page.value = 1
}

function resetForm() {
  const firstLocation = availableLocations.value[0]
  form.equipmentCode = ''
  form.equipmentName = ''
  form.equipmentType = 'BOX'
  form.equipmentModel = ''
  form.capacity = 1
  form.warehouseName = firstLocation?.warehouseName ?? ''
  form.zoneName = firstLocation?.zoneName ?? ''
  form.status = 'ENABLED'
}

function openCreate() {
  resetForm()
  createOpen.value = true
}

async function submit() {
  await props.model.actions.createEquipment({ ...form })
  resetForm()
  createOpen.value = false
}

function closeCreateModal() {
  createOpen.value = false
}

function handleFormWarehouseChange() {
  form.zoneName = formZoneOptions.value[0] ?? ''
}
</script>

<template>
  <section class="stack">
      <section class="panel equipment-filter-panel">
        <div class="equipment-filter-line">
          <h3>{{ titleText }}</h3>
          <div class="equipment-filter-row">
            <input v-model="filters.keyword" placeholder="编码 / 名称 / 型号" />
            <select v-model="filters.equipmentType">
              <option value="">全部类型</option>
              <option value="BOX">周转箱</option>
              <option value="RACK">料架</option>
            </select>
            <select v-model="filters.warehouseName">
              <option value="">全部仓库</option>
              <option v-for="warehouse in equipmentWarehouseOptions" :key="warehouse" :value="warehouse">
                {{ warehouse }}
              </option>
            </select>
            <select v-model="filters.zoneName">
              <option value="">全部库区</option>
              <option v-for="zone in filterZoneOptions" :key="zone" :value="zone">
                {{ zone }}
              </option>
            </select>
            <select v-model="filters.status">
              <option value="">全部状态</option>
              <option value="ENABLED">启用</option>
              <option value="DISABLED">停用</option>
            </select>
            <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
          </div>
          <div class="action-row equipment-actions">
            <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="rows.length" />
            <button @click="openCreate">新建器具</button>
          </div>
        </div>
      </section>

      <section class="panel table-scroll">
        <table class="table equipment-table">
          <thead>
            <tr>
              <th>器具编码</th>
              <th>名称</th>
              <th>类型</th>
              <th>型号</th>
              <th>容量</th>
              <th>仓库</th>
              <th>库区</th>
              <th>性质</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in pageRows" :key="item.id" :class="{ 'tone-a': index % 2 === 0, 'tone-b': index % 2 === 1 }">
              <td class="mono">{{ item.equipmentCode }}</td>
              <td>{{ item.equipmentName }}</td>
              <td>{{ formatEquipmentType(item.equipmentType) }}</td>
              <td>{{ item.equipmentModel }}</td>
              <td>{{ Number(item.capacity).toFixed(3) }}</td>
              <td>{{ item.warehouseName || '-' }}</td>
              <td>{{ item.zoneName || '-' }}</td>
              <td>{{ formatWarehouseType(locationForEquipment(item)?.warehouseType) }}</td>
              <td>{{ formatStatus(item.status) }}</td>
            </tr>
            <tr v-if="!pageRows.length">
              <td colspan="9" class="empty-cell">没有匹配的器具</td>
            </tr>
          </tbody>
        </table>
      </section>

    <PageModal :open="createOpen" @close="closeCreateModal">
      <section class="panel equipment-create-panel">
        <div class="section-head compact-head">
          <div>
            <h3>新建{{ titleText }}</h3>
          </div>
          <div class="property-pill">{{ formatWarehouseType(expectedWarehouseType) }}</div>
        </div>
        <div class="equipment-create-grid">
          <input v-model="form.equipmentCode" placeholder="器具编码" />
          <input v-model="form.equipmentName" placeholder="器具名称" />
          <select v-model="form.equipmentType">
            <option value="BOX">周转箱</option>
            <option value="RACK">料架</option>
          </select>
          <input v-model="form.equipmentModel" placeholder="器具型号" />
          <input v-model.number="form.capacity" type="number" min="0.001" step="0.001" placeholder="容量" />
          <select v-model="form.warehouseName" @change="handleFormWarehouseChange">
            <option value="">选择仓库</option>
            <option v-for="warehouse in equipmentWarehouseOptions" :key="warehouse" :value="warehouse">
              {{ warehouse }}
            </option>
          </select>
          <select v-model="form.zoneName">
            <option value="">选择库区</option>
            <option v-for="zone in formZoneOptions" :key="zone" :value="zone">
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
        </div>
      </section>
    </PageModal>
  </section>
</template>

<style scoped>
.equipment-filter-panel {
  padding: 8px 10px;
}

.equipment-filter-line {
  display: grid;
  grid-template-columns: auto minmax(580px, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.equipment-filter-line h3,
.compact-head h3 {
  margin: 0;
  white-space: nowrap;
  font-size: 16px;
}

.equipment-filter-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.35fr) 88px minmax(118px, 0.9fr) minmax(100px, 0.8fr) 88px 52px;
  gap: 5px;
  align-items: center;
}

.equipment-filter-row input,
.equipment-filter-row select,
.compact-filter-button {
  min-height: 34px;
}

.equipment-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.equipment-actions button {
  min-height: 34px;
  padding: 0 10px;
}

.table-scroll {
  overflow-x: auto;
}

.equipment-table {
  min-width: 980px;
  table-layout: fixed;
}

.equipment-table th,
.equipment-table td {
  padding: 7px 9px;
  vertical-align: middle;
}

.equipment-table th:nth-child(1),
.equipment-table td:nth-child(1) {
  width: 128px;
}

.equipment-table th:nth-child(3),
.equipment-table td:nth-child(3),
.equipment-table th:nth-child(5),
.equipment-table td:nth-child(5),
.equipment-table th:nth-child(9),
.equipment-table td:nth-child(9) {
  width: 88px;
}

.equipment-table tr.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.equipment-table tr.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.equipment-table tbody tr:hover {
  background: rgba(37, 99, 235, 0.11);
}

.equipment-create-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.compact-head {
  align-items: center;
  margin-bottom: 0;
}

.property-pill {
  border: 1px solid rgba(14, 165, 233, 0.28);
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(14, 165, 233, 0.1);
  color: #075985;
  font-size: 12px;
  font-weight: 700;
}

.equipment-create-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr));
  gap: 8px;
}

.equipment-create-grid input,
.equipment-create-grid select {
  min-height: 34px;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

@media (max-width: 1180px) {
  .equipment-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .equipment-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .equipment-filter-row,
  .equipment-create-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
