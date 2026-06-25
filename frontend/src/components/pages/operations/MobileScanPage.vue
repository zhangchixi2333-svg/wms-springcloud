<!-- 本文件实现移动端扫码工作台，扫码识别后会直接执行入库或出库，并展示结果摘要。 -->
<script setup lang="ts">
import { BrowserMultiFormatReader, type IScannerControls } from '@zxing/browser'
import type { Exception, Result } from '@zxing/library'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { formatStatus } from '../../../app/displayText'
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
const inboundLocations = computed(() => props.model.state.locations)
const canSubmit = computed(() =>
  mode.value === 'inbound'
    ? Boolean(scanForm.barcode)
    : Boolean(scanForm.barcode),
)

function normalizeScanCode(value: string) {
  return value.trim()
}

function pendingOutboundItems(order: OutboundOrder) {
  return order.items.filter((item) => typeof item.kanbanId === 'number' && Number(item.scannedQty) < Number(item.plannedQty))
}

function scanDuplicateKey(code: string) {
  return `${mode.value}|${normalizeScanCode(code)}`
}

function findMatchedKanban(scanCode: string) {
  const normalized = normalizeScanCode(scanCode)
  if (!normalized) return null
  const direct = props.model.state.kanbans.find((item) => item.barcode === normalized || item.qrContent === normalized)
  if (direct) return direct
  const parts = normalized.split('|')
  if (parts.length === 3 && parts[0] === 'WMS-KANBAN') {
    return props.model.state.kanbans.find((item) => item.barcode === parts[2]) ?? null
  }
  return null
}

function plannedLocationCode(kanban: Kanban | null) {
  if (!kanban) return ''
  const location = props.model.state.locations.find(
    (item) => item.warehouseName === kanban.warehouseName && item.zoneName === kanban.zoneName,
  )
  return location?.locationCode ?? ''
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
      executedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
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
    executedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
  }
}

function pushFeedback(success: boolean, title: string, detail: string) {
  feedbackList.value.unshift({
    success,
    title,
    detail,
    time: new Date().toLocaleString('zh-CN', { hour12: false }),
  })
  feedbackList.value = feedbackList.value.slice(0, 12)
}

