<script setup lang="ts">
import { computed, reactive } from 'vue'
import { kanbanScanOptions } from '../../../app/optionHelpers'
import QrCodeImage from '../../shared/QrCodeImage.vue'
import type { Kanban, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel; mode: 'repack' | 'balance' | 'transferFreeze' }>()

const transferForm = reactive({ barcode: '', inboundOrderNo: '', locationCode: '', remark: '' })
const freezeForm = reactive({ barcode: '', frozen: true, remark: '' })
const repackOutForm = reactive({ barcode: '', locationCode: '', remark: '' })
const repackInForm = reactive({ barcode: '', locationCode: '', qty: 1, remark: '' })
const balanceForm = reactive({ barcode: '', qty: 1, remark: '' })

const pageTitle = computed(() => ({
  repack: '转包作业',
  balance: '转包结余',
  transferFreeze: '移库 / 封存',
}[props.mode]))

const stockKanbans = computed(() =>
  props.model.state.kanbans.filter((item) => ['INBOUND', 'FROZEN', 'REPACK_OUTBOUND'].includes(item.status)),
)
const operableKanbanOptions = computed(() => kanbanScanOptions(props.model.state.kanbans, ['INBOUND']))
const freezeKanbanOptions = computed(() => kanbanScanOptions(props.model.state.kanbans, ['INBOUND', 'FROZEN']))
const repackInboundKanbanOptions = computed(() => kanbanScanOptions(props.model.state.kanbans, ['REPACK_OUTBOUND']))
const ownLocations = computed(() => props.model.state.locations.filter((item) => item.warehouseType !== 'THIRD_PARTY'))
const thirdPartyLocations = computed(() => props.model.state.locations.filter((item) => item.warehouseType === 'THIRD_PARTY'))
const selectedTransferKanban = computed(() => findKanbanByScanValue(transferForm.barcode))
const transferInboundOptions = computed(() => {
  if (!selectedTransferKanban.value) return []
  return props.model.state.inboundOrders.filter((item) => item.inboundNo === selectedTransferKanban.value?.inboundNo)
})

async function submitTransfer() {
  await props.model.actions.transferKanban(transferForm)
  transferForm.barcode = ''
  transferForm.inboundOrderNo = ''
  transferForm.remark = ''
}

async function submitFreeze() {
  await props.model.actions.freezeKanban(freezeForm)
  freezeForm.barcode = ''
  freezeForm.remark = ''
}

async function submitRepackOut() {
  await props.model.actions.repackOutbound(repackOutForm)
  repackOutForm.barcode = ''
  repackOutForm.locationCode = ''
  repackOutForm.remark = ''
}

async function submitRepackIn() {
  await props.model.actions.repackInbound(repackInForm)
  repackInForm.barcode = ''
  repackInForm.qty = 1
  repackInForm.remark = ''
}

async function submitBalance() {
  await props.model.actions.adjustKanbanBalance(balanceForm)
  balanceForm.barcode = ''
  balanceForm.qty = 1
  balanceForm.remark = ''
}

function handleTransferKanbanChange() {
  transferForm.inboundOrderNo = ''
}

function findKanbanByScanValue(value: string) {
  if (!value) return null
  return props.model.state.kanbans.find((item) => item.barcode === value || item.qrContent === value) ?? null
}

