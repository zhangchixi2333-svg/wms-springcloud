<!-- 本文件实现 SystemConfigPage 页面组件。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatStatus } from '../../../app/displayText'
import CompactPager from '../../shared/CompactPager.vue'
import PageModal from '../../shared/PageModal.vue'
import type { ConfigItem, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; moduleKey: string }>()

const rows = ref<ConfigItem[]>([])
const loading = ref(false)
const keyword = ref('')
const createOpen = ref(false)
const page = ref(1)
const pageSize = ref(20)

const form = reactive({
  itemCode: '',
  itemName: '',
  status: 'ENABLED',
  remark: '',
})

const meta = computed(() => ({
  userManagement: { title: '用户管理', code: '用户名', name: '显示名称' },
  roleManagement: { title: '角色管理', code: '角色编码', name: '角色名称' },
  departmentManagement: { title: '部门管理', code: '部门编码', name: '部门名称' },
  postManagement: { title: '岗位管理', code: '岗位编码', name: '岗位名称' },
  dictionaryManagement: { title: '字典管理', code: '字典编码', name: '字典名称' },
  parameterSettings: { title: '参数设置', code: '参数键', name: '参数名称' },
  systemTools: { title: '系统工具', code: '工具编码', name: '工具名称' },
  categoryManagement: { title: '分类管理', code: '分类编码', name: '分类名称' },
}[props.moduleKey] ?? { title: '系统配置', code: '编码', name: '名称' }))

const filteredRows = computed(() =>
  rows.value.filter((item) => {
    const text = `${item.itemCode} ${item.itemName} ${item.status} ${item.remark}`.toLowerCase()
    return !keyword.value || text.includes(keyword.value.toLowerCase())
  }),
)

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRows.value.length / pageSize.value)))
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

