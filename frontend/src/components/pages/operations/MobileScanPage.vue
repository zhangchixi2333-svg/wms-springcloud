<!-- 本文件实现移动端扫码工作台，扫码识别后会直接执行入库或出库，并展示结果摘要。 -->
<script setup lang="ts">
import { BrowserMultiFormatReader, type IScannerControls } from '@zxing/browser'
import type { Exception, Result } from '@zxing/library'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { findKanbanByScanCode, findLocationForKanban, formatDateTime, normalizeScanCode } from '../../../app/kanbanHelpers'
import WorkModePage from '../../shared/WorkModePage.vue'
import type { Kanban, OutboundOrder, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const mode = ref<'inbound' | 'outbound'>('inbound')
const videoRef = ref<HTMLVideoElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const scanner = new BrowserMultiFormatReader()
const scannerControls = ref<IScannerControls | null>(null)
const activeStream = ref<MediaStream | null>(null)
const scannerActive = ref(false)
const scannerError = ref('')
const message = ref('请先选择模式，然后启动摄像头扫码。')
const busy = ref(false)
const imageScanBusy = ref(false)
const autoExecute = ref(true)
const lastRecognizedText = ref('')
const lastRequestText = ref('')
const lastScanAt = ref(0)
const lastDecodeErrorAt = ref(0)
const userSelectedOutboundOrderNo = ref('')
const successfulScanKeys = new Set<string>()
const feedbackList = ref<Array<{
  success: boolean
  title: string
  detail: string
  time: string
}>>([])
const latestFeedback = computed(() => feedbackList.value[0] ?? null)
const lastResult = ref<{
  barcode: string
  kanbanNo: string
  partText: string
  actionText: string
  locationText: string
  statusText: string
  executedAt: string
} | null>(null)

const scanForm = reactive({
  barcode: '',
  locationCode: '',
  outboundOrderNo: '',
})

const modes = [
  { key: 'inbound', label: '扫码入库' },
  { key: 'outbound', label: '扫码出库' },
]

const activeOutboundOrders = computed(() =>
  props.model.state.outboundOrders.filter((item) => item.status !== 'COMPLETED' && pendingOutboundItems(item).length > 0),
)

function pendingOutboundItems(order: OutboundOrder) {
  return order.items.filter((item) => Number(item.scannedQty) < Number(item.plannedQty))
}

function scanDuplicateKey(code: string) {
  return `${mode.value}|${normalizeScanCode(code)}`
}

function findMatchedKanban(scanCode: string) {
  return findKanbanByScanCode(props.model.state.kanbans, scanCode)
}

function inboundOrderForScanCode(scanCode: string) {
  const normalized = normalizeScanCode(scanCode)
  const inboundNo = normalized.startsWith('WMS-INBOUND|') ? normalized.split('|')[1] : ''
  return props.model.state.inboundOrders.find((item) => item.inboundNo === inboundNo) ?? null
}

function outboundOrderForScanCode(scanCode: string) {
  const normalized = normalizeScanCode(scanCode)
  const outboundNo = normalized.startsWith('WMS-OUTBOUND|') ? normalized.split('|')[1] : ''
  return props.model.state.outboundOrders.find((item) => item.outboundNo === outboundNo) ?? null
}

function outboundNoFromScanCode(scanCode: string) {
  const normalized = normalizeScanCode(scanCode)
  return normalized.startsWith('WMS-OUTBOUND|') ? normalized.split('|')[1] || '' : ''
}

function plannedLocationCode(kanban: Kanban | null) {
  return findLocationForKanban(props.model.state.locations, kanban)?.locationCode ?? ''
}

function resultDetail(prefix: string, affectedCount?: number, affectedKanbanNos?: string[]) {
  const countText = typeof affectedCount === 'number' ? `，处理 ${affectedCount} 箱` : ''
  const kanbanText = affectedKanbanNos?.length ? `：${affectedKanbanNos.join('、')}` : ''
  return `${prefix}${countText}${kanbanText}`
}

function fallbackKanbanText(kanban: Kanban | null, resultKanbanNo?: string) {
  return resultKanbanNo || kanban?.kanbanNo || '后端已识别看板'
}

function formatResult(kanban: Kanban | null, statusText: string) {
  if (!kanban) {
    return {
      barcode: scanForm.barcode,
      kanbanNo: '未识别看板',
      partText: '-',
      actionText: mode.value === 'inbound' ? '已执行入库' : '已执行出库',
      locationText: mode.value === 'inbound' ? scanForm.locationCode : scanForm.outboundOrderNo,
      statusText,
      executedAt: formatDateTime(new Date().toISOString()),
    }
  }
  return {
    barcode: kanban.barcode,
    kanbanNo: kanban.kanbanNo,
    partText: `${kanban.partCode} | ${kanban.partName}`,
    actionText: mode.value === 'inbound' ? '扫码入库成功' : '扫码出库成功',
    locationText:
      mode.value === 'inbound'
        ? `${kanban.warehouseName} / ${kanban.zoneName} / ${scanForm.locationCode || kanban.locationCode || '自动匹配库位'}`
        : `出库单 ${scanForm.outboundOrderNo}`,
    statusText,
    executedAt: formatDateTime(new Date().toISOString()),
  }
}

function pushFeedback(success: boolean, title: string, detail: string) {
  feedbackList.value.unshift({
    success,
    title,
    detail,
    time: formatDateTime(new Date().toISOString()),
  })
  feedbackList.value = feedbackList.value.slice(0, 12)
}

async function executeScan(code: string, source: 'camera' | 'manual' | 'image' = 'manual') {
  const normalized = normalizeScanCode(code)
  if (!normalized) return
  if (busy.value) {
    message.value = '上一笔扫码正在提交，请稍候。'
    pushFeedback(false, '扫码暂缓', '上一笔业务还未返回结果，已忽略本次重复识别。')
    return
  }
  lastRecognizedText.value = normalized
  const duplicateKey = scanDuplicateKey(normalized)
  if (successfulScanKeys.has(duplicateKey)) {
    message.value = '该二维码本次已经执行成功，请扫描下一张。'
    lastRequestText.value = ''
    pushFeedback(false, '重复扫码已忽略', `${message.value} ${normalized}`)
    return
  }
  pushFeedback(true, '已识别二维码', source === 'camera' ? '摄像头已识别，正在提交业务。' : `已识别：${normalized}`)
  const kanban = findMatchedKanban(normalized)
  scanForm.barcode = normalized

  if (mode.value === 'inbound') {
    const matchedLocationCode = plannedLocationCode(kanban)
    if (matchedLocationCode) {
      scanForm.locationCode = matchedLocationCode
    }
  }

  busy.value = true
  lastRequestText.value = mode.value === 'inbound' ? '正在提交扫码入库请求...' : '正在提交扫码出库请求...'
  try {
    if (mode.value === 'inbound') {
      const result = await props.model.actions.scanInbound({
        barcode: normalized,
        locationCode: scanForm.locationCode,
      })
      const resultKanban = findMatchedKanban(normalized) ?? findMatchedKanban(result.barcode) ?? kanban
      const inboundOrder = inboundOrderForScanCode(normalized)
      lastResult.value = formatResult(resultKanban, result.message || '入库完成。')
      lastResult.value.kanbanNo = fallbackKanbanText(resultKanban, result.scannedKanbanNo)
      if (inboundOrder) {
        lastResult.value.kanbanNo = inboundOrder.inboundNo
        lastResult.value.partText = `入库单批量入库，处理 ${result.affectedCount} 箱`
        lastResult.value.locationText = `${inboundOrder.supplierName} / ${scanForm.locationCode || '自动匹配库位'}`
      }
      message.value = `已入库 ${lastResult.value.kanbanNo}`
      scannerError.value = ''
      successfulScanKeys.add(duplicateKey)
      pushFeedback(true, '入库成功', resultDetail(result.message || `${lastResult.value.kanbanNo} 已入库到 ${lastResult.value.locationText}`, result.affectedCount, result.affectedKanbanNos))
    } else {
      const scannedOutboundNo = outboundNoFromScanCode(normalized)
      const outboundOrderNoForRequest = scannedOutboundNo || userSelectedOutboundOrderNo.value
      if (scannedOutboundNo) {
        scanForm.outboundOrderNo = scannedOutboundNo
      }
      const result = await props.model.actions.scanOutbound({
        barcode: normalized,
        outboundOrderNo: outboundOrderNoForRequest,
      })
      if (result.outboundOrderNo) {
        scanForm.outboundOrderNo = result.outboundOrderNo
      }
      const resultKanban = findMatchedKanban(normalized) ?? findMatchedKanban(result.barcode) ?? kanban
      const outboundOrder = outboundOrderForScanCode(normalized)
      lastResult.value = formatResult(resultKanban, result.message || '出库完成，系统已推进对应出库单状态。')
      lastResult.value.kanbanNo = fallbackKanbanText(resultKanban, result.scannedKanbanNo)
      if (outboundOrder) {
        lastResult.value.kanbanNo = outboundOrder.outboundNo
        lastResult.value.partText = `出库单批量出库，处理 ${result.affectedCount} 箱`
      }
      lastResult.value.locationText = `出库单 ${result.outboundOrderNo || scanForm.outboundOrderNo || outboundOrder?.outboundNo || '自动匹配'}`
      message.value = `已出库 ${lastResult.value.kanbanNo}`
      scannerError.value = ''
      successfulScanKeys.add(duplicateKey)
      pushFeedback(true, '出库成功', resultDetail(result.message || `${lastResult.value.kanbanNo} 已绑定 ${lastResult.value.locationText}`, result.affectedCount, result.affectedKanbanNos))
    }
    scanForm.barcode = ''
  } catch (error) {
    scannerError.value = error instanceof Error ? error.message : '扫码业务执行失败'
    message.value = scannerError.value
    lastResult.value = null
    pushFeedback(false, mode.value === 'inbound' ? '入库失败' : '出库失败', scannerError.value)
  } finally {
    busy.value = false
    lastRequestText.value = ''
  }
}

function isLocalCameraHost(hostname: string) {
  return ['localhost', '127.0.0.1', '::1'].includes(hostname)
}

function checkCameraSupport() {
  if (typeof navigator === 'undefined') {
    return '当前环境不支持浏览器摄像头。'
  }
  if (typeof window !== 'undefined' && !window.isSecureContext && !isLocalCameraHost(window.location.hostname)) {
    return '当前页面不是 HTTPS 安全页面，手机浏览器会隐藏实时摄像头能力。请使用“拍照识别二维码”，或改用 HTTPS 域名访问。'
  }
  if (!navigator.mediaDevices) {
    return '当前浏览器没有 mediaDevices，通常是浏览器过旧或页面不是 HTTPS。请使用“拍照识别二维码”继续操作。'
  }
  if (!navigator.mediaDevices.getUserMedia) {
    return '当前浏览器不支持 getUserMedia，无法实时调用摄像头。请使用“拍照识别二维码”继续操作。'
  }
  return ''
}

async function startScanner() {
  scannerError.value = ''
  const supportError = checkCameraSupport()
  if (supportError) {
    scannerError.value = supportError
    return
  }
  if (!videoRef.value) {
    scannerError.value = '视频预览节点未初始化。'
    return
  }
  stopScanner()
  try {
    const constraints: MediaStreamConstraints = {
      video: { facingMode: { ideal: 'environment' } },
      audio: false,
    }
    scannerControls.value = await scanner.decodeFromConstraints(constraints, videoRef.value, handleDecodeResult)
    activeStream.value = videoRef.value.srcObject instanceof MediaStream ? videoRef.value.srcObject : null
    scannerActive.value = true
    message.value = '摄像头已启动，识别到二维码后会直接执行业务。'
  } catch (error) {
    const detail = error instanceof Error ? error.message : '启动摄像头失败'
    scannerError.value = detail.includes('setPhotoOptions')
      ? '摄像头启动兼容失败，请使用“拍照识别二维码”或手工输入条码。'
      : detail
    scannerActive.value = false
  }
}

async function handleDecodeResult(result: Result | undefined, error: Exception | undefined) {
  if (result) {
    const text = result.getText()
    const now = Date.now()
    const normalized = normalizeScanCode(text)
    if (normalized === lastRecognizedText.value && now - lastScanAt.value < 1500) {
      return
    }
    lastScanAt.value = now
    if (autoExecute.value) {
      await executeScan(text, 'camera')
    } else {
      scanForm.barcode = text
      message.value = '已识别二维码内容，可手动确认执行。'
    }
    return
  }
  if (error && !String(error).includes('NotFoundException')) {
    const now = Date.now()
    if (now - lastDecodeErrorAt.value < 2000) return
    lastDecodeErrorAt.value = now
    const detail = error instanceof Error ? error.message : String(error)
    if (detail.includes('setPhotoOptions')) {
      scannerError.value = '当前摄像头不支持浏览器设置参数，请使用“拍照识别二维码”。'
      return
    }
    scannerError.value = detail
  }
}

function stopScanner() {
  const stopResult = scannerControls.value?.stop()
  if (stopResult && typeof (stopResult as Promise<void>).catch === 'function') {
    ;(stopResult as Promise<void>).catch(() => undefined)
  }
  scannerControls.value = null
  activeStream.value?.getTracks().forEach((track) => track.stop())
  activeStream.value = null
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
  scannerActive.value = false
}

function triggerImageScan() {
  if (busy.value || imageScanBusy.value) return
  stopScanner()
  scannerError.value = ''
  imageInputRef.value?.click()
}

async function handleImagePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  imageScanBusy.value = true
  scannerError.value = ''
  const imageUrl = URL.createObjectURL(file)
  try {
    const text = await decodePickedImage(file, imageUrl)
    if (autoExecute.value) {
      await executeScan(text, 'image')
    } else {
      scanForm.barcode = text
      message.value = '已从图片识别二维码内容，可手动确认执行。'
      pushFeedback(true, '图片识别成功', '已识别二维码内容，等待手动执行业务。')
    }
  } catch (error) {
    scannerError.value = '未能从图片中识别二维码，请重新拍摄清晰的二维码或手动输入条码。'
    pushFeedback(false, '图片识别失败', error instanceof Error ? `${scannerError.value} 原因：${error.message}` : scannerError.value)
  } finally {
    URL.revokeObjectURL(imageUrl)
    imageScanBusy.value = false
  }
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('读取图片失败'))
    reader.readAsDataURL(file)
  })
}

