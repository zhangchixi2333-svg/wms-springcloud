<!-- 本文件实现 SystemConfigPage 页面组件。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/wms'
import { formatStatus } from '../../../app/displayText'
import type { ConfigItem, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; moduleKey: string }>()

const rows = ref<ConfigItem[]>([])
const loading = ref(false)
const keyword = ref('')

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

async function loadRows() {
  loading.value = true
  try {
    rows.value = await api.listConfigItems(props.moduleKey)
  } finally {
    loading.value = false
  }
}

async function submit() {
  await api.createConfigItem({
    moduleKey: props.moduleKey,
    itemCode: form.itemCode,
    itemName: form.itemName,
    status: form.status,
    remark: form.remark,
  })
  form.itemCode = ''
  form.itemName = ''
  form.status = 'ENABLED'
  form.remark = ''
  props.model.actions.setNotice(`${meta.value.title}已保存`)
  await loadRows()
}

async function remove(id: number) {
  await api.deleteConfigItem(id)
  props.model.actions.setNotice(`${meta.value.title}已删除`)
  await loadRows()
}

onMounted(loadRows)
watch(() => props.moduleKey, loadRows)
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>{{ meta.title }}</h3>
          <p>当前模块的数据会保存到后端配置表，可用于系统基础配置和后续权限扩展。</p>
        </div>
      </div>

      <div class="form-grid four">
        <input v-model="form.itemCode" :placeholder="meta.code" />
        <input v-model="form.itemName" :placeholder="meta.name" />
        <select v-model="form.status">
          <option value="ENABLED">启用</option>
          <option value="DISABLED">停用</option>
        </select>
        <input v-model="form.remark" placeholder="备注 / 参数值" />
      </div>
      <div class="button-row">
        <button @click="submit">保存</button>
        <input v-model="keyword" placeholder="筛选关键字" />
      </div>
    </section>

    <section class="panel">
      <table class="table">
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
          <tr v-for="item in filteredRows" :key="item.id">
            <td class="mono">{{ item.itemCode }}</td>
            <td>{{ item.itemName }}</td>
            <td>{{ formatStatus(item.status) }}</td>
            <td>{{ item.remark }}</td>
            <td>{{ new Date(item.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</td>
            <td><button class="danger-button" @click="remove(item.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.button-row {
  display: grid;
  grid-template-columns: auto minmax(220px, 360px);
  gap: 12px;
  margin-top: 16px;
  justify-content: start;
}

@media (max-width: 1180px) {
  .button-row {
    grid-template-columns: 1fr;
  }
}
</style>