async function loadRows() {
  loading.value = true
  try {
    rows.value = await api.listConfigItems(props.moduleKey)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  page.value = 1
}

function resetForm() {
  form.itemCode = ''
  form.itemName = ''
  form.status = 'ENABLED'
  form.remark = ''
}

function openCreate() {
  resetForm()
  createOpen.value = true
}

function closeCreateModal() {
  createOpen.value = false
}

async function submit() {
  await api.createConfigItem({
    moduleKey: props.moduleKey,
    itemCode: form.itemCode,
    itemName: form.itemName,
    status: form.status,
    remark: form.remark,
  })
  resetForm()
  closeCreateModal()
  props.model.actions.setNotice(`${meta.value.title}已保存`)
  await loadRows()
}

async function remove(id: number) {
  await api.deleteConfigItem(id)
  props.model.actions.setNotice(`${meta.value.title}已删除`)
  await loadRows()
}

onMounted(loadRows)
watch(() => props.moduleKey, () => {
  page.value = 1
  resetForm()
  void loadRows()
})
watch(filteredRows, () => {
  if (page.value > totalPages.value) page.value = totalPages.value
})
watch(keyword, () => {
  page.value = 1
})
</script>

<template>
  <section class="stack config-page">
    <section class="panel config-filter-panel">
      <div class="config-filter-line">
        <h3>{{ meta.title }}</h3>
        <div class="config-filter-row">
          <input v-model="keyword" placeholder="编码 / 名称 / 备注" />
          <button class="secondary-button compact-filter-button" @click="resetFilters">重置</button>
        </div>
        <div class="action-row config-actions">
          <CompactPager v-model:page="page" v-model:page-size="pageSize" :total="filteredRows.length" />
          <button @click="openCreate">新增{{ meta.title }}</button>
        </div>
      </div>
    </section>

    <section class="panel table-scroll config-table-panel">
      <table class="table config-table">
        <thead>
          <tr>
            <th>{{ meta.code }}</th>
            <th>{{ meta.name }}</th>
            <th>状态</th>
            <th>备注 / 值</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6">加载中...</td>
          </tr>
          <tr v-for="(item, index) in pageRows" :key="item.id" class="config-row" :class="{ 'tone-a': index % 2 === 0, 'tone-b': index % 2 === 1 }">
            <td class="mono">{{ item.itemCode }}</td>
            <td>{{ item.itemName }}</td>
            <td>{{ formatStatus(item.status) }}</td>
            <td>{{ item.remark }}</td>
            <td>{{ new Date(item.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
            <td class="row-actions"><button class="danger-button" @click="remove(item.id)">删除</button></td>
          </tr>
          <tr v-if="!loading && !pageRows.length">
            <td colspan="6" class="empty-cell">没有匹配的{{ meta.title }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <PageModal :open="createOpen" @close="closeCreateModal">
      <section class="panel config-create-panel">
        <div class="section-head compact-head">
          <div>
            <h3>新增{{ meta.title }}</h3>
          </div>
          <div class="action-row">
            <button @click="submit">保存</button>
          </div>
        </div>
        <div class="create-table-wrap">
          <div class="config-create-row config-create-header">
            <span>{{ meta.code }}</span>
            <span>{{ meta.name }}</span>
            <span>状态</span>
            <span>备注 / 参数值</span>
          </div>
          <div class="config-create-row config-create-data">
            <input v-model="form.itemCode" :placeholder="meta.code" />
            <input v-model="form.itemName" :placeholder="meta.name" />
            <select v-model="form.status">
              <option value="ENABLED">启用</option>
              <option value="DISABLED">停用</option>
            </select>
            <input v-model="form.remark" placeholder="备注 / 参数值" />
          </div>
        </div>
      </section>
    </PageModal>
  </section>
</template>

<style scoped>
.config-filter-panel {
  padding: 8px 10px;
}

.config-filter-line {
  display: grid;
  grid-template-columns: 96px minmax(320px, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.config-filter-line h3,
.compact-head h3 {
  margin: 0;
  white-space: nowrap;
  font-size: 16px;
}

.config-filter-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 58px;
  gap: 6px;
  align-items: center;
}

.config-filter-row input,
.compact-filter-button {
  min-height: 34px;
}

.config-actions {
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 6px;
}

.config-actions button {
  min-height: 34px;
  padding: 0 10px;
  white-space: nowrap;
}

.table-scroll,
.create-table-wrap {
  overflow-x: auto;
}

.config-table-panel {
  padding: 10px;
}

.config-table {
  min-width: 980px;
  table-layout: fixed;
}

.config-table th,
.config-table td {
  padding: 7px 9px;
  vertical-align: middle;
}

.config-table th:nth-child(1),
.config-table td:nth-child(1),
.config-table th:nth-child(3),
.config-table td:nth-child(3) {
  width: 128px;
}

.config-table th:nth-child(5),
.config-table td:nth-child(5) {
  width: 170px;
}

.config-table th:nth-child(6),
.config-table td:nth-child(6) {
  width: 72px;
}

.config-row.tone-a {
  background: rgba(255, 255, 255, 0.92);
}

.config-row.tone-b {
  background: rgba(148, 163, 184, 0.07);
}

.config-row:hover {
  background: rgba(37, 99, 235, 0.11);
}

.row-actions button {
  min-height: 28px;
  padding: 0 8px;
}

.config-create-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.compact-head {
  align-items: center;
  margin-bottom: 0;
}

.config-create-row {
  display: grid;
  grid-template-columns: 150px 180px 104px minmax(220px, 1fr);
  gap: 6px;
  align-items: center;
  min-width: 720px;
}

.config-create-header {
  padding: 0 2px 2px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.config-create-data {
  padding: 4px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.config-create-data input,
.config-create-data select {
  min-width: 0;
  min-height: 32px;
}

.empty-cell {
  color: var(--text-secondary);
  text-align: center;
}

@media (max-width: 1180px) {
  .config-filter-line {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .config-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

@media (max-width: 760px) {
  .config-filter-row {
    grid-template-columns: 1fr 58px;
  }
}
</style>
