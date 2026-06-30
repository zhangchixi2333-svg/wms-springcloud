# WMS Agent 智能助手设计与运行指南

更新时间：2026-06-30

`wms-agent-service` 是 WMS 的旁路智能分析服务。它读取业务数据做库存预测、补货建议、问答和 RAG 预留，不直接修改入库、出库、看板、库存等主业务表。

## 一、设计目标

Agent 的目标是辅助操作员和仓库主管：

- 预测未来一段时间库存风险。
- 给出低库存、严重不足、关注库存等建议。
- 回答系统功能、业务流程和库存状态问题。
- 预留 RAG、Redis 记忆、Qdrant 语义检索和 LLM tool call 能力。
- 即使 Agent 没开启或挂掉，也不影响入库、出库、扫码和基础资料页面。

## 二、整体管线

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

## 三、默认降级策略

默认不开外部 AI：

```text
AGENT_CALL_API=false
AGENT_RAG_ENABLED=false
AGENT_REDIS_ENABLED=false
AGENT_LLM_PROVIDER=disabled
AGENT_LLM_MODEL=local-rule
```

关闭这些开关时，Agent 使用本地规则、MySQL 历史消息和 MySQL RAG 切片降级运行。主业务页面不依赖 `/api/agent/**`，所以 Agent 不可用时不应导致网站整体不可用。

## 四、关键代码

| 职责 | 文件 |
| --- | --- |
| API 入口 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\api\AgentController.java` |
| 库存预测、建议、RAG 文档维护 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\service\AgentAnalysisService.java` |
| 配置绑定 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\config\AgentProperties.java` |
| 管线总控 | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\pipeline\AgentPipelineService.java` |
| Planner / Router | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\pipeline\AgentPlanner.java` |
| Memory Manager | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\memory\AgentMemoryManager.java` |
| RAG Retriever | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\rag\AgentRagRetriever.java` |
| Tool Orchestrator | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\tool\AgentToolOrchestrator.java` |
| Reflection | `D:\projects\wms-springcloud\wms-agent-service\src\main\java\com\example\wms\agent\reflection\AgentReflectionService.java` |
| 前端页面 | `D:\projects\wms-springcloud\frontend\src\components\pages\agent\AgentAssistantPage.vue` |

## 五、数据库表

初始化 SQL：

```text
D:\projects\wms-springcloud\sql\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\helm\wms\files\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\kubernetes\mysql\wms-cloud-init.sql
```

Agent 相关表：

| 表 | 用途 |
| --- | --- |
| `agent_run` | 每次分析任务记录 |
| `agent_forecast_snapshot` | 库存预测快照 |
| `agent_suggestion` | 补货、呆滞、风险建议 |
| `agent_chat_message` | 用户与助手历史消息 |
| `agent_user_profile` | 长期画像，记录偏好主题、最近意图、摘要 |
| `agent_rag_document` | RAG 文档原文 |
| `agent_rag_chunk` | 文档切片，后续可补 embedding |
| `agent_pipeline_trace` | 每次问答的路由、工具数量、反思结果和耗时 |
| `agent_config` | Agent 配置预留 |

`agent_pipeline_trace` 是后续性能迭代的关键表。它可以回答“这次慢在 Planner、RAG、工具、数据库，还是反思检查”。

## 六、接口

| 接口 | 用途 |
| --- | --- |
| `GET /api/agent/health` | 健康检查 |
| `GET /api/agent/overview` | Agent 总览 |
| `GET /api/agent/dashboard` | 助手仪表盘 |
| `GET /api/agent/forecast/inventory` | 库存预测 |
| `GET /api/agent/suggestions` | 建议列表 |
| `POST /api/agent/analyze` | 执行分析 |
| `POST /api/agent/ask` | 问答入口 |
| `GET /api/agent/rag/documents` | RAG 文档列表 |
| `POST /api/agent/rag/documents` | 新增 RAG 文档 |

`POST /api/agent/ask` 返回管线信息，前端可以展示：

```json
{
  "answer": "回答正文",
  "callApi": false,
  "traceNo": "TRACE-XXXXXXXX",
  "plan": {
    "intent": "REPLENISHMENT",
    "routeLevel": "L2_RULE",
    "confidence": 0.88
  },
  "rag": {
    "mode": "MySQL 本地切片检索",
    "snippets": []
  },
  "reflection": {
    "passed": true,
    "warnings": []
  },
  "latencyMs": 35
}
```

## 七、本地运行

基础组件：

```powershell
cd D:\projects\wms-springcloud
docker compose up -d mysql
```

可选 Redis 和 Qdrant：

```powershell
docker compose --profile memory up -d redis
docker compose --profile rag up -d qdrant
```

启动服务：

```powershell
cd D:\projects\wms-springcloud
.\start-services.ps1
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

查看追踪：

```powershell
docker exec wms-cloud-mysql mysql -uwms -pwms123456 --default-character-set=utf8mb4 -D wms_cloud -e "SELECT trace_no, route_level, intent, tool_count, latency_ms, created_at FROM agent_pipeline_trace ORDER BY id DESC LIMIT 5;"
```

## 八、Kubernetes 部署

Helm 模板：

```text
D:\projects\wms-springcloud\deploy\helm\wms\templates\agent-service.yaml
```

集群访问链路：

```text
frontend -> /api/agent/**
nginx -> wms-gateway Service
wms-gateway -> http://wms-agent-service:8085
wms-agent-service -> mysql:3306/wms_cloud
```

验证：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get pods,svc,endpoints -n wms -l app=wms-agent-service
kubectl logs deploy/wms-agent-service -n wms --since=3m
```

临时关闭 Agent：

```bash
kubectl scale deployment/wms-agent-service -n wms --replicas=0
```

恢复：

```bash
kubectl scale deployment/wms-agent-service -n wms --replicas=1
kubectl rollout status deployment/wms-agent-service -n wms --timeout=300s
```

## 九、常见问题

| 现象 | 排查命令 | 说明 |
| --- | --- | --- |
| Agent 页面不可用 | `kubectl get svc,endpoints -n wms wms-agent-service` | 先看 Service 是否有 Endpoints |
| 问答很慢 | 查询 `agent_pipeline_trace.latency_ms` | 判断慢在路由、工具、RAG 还是数据库 |
| RAG 没命中 | 查询 `agent_rag_document` 和 `agent_rag_chunk` | 先确认是否有文档和切片 |
| Agent 挂了但主业务正常 | 预期行为 | Agent 是旁路服务，主业务不依赖它 |