async function executeScan(code: string, source: 'camera' | 'manual' | 'image' | 'simulate' = 'manual') {
  const normalized = normalizeScanCode(code)
  if (!normalized || busy.value) return
  lastRecognizedText.value = normalized
  pushFeedback(true, '已识别二维码', source === 'camera' ? '摄像头已识别，正在提交业务。' : `已识别：${normalized}`)
  const duplicateKey = scanDuplicateKey(normalized)
  if (successfulScanKeys.has(duplicateKey)) {
    message.value = '该二维码本次已经执行成功，请扫描下一张。'
    if (source !== 'camera') {
      pushFeedback(false, '重复扫码已忽略', message.value)
    }
    return
  }
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
      lastResult.value = formatResult(resultKanban, result.message || '入库完成，父看板会自动联动子看板。')
      lastResult.value.kanbanNo = fallbackKanbanText(resultKanban, result.scannedKanbanNo)
      lastResult.value.locationText = `父看板 ${result.parentKanbanNo || lastResult.value.kanbanNo} / ${lastResult.value.locationText}`
      message.value = `已入库 ${lastResult.value.kanbanNo}`
      scannerError.value = ''
      successfulScanKeys.add(duplicateKey)
      pushFeedback(true, '入库成功', resultDetail(result.message || `${lastResult.value.kanbanNo} 已入库到 ${lastResult.value.locationText}`, result.affectedCount, result.affectedKanbanNos))
    } else {
      const result = await props.model.actions.scanOutbound({
        barcode: normalized,
        outboundOrderNo: scanForm.outboundOrderNo,
      })
      if (result.outboundOrderNo) {
        scanForm.outboundOrderNo = result.outboundOrderNo
      }
      const resultKanban = findMatchedKanban(normalized) ?? findMatchedKanban(result.barcode) ?? kanban
      lastResult.value = formatResult(resultKanban, result.message || '出库完成，系统已推进对应出库单状态。')
      lastResult.value.kanbanNo = fallbackKanbanText(resultKanban, result.scannedKanbanNo)
      lastResult.value.locationText = `父看板 ${result.parentKanbanNo || lastResult.value.kanbanNo} / 出库单 ${result.outboundOrderNo || scanForm.outboundOrderNo || '自动匹配'}`
      message.value = `已出库 ${lastResult.value.kanbanNo}`
      scannerError.value = ''
      successfulScanKeys.add(duplicateKey)
      pushFeedback(true, '出库成功', resultDetail(result.message || `${lastResult.value.kanbanNo} 已绑定 ${lastResult.value.locationText}`, result.affectedCount, result.affectedKanbanNos))
    }
    scanForm.barcode = ''
  } catch (error) {
    scannerError.value = error instanceof Error ? error.message : '扫码业务执行失败'
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

async function submitScan() {
  if (!canSubmit.value || !scanForm.barcode) return
  await executeScan(scanForm.barcode, 'manual')
}

async function simulateFirst() {
  await nextTick()
  const candidate =
    mode.value === 'inbound'
      ? props.model.state.kanbans.find((item) => item.parentKanban && ['WAIT_SCAN', 'CREATED', 'PARTIAL', 'PARTIAL_INBOUND'].includes(item.status))
      : props.model.state.kanbans.find((item) => item.parentKanban && ['INBOUND', 'PARTIAL_OUTBOUND'].includes(item.status))
  if (!candidate) return
  await executeScan(candidate.qrContent || candidate.barcode, 'simulate')
}

function handleOutboundOrderChange() {
  scanForm.barcode = ''
  scannerError.value = ''
}

watch(mode, () => {
  scanForm.barcode = ''
  scanForm.locationCode = ''
  scanForm.outboundOrderNo = ''
  scannerError.value = ''
  lastResult.value = null
  successfulScanKeys.clear()
  message.value = mode.value === 'inbound' ? '请扫描待入库父看板或箱级子看板。' : '请扫描待出库父看板或箱级子看板。'
})

watch(activeOutboundOrders, (orders) => {
  if (mode.value !== 'outbound' || !scanForm.outboundOrderNo) return
  if (!orders.some((item) => item.outboundNo === scanForm.outboundOrderNo)) {
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
  <WorkModePage v-model="mode" :modes="modes" hint="移动端扫码识别后会直接执行入库或出库；实时摄像头需要 HTTPS 或 localhost，公网 HTTP 可使用拍照识别。">
    <section class="mobile-scan-page">
      <section class="panel">
        <div class="section-head">
          <div>
            <h3>移动扫码</h3>
            <p>父看板扫码时会批量处理子看板，子看板扫码时只处理当前箱。</p>
          </div>
          <div class="action-row">
            <button @click="startScanner">{{ scannerActive ? '重新启动摄像头' : '启动摄像头' }}</button>
            <button class="secondary-button" @click="stopScanner">停止摄像头</button>
            <button class="secondary-button" :disabled="imageScanBusy || busy" @click="triggerImageScan">
              {{ imageScanBusy ? '识别中...' : '拍照识别二维码' }}
            </button>
            <button class="secondary-button" @click="simulateFirst">模拟扫码执行</button>
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
        <p class="scan-support-note">公网 IP 或 HTTP 访问时，手机浏览器通常不会开放实时摄像头；点击“拍照识别二维码”可拍照解码并直接执行当前模式业务。</p>
        <p class="scan-message">{{ message }}</p>
        <p v-if="lastRecognizedText" class="scan-meta">最近识别：{{ lastRecognizedText }}</p>
        <p v-if="lastRequestText" class="scan-meta">{{ lastRequestText }}</p>
        <div v-if="latestFeedback" class="latest-feedback" :class="{ success: latestFeedback.success, fail: !latestFeedback.success }">
          <strong>{{ latestFeedback.title }}</strong>
          <span>{{ latestFeedback.detail }}</span>
          <small>{{ latestFeedback.time }}</small>
        </div>
        <p v-if="scannerError" class="scan-error">{{ scannerError }}</p>
      </section>

      <section class="panel">
        <div class="form-grid two">
          <input v-model="scanForm.barcode" placeholder="手工输入二维码内容 / 条码" />
          <select v-if="mode === 'inbound'" v-model="scanForm.locationCode">
            <option value="">选择入库库位</option>
            <option v-for="item in inboundLocations" :key="item.id" :value="item.locationCode">
              {{ item.locationCode }} | {{ item.warehouseName }} / {{ item.zoneName }}
            </option>
          </select>
          <select v-else v-model="scanForm.outboundOrderNo" @change="handleOutboundOrderChange">
            <option value="">选择出库单</option>
            <option v-for="item in activeOutboundOrders" :key="item.id" :value="item.outboundNo">
              {{ item.outboundNo }} | 待出库 {{ pendingOutboundItems(item).length }} 箱 | {{ item.customerName || '未绑定客户' }} | {{ formatStatus(item.status) }}
            </option>
          </select>
        </div>
        <p v-if="mode === 'outbound' && !activeOutboundOrders.length" class="scan-error">
          当前没有可扫码出库的出库单，请先在出库管理中创建并绑定箱级看板。
        </p>
        <div class="footer-actions">
          <label class="toggle-line"><input v-model="autoExecute" type="checkbox" /> 识别后自动执行</label>
          <button :disabled="!canSubmit || busy || !scanForm.barcode" @click="submitScan">{{ busy ? '执行中...' : '手动执行扫码业务' }}</button>
        </div>
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
  gap: 16px;
}

.scan-video {
  width: 100%;
  min-height: 240px;
  max-height: 48vh;
  background: #111827;
  border-radius: 8px;
  object-fit: cover;
}

.hidden-file-input {
  display: none;
}

.scan-support-note {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.scan-message {
  margin: 12px 0 0;
}

.scan-meta {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
  word-break: break-all;
}

.latest-feedback {
  display: grid;
  gap: 4px;
  margin-top: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
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
  margin: 8px 0 0;
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
  gap: 10px;
}

.feedback-item {
  display: grid;
  gap: 4px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
}

.feedback-item.success {
  border-color: #16a34a;
  background: rgba(22, 163, 74, 0.08);
}

.feedback-item.fail {
  border-color: #dc2626;
  background: rgba(220, 38, 38, 0.08);
}
</style>