function showStatus(item: Kanban) {
  if (item.status === 'FROZEN') return '已封存'
  return {
    INBOUND: '在库',
    REPACK_OUTBOUND: '转包出库',
    OUTBOUND: '已出库',
    WAIT_SCAN: '待入库',
  }[item.status] ?? item.status
}
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>{{ pageTitle }}</h3>
          <p>扫描内容可以输入看板条码，也可以直接输入打印二维码中的内容。</p>
        </div>
      </div>

      <div v-if="mode === 'transferFreeze'" class="ops-grid">
        <div class="op-box">
          <h4>移库</h4>
          <div class="scan-action-layout">
            <div class="form-grid">
              <select v-model="transferForm.barcode" @change="handleTransferKanbanChange">
                <option value="">选择看板条码 / 二维码</option>
                <option v-for="item in operableKanbanOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <select v-model="transferForm.inboundOrderNo" :disabled="!transferForm.barcode">
                <option value="">选择来源入库单</option>
                <option v-for="item in transferInboundOptions" :key="item.id" :value="item.inboundNo">
                  {{ item.inboundNo }} | {{ item.supplierName }} | {{ item.status }}
                </option>
              </select>
              <select v-model="transferForm.locationCode">
                <option value="">选择目标库位</option>
                <option v-for="item in model.state.locations" :key="item.id" :value="item.locationCode">
                  {{ item.locationCode }} | {{ item.warehouseName }} / {{ item.zoneName }}
                </option>
              </select>
              <input v-model="transferForm.remark" placeholder="备注" />
              <button :disabled="!transferForm.barcode || !transferForm.inboundOrderNo || !transferForm.locationCode" @click="submitTransfer">确认移库</button>
            </div>
            <div v-if="transferForm.barcode" class="scan-qr-preview">
              <QrCodeImage :text="transferForm.barcode" :size="160" />
              <p class="mono">{{ transferForm.barcode }}</p>
            </div>
          </div>
        </div>

        <div class="op-box">
          <h4>封存 / 解封</h4>
          <div class="scan-action-layout">
            <div class="form-grid">
              <select v-model="freezeForm.barcode">
                <option value="">选择看板条码 / 二维码</option>
                <option v-for="item in freezeKanbanOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <select v-model="freezeForm.frozen">
                <option :value="true">封存</option>
                <option :value="false">解封</option>
              </select>
              <input v-model="freezeForm.remark" placeholder="原因 / 备注" />
              <button @click="submitFreeze">{{ freezeForm.frozen ? '确认封存' : '确认解封' }}</button>
            </div>
            <div v-if="freezeForm.barcode" class="scan-qr-preview">
              <QrCodeImage :text="freezeForm.barcode" :size="160" />
              <p class="mono">{{ freezeForm.barcode }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="mode === 'repack'" class="ops-grid">
        <div class="op-box">
          <h4>转包出库</h4>
          <div class="scan-action-layout">
            <div class="form-grid">
              <select v-model="repackOutForm.barcode">
                <option value="">选择看板条码 / 二维码</option>
                <option v-for="item in operableKanbanOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <select v-model="repackOutForm.locationCode">
                <option value="">选择第三方仓库库位</option>
                <option v-for="item in thirdPartyLocations" :key="item.id" :value="item.locationCode">
                  {{ item.locationCode }} | {{ item.warehouseName }} / {{ item.zoneName }}
                </option>
              </select>
              <input v-model="repackOutForm.remark" placeholder="备注" />
              <button @click="submitRepackOut">确认转包出库</button>
            </div>
            <div v-if="repackOutForm.barcode" class="scan-qr-preview">
              <QrCodeImage :text="repackOutForm.barcode" :size="160" />
              <p class="mono">{{ repackOutForm.barcode }}</p>
            </div>
          </div>
        </div>

        <div class="op-box">
          <h4>转包入库</h4>
          <div class="scan-action-layout">
            <div class="form-grid">
              <select v-model="repackInForm.barcode">
                <option value="">选择看板条码 / 二维码</option>
                <option v-for="item in repackInboundKanbanOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <select v-model="repackInForm.locationCode">
                <option value="">选择自己仓库入库库位</option>
                <option v-for="item in ownLocations" :key="item.id" :value="item.locationCode">
                  {{ item.locationCode }} | {{ item.warehouseName }} / {{ item.zoneName }}
                </option>
              </select>
              <input v-model.number="repackInForm.qty" type="number" min="0.001" step="0.001" placeholder="转包后数量" />
              <input v-model="repackInForm.remark" placeholder="备注" />
              <button @click="submitRepackIn">确认转包入库</button>
            </div>
            <div v-if="repackInForm.barcode" class="scan-qr-preview">
              <QrCodeImage :text="repackInForm.barcode" :size="160" />
              <p class="mono">{{ repackInForm.barcode }}</p>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="scan-action-layout">
        <div class="form-grid four">
          <select v-model="balanceForm.barcode">
            <option value="">选择看板条码 / 二维码</option>
            <option v-for="item in operableKanbanOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <input v-model.number="balanceForm.qty" type="number" min="0.001" step="0.001" placeholder="调整后数量" />
          <input v-model="balanceForm.remark" placeholder="调整原因" />
          <button @click="submitBalance">确认调整</button>
        </div>
        <div v-if="balanceForm.barcode" class="scan-qr-preview">
          <QrCodeImage :text="balanceForm.barcode" :size="160" />
          <p class="mono">{{ balanceForm.barcode }}</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <table class="table">
        <thead>
          <tr>
            <th>看板号</th>
            <th>条码</th>
            <th>零件</th>
            <th>数量</th>
            <th>库位</th>
            <th>状态</th>
            <th>二维码内容</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in stockKanbans" :key="item.id">
            <td>{{ item.kanbanNo }}</td>
            <td class="mono">{{ item.barcode }}</td>
            <td>{{ item.partCode }} | {{ item.partName }}</td>
            <td>{{ item.qty }}</td>
            <td>{{ item.locationCode }}</td>
            <td>{{ showStatus(item) }}</td>
            <td class="mono qr-cell">{{ item.qrContent }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.ops-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.op-box {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px;
  background: #f8fbfc;
}

.op-box h4 {
  margin: 0 0 12px;
}

.qr-cell {
  max-width: 280px;
  word-break: break-all;
}

@media (max-width: 1180px) {
  .ops-grid {
    grid-template-columns: 1fr;
  }
}
</style>
