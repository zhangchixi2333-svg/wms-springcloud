# WMS Agent 智能助手设计与运行指南

更新时间：2026-06-29

## 1. 整体链路

`wms-agent-service` 是 WMS 的旁路智能分析服务。它读取业务数据做预测、建议和问答，把结果写入 `agent_*` 表；它不直接修改入库、出库、库存、看板等主业务表。

当前问答链路已经拆成管线：

```text
用户问题
-> Planner / Router
   -> L1 精准命令
   -> L2 规则匹配
   -> L3 本地语义匹配
   -> L4 LLM tool call 预留
-> Memory Manager
   -> MySQL 历史消息
   -> MySQL 长期画像
   -> Redis 最近对话预留
   -> Qdrant 语义记忆预留
-> RAG Retriever
   -> MySQL 文档 chunk
   -> 本地关键词/语义打分
   -> Qdrant topK 预留
-> Tool Orchestrator
   -> 库存预测工具
   -> 补货建议工具
   -> 菜单权限工具
   -> RAG 搜索工具
-> Reflection
   -> 事实一致性
   -> 权限隐私
   -> 业务规则
   -> 完整性检查
-> 返回前端
```

默认仍然不开外部 AI：

```text
AGENT_CALL_API=false
AGENT_RAG_ENABLED=false
AGENT_REDIS_ENABLED=false
AGENT_LLM_PROVIDER=disabled
```

这几个开关关闭时，Agent 会使用本地规则、MySQL 记忆和 MySQL RAG 切片降级，不影响主业务页面。

## 2. 关键代码文件

| 职责 | 文件 |
| --- | --- |
| API 入口 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\api\AgentController.java` |
| 库存预测、建议、RAG 文档维护 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\service\AgentAnalysisService.java` |
| Agent 配置 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\config\AgentProperties.java` |
| 管线总控 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\pipeline\AgentPipelineService.java` |
| Planner / Router | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\pipeline\AgentPlanner.java` |
| Memory Manager | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\memory\AgentMemoryManager.java` |
| RAG Retriever | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\rag\AgentRagRetriever.java` |
| Tool Orchestrator | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\tool\AgentToolOrchestrator.java` |
| Reflection | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\reflection\AgentReflectionService.java` |
| 前端页面 | `D:\projects\wms-springcloud\frontend\src\components\pages\agent\AgentAssistantPage.vue` |
| 前端类型 | `D:\projects\wms-springcloud\frontend\src\types\app.ts` |

## 3. 数据库表

初始化 SQL 已同步到三处：

```text
D:\projects\wms-springcloud\sql\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\helm\wms\files\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\kubernetes\mysql\wms-cloud-init.sql
```

Agent 表：

| 表 | 用途 |
| --- | --- |
| `agent_run` | 每次分析任务记录 |
| `agent_forecast_snapshot` | 库存预测快照 |
| `agent_suggestion` | 补货、呆滞、风险建议 |
| `agent_chat_message` | 用户与助手历史消息 |
| `agent_user_profile` | 长期画像，记录偏好主题、最近意图、摘要 |
| `agent_rag_document` | RAG 文档原文 |
| `agent_rag_chunk` | 文档切片，后续可补 embedding |
| `agent_pipeline_trace` | 每次问答的路由、工具数量、反思结果、耗时 |
| `agent_config` | Agent 自身配置预留 |

`agent_pipeline_trace` 是后续性能迭代的关键表。它能回答：“这次慢在哪里，是 Planner、RAG、工具，还是数据库？”

## 4. 问答接口返回内容

`POST /api/agent/ask` 现在返回完整管线结果，前端可以直接展示：

```json
{
  "answer": "回答正文",
  "callApi": false,
  "suggestions": [],
  "traceNo": "TRACE-XXXXXXXX",
  "plan": {
    "intent": "REPLENISHMENT",
    "routeLevel": "L2_RULE",
    "routeLabel": "L2 规则匹配",
    "confidence": 0.88,
    "reason": "问题包含补货/低库存关键词，使用规则匹配到库存建议工具。"
  },
  "memory": {
    "recentSource": "MySQL 历史消息；Redis 最近对话为预留能力，未启用时自动降级。"
  },
  "rag": {
    "mode": "MySQL 本地切片检索。",
    "snippets": []
  },
  "toolResults": [],
  "reflection": {
    "passed": true,
    "checks": [],
    "warnings": []
  },
  "latencyMs": 35
}
```

前端页面会展示路由层级、意图、工具结果、RAG 命中、反思检查和耗时。

## 5. 当前已实现与预留

已实现：

- L1 精准命令：`执行分析`、`刷新库存预测`、`健康检查`。
- L2 规则匹配：补货、低库存、预测、呆滞、菜单权限。
- L3 本地语义匹配：MySQL RAG 文档切片检索。
- Memory：MySQL 历史消息、MySQL 长期画像。
- Tool：库存预测、补货建议、执行分析、菜单权限、RAG 搜索、健康检查。
- Reflection：事实一致性、权限隐私、业务规则、完整性检查。
- Trace：每次问答写入 `agent_pipeline_trace`。

预留：

- Redis 最近对话：`docker compose --profile memory up -d redis` 可拉起组件，但当前代码默认不依赖它。
- Qdrant 语义记忆：`docker compose --profile rag up -d qdrant` 可拉起组件，当前代码在未接 Java 客户端时降级到 MySQL RAG。
- LLM tool call：`AGENT_CALL_API=true` 后可继续接模型提供方，现在仍返回本地预留说明。

## 6. 本地启动和验证

启动基础组件：

```powershell
cd D:\projects\wms-springcloud
docker compose up -d mysql
```

可选启动 Redis 和 Qdrant：

```powershell
docker compose --profile memory up -d redis
docker compose --profile rag up -d qdrant
```

启动后端和前端：

```powershell
cd D:\projects\wms-springcloud
.\start-services.ps1

