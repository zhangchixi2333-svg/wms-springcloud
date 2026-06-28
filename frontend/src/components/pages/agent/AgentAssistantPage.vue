<!-- 本文件实现 Agent 助手页面，独立展示预测、建议、问答管线、RAG 知识和降级状态。 -->
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
const asking = ref(false)
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

const visibleForecastRows = computed(() => forecastRows.value.slice(0, 50))

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

function intentLabel(intent?: string) {
  const labels: Record<string, string> = {
    RUN_ANALYSIS: '执行分析',
    HEALTH_CHECK: '健康检查',
    REPLENISHMENT: '补货预警',
    FORECAST: '库存预测',
    SLOW_MOVING: '呆滞库存',
    MENU_SECURITY: '菜单权限',
    RAG_QA: '知识问答',
    LOCAL_FALLBACK: '本地兜底',
    LLM_TOOL_CALL: '模型工具',
  }
  return intent ? labels[intent] ?? intent : '-'
}

function setAgentError(error: unknown) {
  serviceError.value = error instanceof Error ? error.message : '智能助手服务不可用'
}

function applyDashboard(data: AgentDashboard) {
  health.value = data.health
  overview.value = data.overview
  forecastRows.value = data.forecastRows
  suggestions.value = data.suggestions
  latestRun.value = data.latestRun
  ragDocuments.value = data.ragDocuments
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
  asking.value = true
  serviceError.value = ''
  answer.value = null
  try {
    answer.value = await api.askAgent({ sessionId: 'workspace', question: question.value })
  } catch (error) {
    setAgentError(error)
  } finally {
    asking.value = false
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
          <h3>Agent 助手</h3>
          <p>按本地规则、记忆、RAG 和工具编排进行仓储分析；外部模型默认关闭，Agent 异常不会影响主业务。</p>
        </div>
        <div class="action-row">
          <input v-model.number="forecastDays" class="days-input" type="number" min="1" max="365" />
          <button class="secondary-button" :disabled="loading" @click="loadAgentData">刷新</button>
          <button :disabled="analyzing" @click="runAnalyze">{{ analyzing ? '分析中' : '执行分析' }}</button>
        </div>
      </div>

      <div v-if="serviceError" class="agent-error">
        {{ serviceError }}。这不会影响入库、出库、库存和看板等业务功能。
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
          <span>LLM</span>
          <strong>{{ health?.callApi ? `${health.llmProvider ?? '外部'} / ${health.llmModel ?? '-'}` : '关闭' }}</strong>
        </article>
        <article class="status-card">
          <span>记忆与 RAG</span>
          <strong>{{ health?.memoryMode ?? 'MySQL 本地记忆' }}</strong>
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
          <h3>本地问答管线</h3>
          <p>问题会经过 Planner、Memory、RAG、Tool Orchestrator、Reflection 后返回。</p>
        </div>
      </div>
      <div class="ask-row">
        <input v-model="question" placeholder="例如：哪些零件需要补货？" @keyup.enter="ask" />
        <button :disabled="asking" @click="ask">{{ asking ? '处理中' : '提问' }}</button>
      </div>

      <div v-if="answer" class="answer-box">
        <div class="answer-head">
          <div>
            <strong>回答</strong>
            <p>{{ answer.answer }}</p>
          </div>
          <span class="run-pill">{{ answer.latencyMs }} ms</span>
        </div>

        <div class="pipeline-grid">
          <article class="trace-card">
            <span>路由</span>
            <strong>{{ answer.plan.routeLabel }}</strong>
            <p>{{ intentLabel(answer.plan.intent) }} / 置信度 {{ Math.round(answer.plan.confidence * 100) }}%</p>
          </article>
          <article class="trace-card">
            <span>追踪号</span>
            <strong>{{ answer.traceNo }}</strong>
            <p>{{ answer.reflection.passed ? '反思检查通过' : '存在反思警告' }}</p>
          </article>
          <article class="trace-card">
            <span>RAG</span>
            <strong>{{ answer.rag.snippets.length }} 条命中</strong>
            <p>{{ answer.rag.mode }}</p>
          </article>
        </div>

        <div class="trace-section">
          <h4>工具结果</h4>
          <div class="trace-list">
            <article v-for="tool in answer.toolResults" :key="tool.toolName" class="trace-row">
              <strong>{{ tool.toolLabel }}</strong>
              <span :class="tool.success ? 'ok-text' : 'danger-text'">{{ tool.success ? '成功' : '失败' }}</span>
              <p>{{ tool.summary }} / {{ tool.latencyMs }} ms</p>
            </article>
          </div>
        </div>

        <div v-if="answer.rag.snippets.length" class="trace-section">
          <h4>知识命中</h4>
          <div class="trace-list">
            <article v-for="snippet in answer.rag.snippets" :key="snippet.chunkId" class="trace-row">
              <strong>{{ snippet.title }}</strong>
              <span>得分 {{ snippet.score.toFixed(2) }}</span>
              <p>{{ snippet.content.slice(0, 160) }}</p>
            </article>
          </div>
        </div>

        <div class="trace-section">
          <h4>反思检查</h4>
          <div class="suggestion-tags">
            <span v-for="check in answer.reflection.checks" :key="check">{{ check }}</span>
            <span v-for="warning in answer.reflection.warnings" :key="warning" class="warning-chip">{{ warning }}</span>
          </div>
        </div>

        <div v-if="answer.suggestions.length" class="suggestion-list compact">
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
          <h3>库存预测</h3>
          <p>按近 30 天出库速度估算未来库存，并结合库存阈值给出风险等级。</p>
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
          <p>建议只写入 Agent 自己的表，不直接修改业务库存。</p>
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
.summary-grid,
.pipeline-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.pipeline-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 14px;
}

.status-card,
.summary-card,
.trace-card {
  display: grid;
  gap: 8px;
}

.status-card,
.trace-card {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f8fbfc;
}

.status-card span,
.summary-card span,
.trace-card span {
  color: var(--muted);
  font-size: 13px;
}

.status-card strong,
.trace-card strong {
  overflow-wrap: anywhere;
}

.summary-card strong {
  font-size: 28px;
}

.summary-card.critical strong,
.risk-badge.critical,
.danger-text {
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

.ok-text {
  color: #067647;
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

.warning-chip {
  color: #b45309;
  background: rgba(245, 158, 11, 0.12) !important;
}

.table td p {
  margin: 4px 0 0;
  color: var(--muted);
}

.suggestion-list,
.trace-list {
  display: grid;
  gap: 12px;
}

.suggestion-list.compact {
  margin-top: 12px;
}

.suggestion-card,
.trace-row {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f8fbfc;
}

.suggestion-card p,
.trace-row p,
.trace-card p {
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

.answer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.answer-head p {
  margin: 8px 0 0;
  white-space: pre-line;
}

.trace-section {
  margin-top: 14px;
}

.trace-section h4 {
  margin: 0 0 8px;
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
  .pipeline-grid,
  .rag-layout {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .agent-status-grid,
  .summary-grid,
  .pipeline-grid,
  .rag-layout,
  .ask-row {
    grid-template-columns: 1fr;
  }

  .answer-head {
    display: grid;
  }
}
</style>
