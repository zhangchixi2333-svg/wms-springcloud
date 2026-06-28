<!-- 本文件实现智能助手页面，独立调用 Agent 服务并在服务不可用时局部降级。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../../../api/wms'
import type {
  AgentAnswer,
  AgentDashboard,
  AgentForecastRow,
  AgentHealth,
  AgentOverview,
  AgentRun,
  AgentSuggestion,
  PageModel,
  RagDocument,
} from '../../../types/app'

defineProps<{ model: PageModel }>()

const loading = ref(false)
const analyzing = ref(false)
const serviceError = ref('')
const forecastDays = ref(30)
const health = ref<AgentHealth | null>(null)
const overview = ref<AgentOverview | null>(null)
const forecastRows = ref<AgentForecastRow[]>([])
const suggestions = ref<AgentSuggestion[]>([])
const latestRun = ref<AgentRun | null>(null)
const answer = ref<AgentAnswer | null>(null)
const ragDocuments = ref<RagDocument[]>([])
const question = ref('哪些零件需要补货？')
const ragForm = reactive({
  title: '',
  content: '',
  sourceType: 'MANUAL',
})

const riskSummary = computed(() => [
  { label: '严重不足', value: overview.value?.criticalCount ?? 0, className: 'critical' },
  { label: '低库存', value: overview.value?.lowCount ?? 0, className: 'low' },
  { label: '关注', value: overview.value?.attentionCount ?? 0, className: 'attention' },
])

const visibleForecastRows = computed(() => forecastRows.value)

function riskClass(level: string) {
  return level.toLowerCase()
}

function riskLabel(level: string) {
  const labels: Record<string, string> = {
    CRITICAL: '严重不足',
    LOW: '低库存',
    ATTENTION: '关注',
    NORMAL: '正常',
  }
  return labels[level] ?? level
}

function suggestionTypeLabel(type: string) {
  const labels: Record<string, string> = {
    REPLENISH: '补货建议',
    SLOW_MOVING: '呆滞库存',
  }
  return labels[type] ?? type
}

function setAgentError(error: unknown) {
  serviceError.value = error instanceof Error ? error.message : '智能助手服务不可用'
}

async function loadAgentData() {
  loading.value = true
  serviceError.value = ''
  try {
    applyDashboard(await api.agentDashboard(forecastDays.value))
  } catch (error) {
    setAgentError(error)
  } finally {
    loading.value = false
  }
}

function applyDashboard(data: AgentDashboard) {
  health.value = data.health
  overview.value = data.overview
  forecastRows.value = data.forecastRows
  suggestions.value = data.suggestions
  latestRun.value = data.latestRun
  ragDocuments.value = data.ragDocuments
}

async function runAnalyze() {
  analyzing.value = true
  serviceError.value = ''
  try {
    latestRun.value = await api.runAgentAnalyze(forecastDays.value)
    await loadAgentData()
  } catch (error) {
    setAgentError(error)
  } finally {
    analyzing.value = false
  }
}

async function ask() {
  if (!question.value.trim()) return
  serviceError.value = ''
  answer.value = null
  try {
    answer.value = await api.askAgent({ sessionId: 'workspace', question: question.value })
  } catch (error) {
    setAgentError(error)
  }
}

async function saveRagDocument() {
  if (!ragForm.title.trim() || !ragForm.content.trim()) {
    serviceError.value = '请填写知识标题和内容'
    return
  }
  serviceError.value = ''
  try {
    await api.createRagDocument({
      title: ragForm.title.trim(),
      sourceType: ragForm.sourceType,
      content: ragForm.content.trim(),
      metadataJson: '{}',
      enabled: true,
    })
    ragForm.title = ''
    ragForm.content = ''
    ragDocuments.value = await api.listRagDocuments()
  } catch (error) {
    setAgentError(error)
  }
}