cd D:\projects\wms-springcloud\frontend
npm run dev
```

验证健康检查：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/health'
```

验证问答：

```powershell
$body = @{
  sessionId = 'manual-test'
  question = '哪些零件需要补货？'
} | ConvertTo-Json

Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/ask' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

验证追踪表：

```powershell
docker exec wms-cloud-mysql sh -c "mysql -uwms -pwms123456 --default-character-set=utf8mb4 -D wms_cloud -e 'SELECT trace_no, route_level, intent, tool_count, latency_ms, created_at FROM agent_pipeline_trace ORDER BY id DESC LIMIT 5;'"
```

## 7. 部署配置

Helm 模板：

```text
D:\projects\wms-springcloud\deploy\helm\wms\templates\agent-service.yaml
```

原生 Kubernetes YAML：

```text
D:\projects\wms-springcloud\deploy\kubernetes\services\agent-service.yaml
```

Agent 默认环境变量：

```text
AGENT_CALL_API=false
AGENT_RAG_ENABLED=false
AGENT_RAG_PROVIDER=local
AGENT_REDIS_ENABLED=false
AGENT_LLM_PROVIDER=disabled
AGENT_LLM_MODEL=local-rule
```

网关访问 Agent：

```text
frontend -> /api/agent/**
nginx -> wms-gateway Service
wms-gateway -> http://wms-agent-service:8085
wms-agent-service -> mysql:3306/wms_cloud
```

## 8. 故障排查

Agent 页面不可用：

```powershell
kubectl get pods,svc,endpoints -n wms -l app=wms-agent-service
kubectl logs deploy/wms-agent-service -n wms --tail=200
```

问答很慢：

```powershell
kubectl logs deploy/wms-agent-service -n wms --tail=200
kubectl exec -n wms mysql-0 -- mysql -uroot -proot123456 -D wms_cloud -e "SELECT route_level, intent, AVG(latency_ms) avg_ms, COUNT(*) cnt FROM agent_pipeline_trace GROUP BY route_level, intent ORDER BY avg_ms DESC;"
```

RAG 没命中：

```powershell
kubectl exec -n wms mysql-0 -- mysql -uroot -proot123456 -D wms_cloud -e "SELECT COUNT(*) docs FROM agent_rag_document; SELECT COUNT(*) chunks FROM agent_rag_chunk;"
```

Agent 挂了但主业务正常：

这是预期边界。Agent 是旁路服务，入库、出库、库存、看板不会依赖 `/api/agent/**`。可以临时关闭：

```powershell
kubectl scale deployment/wms-agent-service -n wms --replicas=0
```

恢复：

```powershell
kubectl scale deployment/wms-agent-service -n wms --replicas=1
kubectl rollout status deployment/wms-agent-service -n wms --timeout=300s
```
