<script setup lang="ts">
import type { PageModel } from '../../../types/app'

defineProps<{ model: PageModel }>()
</script>

<template>
  <section class="panel">
    <div class="section-head">
      <div>
        <h3>出入记录</h3>
        <p>每次入库、出库都会在这里留下流水。</p>
      </div>
    </div>
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
        <tr v-for="row in model.state.transactions" :key="row.id">
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
</template>