onMounted(loadAgentData)
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>智能助手</h3>
          <p>当前先使用本地规则做库存预测和建议，外部模型调用默认关闭。</p>
        </div>
        <div class="action-row">
          <input v-model.number="forecastDays" class="days-input" type="number" min="1" max="365" />
          <button class="secondary-button" :disabled="loading" @click="loadAgentData">刷新</button>
          <button :disabled="analyzing" @click="runAnalyze">{{ analyzing ? '分析中' : '执行分析' }}</button>
        </div>
      </div>

      <div v-if="serviceError" class="agent-error">
        {{ serviceError }}。这不会影响入库、出库、库存看板等业务功能。
      </div>

      <div class="agent-status-grid">
        <article class="status-card">
          <span>服务状态</span>
          <strong>{{ health?.status ?? (serviceError ? '不可用' : '加载中') }}</strong>
        </article>
        <article class="status-card">
          <span>运行模式</span>
          <strong>{{ health?.mode ?? '本地规则分析模式' }}</strong>
        </article>
        <article class="status-card">
          <span>外部 API</span>
          <strong>{{ health?.callApi ? '已开启' : '关闭' }}</strong>
        </article>
        <article class="status-card">
          <span>RAG 数据库</span>
          <strong>{{ health?.ragEnabled ? health.ragProvider : '本地表已预留' }}</strong>
        </article>
      </div>
    </section>

    <section class="summary-grid">
      <article class="panel summary-card">
        <span>参与预测零件</span>
        <strong>{{ overview?.partCount ?? 0 }}</strong>
      </article>
      <article class="panel summary-card">
        <span>库存总量</span>
        <strong>{{ overview?.currentQty ?? 0 }}</strong>
      </article>
      <article v-for="item in riskSummary" :key="item.label" :class="['panel', 'summary-card', item.className]">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>库存预测</h3>
          <p>按近 30 天出库速度估算未来库存，结合库存阈值给出风险等级。</p>
        </div>
        <span v-if="latestRun" class="run-pill">最近分析：{{ latestRun.runNo }} / {{ latestRun.suggestionCount }} 条建议</span>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>零件</th>
            <th>当前库存</th>
            <th>日均出库</th>
            <th>预测库存</th>
            <th>预计耗尽</th>
            <th>阈值</th>
            <th>建议补货</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!visibleForecastRows.length">
            <td colspan="8">暂无预测数据</td>
          </tr>
          <tr v-for="row in visibleForecastRows" :key="row.partId">
            <td>
              <strong>{{ row.partCode }}</strong>
              <p>{{ row.partName }}</p>
            </td>
            <td>{{ row.currentQty }}</td>
            <td>{{ row.avgDailyOutQty }}</td>
            <td>{{ row.forecastQty }}</td>
            <td>{{ row.estimatedStockoutDate ?? '-' }}</td>
            <td>严重≤{{ row.criticalThreshold }} / 低≤{{ row.lowThreshold }} / 关注≤{{ row.attentionThreshold }}</td>
            <td>{{ row.suggestedReplenishQty }}</td>
            <td><span :class="['risk-badge', riskClass(row.riskLevel)]">{{ row.riskLabel }}</span></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>建议</h3>
          <p>建议会写入 Agent 自己的表，不直接修改业务库存。</p>
        </div>
      </div>
      <div class="suggestion-list">
        <article v-for="item in suggestions" :key="`${item.title}-${item.partCode}-${item.createdAt}`" class="suggestion-card">
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.content }}</p>
          </div>
          <div class="suggestion-tags">
            <span>{{ suggestionTypeLabel(item.suggestionType) }}</span>
            <span>{{ riskLabel(item.riskLevel) }}</span>
            <span>{{ item.targetPageKey ?? '无跳转' }}</span>
          </div>
        </article>
        <p v-if="!suggestions.length" class="hint">暂无建议，点击执行分析生成第一批建议。</p>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>本地问答</h3>
          <p>当前不会调用外部模型，会按库存预测、低库存、呆滞库存等规则回答。</p>
        </div>
      </div>
      <div class="ask-row">
        <input v-model="question" placeholder="例如：哪些零件需要补货？" @keyup.enter="ask" />
        <button @click="ask">提问</button>
      </div>
      <div v-if="answer" class="answer-box">
        <strong>回答</strong>
        <p>{{ answer.answer }}</p>
        <div class="suggestion-list compact">
          <article v-for="item in answer.suggestions" :key="`${item.title}-${item.partCode}`" class="suggestion-card">
            <strong>{{ item.title }}</strong>
            <p>{{ item.content }}</p>
          </article>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>RAG 知识库预留</h3>
          <p>先写入本地 RAG 表并自动切块，后续可接向量库和外部模型。</p>
        </div>
      </div>
      <div class="rag-layout">
        <div class="rag-form">
          <input v-model="ragForm.title" placeholder="知识标题" />
          <textarea v-model="ragForm.content" placeholder="例如：补货策略、看板状态机规则、特殊客户出库规则"></textarea>
          <button @click="saveRagDocument">保存知识</button>
        </div>
        <div class="rag-docs">
          <article v-for="doc in ragDocuments" :key="doc.id" class="doc-card">
            <strong>{{ doc.title }}</strong>
            <span>{{ doc.docKey }} / {{ doc.sourceType }}</span>
            <p>{{ doc.content.slice(0, 120) }}</p>
          </article>
          <p v-if="!ragDocuments.length" class="hint">暂无本地知识文档。</p>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.days-input {
  width: 96px;
}

.agent-error {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid rgba(180, 35, 24, 0.24);
  border-radius: 8px;
  color: var(--danger);
  background: rgba(180, 35, 24, 0.08);
}

.agent-status-grid,
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card,
.summary-card {
  display: grid;
  gap: 8px;
}

.status-card {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f8fbfc;
}

.status-card span,
.summary-card span {
  color: var(--muted);
  font-size: 13px;
}

.summary-card strong {
  font-size: 28px;
}

.summary-card.critical strong,
.risk-badge.critical {
  color: #b42318;
}

.summary-card.low strong,
.risk-badge.low {
  color: #c2410c;
}

.summary-card.attention strong,
.risk-badge.attention {
  color: #b45309;
}

.run-pill,
.risk-badge,
.suggestion-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--surface-alt);
  font-size: 12px;
  font-weight: 700;
}

.table td p {
  margin: 4px 0 0;
  color: var(--muted);
}

.suggestion-list {
  display: grid;
  gap: 12px;
}

.suggestion-list.compact {
  margin-top: 12px;
}

.suggestion-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f8fbfc;
}

.suggestion-card p {
  margin: 0;
  color: var(--muted);
}

.suggestion-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ask-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.answer-box {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.answer-box p {
  margin: 8px 0 0;
}

.rag-layout {
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(0, 1fr);
  gap: 16px;
}

.rag-form {
  display: grid;
  gap: 10px;
  align-content: start;
}

.rag-form textarea {
  min-height: 150px;
  resize: vertical;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  font: inherit;
}

.rag-docs {
  display: grid;
  gap: 10px;
}

.doc-card {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.doc-card span,
.doc-card p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 1000px) {
  .agent-status-grid,
  .summary-grid,
  .rag-layout {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .agent-status-grid,
  .summary-grid,
  .rag-layout,
  .ask-row {
    grid-template-columns: 1fr;
  }
}
</style>
