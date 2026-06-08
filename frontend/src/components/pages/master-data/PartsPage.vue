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
})

const form = reactive({
  partCode: '',
  partName: '',
  unit: 'PCS',
})

const rows = computed(() =>
  props.model.state.parts.filter((item) => {
    const keywordMatch = !filters.keyword || `${item.partCode} ${item.partName}`.toLowerCase().includes(filters.keyword.toLowerCase())
    const unitMatch = !filters.unit || item.unit.toLowerCase().includes(filters.unit.toLowerCase())
    return keywordMatch && unitMatch
  }),
)

function resetFilters() {
  filters.keyword = ''
  filters.unit = ''
}

async function submit() {
  await props.model.actions.createPart(form)
  form.partCode = ''
  form.partName = ''
  form.unit = 'PCS'
  viewMode.value = 'query'
}
</script>

<template>
  <WorkModePage v-model="viewMode" :modes="workModes">
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>零件筛选</h3>
            <p>支持按零件编码、名称和单位快速过滤。</p>
          </div>
        </div>
        <div class="form-grid three">
          <input v-model="filters.keyword" placeholder="零件编码 / 名称" />
          <input v-model="filters.unit" placeholder="单位" />
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
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.partCode }}</td>
              <td>{{ item.partName }}</td>
              <td>{{ item.unit }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else class="panel">
      <div class="section-head">
        <div>
          <h3>新建零件</h3>
          <p>新建后会同步用于入库单、看板和库存看板。</p>
        </div>
      </div>
      <div class="form-grid three">
        <input v-model="form.partCode" placeholder="零件编码" />
        <input v-model="form.partName" placeholder="零件名称" />
        <input v-model="form.unit" placeholder="单位" />
      </div>
      <div class="footer-actions">
        <button @click="submit">保存零件</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>
  </WorkModePage>
</template>