async function decodePickedImage(file: File, objectUrl: string) {
  try {
    return (await scanner.decodeFromImageUrl(objectUrl)).getText()
  } catch (objectUrlError) {
    try {
      const dataUrl = await readFileAsDataUrl(file)
      return (await scanner.decodeFromImageUrl(dataUrl)).getText()
    } catch {
      throw objectUrlError
    }
  }
}

function handleOutboundOrderChange() {
  scanForm.barcode = ''
  userSelectedOutboundOrderNo.value = scanForm.outboundOrderNo
  scannerError.value = ''
}

watch(mode, () => {
  scanForm.barcode = ''
  scanForm.locationCode = ''
  scanForm.outboundOrderNo = ''
  userSelectedOutboundOrderNo.value = ''
  scannerError.value = ''
  lastResult.value = null
  successfulScanKeys.clear()
  message.value = mode.value === 'inbound' ? '请扫描入库单二维码或箱看板。' : '请扫描出库单二维码或箱看板。'
})

watch(activeOutboundOrders, (orders) => {
  if (mode.value !== 'outbound' || !userSelectedOutboundOrderNo.value) return
  if (!orders.some((item) => item.outboundNo === userSelectedOutboundOrderNo.value)) {
    userSelectedOutboundOrderNo.value = ''
    scanForm.outboundOrderNo = ''
    scanForm.barcode = ''
    message.value = '当前出库单已无待出库箱，请选择新的出库单。'
  }
})

