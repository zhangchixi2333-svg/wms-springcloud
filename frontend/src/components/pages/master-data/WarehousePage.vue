<!-- 本文件实现 WarehousePage 页面组件。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const viewMode = ref<'query' | 'create'>('query')
const workModes = [
  { key: 'query', label: '查询仓库/库区' },
  { key: 'create', label: '新建仓库/库区' },
]

const filters = reactive({
  warehouseName: '',
  zoneName: '',
  locationCode: '',
  warehouseType: '',
})

const form = reactive({
  locationCode: '',
  locationName: '',
  warehouseName: '',
  zoneName: '',
  warehouseType: 'OWN' as 'OWN' | 'THIRD_PARTY',
})

const rows = computed(() =>
  props.model.state.locations.filter((item) => {
    const warehouseMatch = !filters.warehouseName || item.warehouseName.toLowerCase().includes(filters.warehouseName.toLowerCase())
    const zoneMatch = !filters.zoneName || item.zoneName.toLowerCase().includes(filters.zoneName.toLowerCase())
    const codeMatch = !filters.locationCode || item.locationCode.toLowerCase().includes(filters.locationCode.toLowerCase())
    const typeMatch = !filters.warehouseType || item.warehouseType === filters.warehouseType
    return warehouseMatch && zoneMatch && codeMatch && typeMatch
  }),
)

function resetFilters() {
  filters.warehouseName = ''
  filters.zoneName = ''
  filters.locationCode = ''
  filters.warehouseType = ''
}

async function submit() {
  await props.model.actions.createLocation(form)
  form.locationCode = ''
  form.locationName = ''
  form.warehouseName = ''
  form.zoneName = ''
  form.warehouseType = 'OWN'
  viewMode.value = 'query'
}
</script>

<template>
  <WorkModePage v-model="viewMode" :modes="workModes">
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>仓库 / 库区筛选</h3>
            <p>按仓库、库区和库位编码筛选管理点位。</p>
          </div>
        </div>
        <div class="form-grid four">
          <input v-model="filters.warehouseName" placeholder="仓库" />
          <input v-model="filters.zoneName" placeholder="库区" />
          <input v-model="filters.locationCode" placeholder="库位编码" />
          <select v-model="filters.warehouseType">
            <option value="">全部性质</option>
            <option value="OWN">自己仓库</option>
            <option value="THIRD_PARTY">第三方仓库</option>
          </select>
        </div>
        <div class="footer-actions compact">
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>仓库</th>
              <th>库区</th>
              <th>性质</th>
              <th>库位编码</th>
              <th>库位名称</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.warehouseName }}</td>
              <td>{{ item.zoneName }}</td>
              <td>{{ item.warehouseType === 'THIRD_PARTY' ? '第三方仓库' : '自己仓库' }}</td>
              <td>{{ item.locationCode }}</td>
              <td>{{ item.locationName }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else class="panel">
      <div class="section-head">
        <div>
          <h3>新建仓库 / 库区</h3>
          <p>这里保存的是一线业务直接使用的仓库、库区和库位信息。</p>
        </div>
      </div>
      <div class="form-grid four">
        <input v-model="form.warehouseName" placeholder="仓库" />
        <input v-model="form.zoneName" placeholder="库区" />
        <input v-model="form.locationCode" placeholder="库位编码" />
        <input v-model="form.locationName" placeholder="库位名称" />
        <select v-model="form.warehouseType">
          <option value="OWN">自己仓库</option>
          <option value="THIRD_PARTY">第三方仓库</option>
        </select>
      </div>
      <div class="footer-actions">
        <button @click="submit">保存仓库/库区</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>
  </WorkModePage>
</template>

<style scoped>
</style>
