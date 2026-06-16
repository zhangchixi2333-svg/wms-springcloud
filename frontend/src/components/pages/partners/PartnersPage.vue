<!-- 本文件实现 PartnersPage 页面组件。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode: 'supplier' | 'customer' }>()

const viewMode = ref<'query' | 'create'>('query')
const workModes = computed(() => [
  { key: 'query', label: `查询${props.mode === 'supplier' ? '供应商' : '客户'}` },
  { key: 'create', label: `新建${props.mode === 'supplier' ? '供应商' : '客户'}` },
])

const filters = reactive({
  code: '',
  name: '',
})

const supplierForm = reactive({ supplierCode: '', supplierName: '' })
const customerForm = reactive({ customerCode: '', customerName: '' })

const suppliers = computed(() =>
  props.model.state.suppliers.filter((item) => {
    const codeMatch = !filters.code || item.supplierCode.toLowerCase().includes(filters.code.toLowerCase())
    const nameMatch = !filters.name || item.supplierName.toLowerCase().includes(filters.name.toLowerCase())
    return codeMatch && nameMatch
  }),
)

const customers = computed(() =>
  props.model.state.customers.filter((item) => {
    const codeMatch = !filters.code || item.customerCode.toLowerCase().includes(filters.code.toLowerCase())
    const nameMatch = !filters.name || item.customerName.toLowerCase().includes(filters.name.toLowerCase())
    return codeMatch && nameMatch
  }),
)

function resetFilters() {
  filters.code = ''
  filters.name = ''
}

async function submitSupplier() {
  await props.model.actions.createSupplier(supplierForm)
  supplierForm.supplierCode = ''
  supplierForm.supplierName = ''
  viewMode.value = 'query'
}

async function submitCustomer() {
  await props.model.actions.createCustomer(customerForm)
  customerForm.customerCode = ''
  customerForm.customerName = ''
  viewMode.value = 'query'
}
</script>

<template>
  <WorkModePage v-model="viewMode" :modes="workModes">
    <section v-if="viewMode === 'query'" class="stack">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>{{ mode === 'supplier' ? '供应商筛选' : '客户筛选' }}</h3>
            <p>默认进入查询状态，方便先查再建。</p>
          </div>
        </div>
        <div class="form-grid three">
          <input v-model="filters.code" :placeholder="mode === 'supplier' ? '供应商编码' : '客户编码'" />
          <input v-model="filters.name" :placeholder="mode === 'supplier' ? '供应商名称' : '客户名称'" />
          <button class="secondary-button" @click="resetFilters">重置筛选</button>
        </div>
      </section>

      <section class="panel">
        <table class="table">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
            </tr>
          </thead>
          <tbody v-if="mode === 'supplier'">
            <tr v-for="item in suppliers" :key="item.id">
              <td>{{ item.supplierCode }}</td>
              <td>{{ item.supplierName }}</td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr v-for="item in customers" :key="item.id">
              <td>{{ item.customerCode }}</td>
              <td>{{ item.customerName }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

    <section v-else class="panel">
      <div class="section-head">
        <div>
          <h3>{{ mode === 'supplier' ? '新建供应商' : '新建客户' }}</h3>
          <p>新建完成后会自动返回查询视图。</p>
        </div>
      </div>

      <div v-if="mode === 'supplier'" class="form-grid two">
        <input v-model="supplierForm.supplierCode" placeholder="供应商编码" />
        <input v-model="supplierForm.supplierName" placeholder="供应商名称" />
      </div>
      <div v-else class="form-grid two">
        <input v-model="customerForm.customerCode" placeholder="客户编码" />
        <input v-model="customerForm.customerName" placeholder="客户名称" />
      </div>

      <div class="footer-actions">
        <button v-if="mode === 'supplier'" @click="submitSupplier">保存供应商</button>
        <button v-else @click="submitCustomer">保存客户</button>
        <button class="secondary-button" @click="viewMode = 'query'">返回查询</button>
      </div>
    </section>
  </WorkModePage>
</template>

<style scoped>
</style>