onBeforeUnmount(() => {
  stopScanner()
})
</script>

<template>
  <WorkModePage v-model="mode" :modes="modes">
    <section class="mobile-scan-page">
      <section class="panel mobile-scan-panel">
        <div class="mobile-scan-toolbar">
          <div class="mobile-scan-title">
            <h3>移动扫码</h3>
            <span>{{ mode === 'inbound' ? '入库：扫入库单或箱看板' : '出库：扫出库单或箱看板' }}</span>
          </div>
          <div class="mobile-scan-actions">
            <label class="toggle-line compact-toggle"><input v-model="autoExecute" type="checkbox" /> 自动执行</label>
            <select v-if="mode === 'outbound'" v-model="scanForm.outboundOrderNo" class="mobile-order-select" @change="handleOutboundOrderChange">
              <option value="">自动匹配出库单</option>
              <option v-for="item in activeOutboundOrders" :key="item.id" :value="item.outboundNo">
                {{ item.outboundNo }} | 待出库 {{ pendingOutboundItems(item).length }} 箱
              </option>
            </select>
            <button @click="startScanner">{{ scannerActive ? '重新启动摄像头' : '启动摄像头' }}</button>
            <button class="secondary-button" @click="stopScanner">停止摄像头</button>
            <button class="secondary-button" :disabled="imageScanBusy || busy" @click="triggerImageScan">
              {{ imageScanBusy ? '识别中...' : '拍照识别二维码' }}
            </button>
          </div>
        </div>
        <input
          ref="imageInputRef"
          class="hidden-file-input"
          type="file"
          accept="image/*"
          capture="environment"
          @change="handleImagePicked"
        />
        <video ref="videoRef" class="scan-video" autoplay muted playsinline />
        <div class="scan-status-row">
          <span class="scan-message">{{ message }}</span>
          <span v-if="lastRecognizedText" class="scan-meta">最近识别：{{ lastRecognizedText }}</span>
          <span v-if="lastRequestText" class="scan-meta">{{ lastRequestText }}</span>
        </div>
        <p v-if="mode === 'outbound' && !activeOutboundOrders.length" class="scan-error">
          当前没有可扫码出库的出库单，请先创建出库单。
        </p>
        <div v-if="latestFeedback" class="latest-feedback" :class="{ success: latestFeedback.success, fail: !latestFeedback.success }">
          <strong>{{ latestFeedback.title }}</strong>
          <span>{{ latestFeedback.detail }}</span>
          <small>{{ latestFeedback.time }}</small>
        </div>
        <p v-if="scannerError" class="scan-error">{{ scannerError }}</p>
      </section>

      <section v-if="lastResult" class="panel result-panel">
        <div class="section-head">
          <div>
            <h3>扫码结果</h3>
            <p>这里展示最近一次成功执行的业务信息。</p>
          </div>
        </div>
        <table class="table compact-table">
          <tbody>
            <tr><th>操作结果</th><td>{{ lastResult.actionText }}</td></tr>
            <tr><th>看板号</th><td>{{ lastResult.kanbanNo }}</td></tr>
            <tr><th>零件</th><td>{{ lastResult.partText }}</td></tr>
            <tr><th>条码</th><td class="mono">{{ lastResult.barcode }}</td></tr>
            <tr><th>目标信息</th><td>{{ lastResult.locationText }}</td></tr>
            <tr><th>状态说明</th><td>{{ lastResult.statusText }}</td></tr>
            <tr><th>执行时间</th><td>{{ lastResult.executedAt }}</td></tr>
          </tbody>
        </table>
      </section>

      <section v-if="feedbackList.length" class="panel result-panel">
        <div class="section-head">
          <div>
            <h3>扫码反馈记录</h3>
            <p>每次扫码成功或失败都会记录在这里，便于连续作业时回看原因。</p>
          </div>
        </div>
        <div class="feedback-list">
          <article v-for="(item, index) in feedbackList" :key="`${item.time}-${index}`" class="feedback-item" :class="{ success: item.success, fail: !item.success }">
            <strong>{{ item.title }}</strong>
            <span>{{ item.detail }}</span>
            <small>{{ item.time }}</small>
          </article>
        </div>
      </section>
    </section>
  </WorkModePage>
</template>

<style scoped>
.mobile-scan-page {
  display: grid;
  gap: 10px;
}

.mobile-scan-panel {
  display: grid;
  gap: 8px;
}

.mobile-scan-toolbar {
  display: grid;
  grid-template-columns: minmax(130px, auto) 1fr;
  align-items: center;
  gap: 8px;
}

.mobile-scan-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.mobile-scan-title h3 {
  margin: 0;
  white-space: nowrap;
}

.mobile-scan-title span {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.mobile-scan-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.mobile-scan-actions button,
.mobile-scan-actions select {
  min-height: 32px;
}

.mobile-scan-actions button {
  padding: 6px 10px;
}

.mobile-order-select {
  width: min(260px, 100%);
}

.compact-toggle {
  min-height: 32px;
  padding: 0 4px;
  white-space: nowrap;
}

.scan-video {
  width: 100%;
  min-height: 220px;
  max-height: 52vh;
  background: #111827;
  border-radius: 8px;
  object-fit: cover;
}

.hidden-file-input {
  display: none;
}

.scan-status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: center;
  min-height: 24px;
}

.scan-message {
  margin: 0;
}

.scan-meta {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  word-break: break-all;
}

.latest-feedback {
  display: grid;
  gap: 4px;
  margin-top: 2px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 10px;
}

.latest-feedback.success {
  border-color: #16a34a;
  background: rgba(22, 163, 74, 0.1);
}

.latest-feedback.fail {
  border-color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}

.scan-error {
  margin: 0;
  color: #dc2626;
}

.toggle-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.compact-table th {
  width: 96px;
  white-space: nowrap;
}

.feedback-list {
  display: grid;
  gap: 8px;
}

.feedback-item {
  display: grid;
  gap: 4px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 10px;
}

.feedback-item.success {
  border-color: #16a34a;
  background: rgba(22, 163, 74, 0.08);
}

.feedback-item.fail {
  border-color: #dc2626;
  background: rgba(220, 38, 38, 0.08);
}

@media (max-width: 720px) {
  .mobile-scan-toolbar {
    grid-template-columns: 1fr;
  }

  .mobile-scan-title {
    justify-content: space-between;
  }

  .mobile-scan-actions {
    justify-content: stretch;
  }

  .mobile-scan-actions button,
  .mobile-scan-actions select {
    flex: 1 1 calc(50% - 6px);
    min-width: 0;
  }

  .compact-toggle {
    flex: 1 1 calc(50% - 6px);
  }
}
</style>
