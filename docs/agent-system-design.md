# WMS Agent 智能助手设计与运行指南

更新时间：2026-06-29

## 1. 目标范围

`wms-agent-service` 是 WMS 的旁路智能分析服务，用于库存预测、补货建议、呆滞库存提示、本地规则问答和 RAG 知识库预留。第一阶段不调用外部大模型，默认配置为：

```text
AGENT_CALL_API=false
AGENT_RAG_ENABLED=false
```

旁路含义：Agent 读取业务表做分析，把结果写入 `agent_*` 表；它不修改入库、出库、看板、库存等主业务表。

## 2. 服务边界和关键文件

| 组件 | 路径 | 说明 |
| --- | --- | --- |
| Agent 启动类 | `wms-agent-service/src/main/java/com/example/wms/WmsAgentServiceApplication.java` | Spring Boot 入口 |
| Agent 接口 | `wms-agent-service/src/main/java/com/example/wms/api/AgentController.java` | 暴露 `/api/agent/**` |
| 分析服务 | `wms-agent-service/src/main/java/com/example/wms/service/AgentAnalysisService.java` | 使用 `JdbcTemplate` 聚合业务数据 |
| Agent 配置 | `wms-agent-service/src/main/java/com/example/wms/config/AgentProperties.java` | 映射 `agent.*` 配置 |
| 应用配置 | `wms-agent-service/src/main/resources/application.yml` | 端口、数据源、Agent 开关 |
| 网关路由 | `wms-gateway/src/main/resources/application.yml` | 增加 `/api/agent/**` 路由 |
| Helm 模板 | `deploy/helm/wms/templates/agent-service.yaml` | Agent Deployment 和 Service |

Agent 端口：

```text
wms-agent-service / 8085
```

网关路由：

```yaml
- id: wms-agent-service
  uri: ${WMS_AGENT_SERVICE_URI:lb://wms-agent-service}
  predicates:
    - Path=/api/agent/**
```

Kubernetes 中由 Helm 注入：

```text
WMS_AGENT_SERVICE_URI=http://wms-agent-service:8085
```

## 3. 为什么 Agent 挂了不影响业务

1. `wms-system-service`、`wms-masterdata-service`、`wms-business-service` 没有依赖 `wms-agent-service`。
2. 入库、出库、扫码、库存刷新不会调用 `/api/agent/**`。
3. 前端 Agent 页面自己捕获接口异常，只在该页面显示服务不可用。
4. `useWorkspaceApp.ts` 的主业务刷新没有把 Agent 请求加入全局刷新。
5. Agent 默认不调用外部 API，即使后续打开外部模型，也应该只影响 Agent 自己的问答或建议生成。

## 4. 数据库表和索引

初始化 SQL：

```text
D:\projects\wms-springcloud\sql\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\helm\wms\files\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\kubernetes\mysql\wms-cloud-init.sql
```

Agent 表：

| 表 | 用途 |
| --- | --- |
| `agent_run` | 每次分析任务记录 |
| `agent_forecast_snapshot` | 每次预测快照 |
| `agent_suggestion` | 补货、呆滞、风险建议 |
| `agent_chat_message` | 本地规则问答历史 |
| `agent_rag_document` | RAG 文档原文 |
| `agent_rag_chunk` | 文档切块，后续可补 embedding |
| `agent_config` | Agent 自身配置预留 |

配套性能索引：

| 索引 | 作用 |
| --- | --- |
| `idx_inventory_part` | 加快按零件汇总库存 |
| `idx_inventory_tx_part_created` | 加快库存流水和预测趋势查询 |
| `idx_config_module_status` | 加快库存预警配置查询 |
| `idx_agent_run_started` | 加快最近分析记录查询 |

## 5. 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent/health` | 查看 Agent 是否可用、外部 API/RAG 是否启用 |
| `GET` | `/api/agent/dashboard?days=30` | 一次返回概览、预测和建议，前端优先用这个接口 |
| `GET` | `/api/agent/overview` | 库存风险汇总 |
| `GET` | `/api/agent/forecast/inventory?days=30` | 库存预测明细 |
| `GET` | `/api/agent/suggestions` | 建议列表 |
| `POST` | `/api/agent/analyze?days=30` | 执行一次分析并持久化快照和建议 |
| `POST` | `/api/agent/ask` | 本地规则问答 |
| `GET` | `/api/agent/rag/documents` | 查看本地 RAG 文档 |
| `POST` | `/api/agent/rag/documents` | 新增本地 RAG 文档并切块 |

## 6. 当前分析策略

`AgentAnalysisService` 目前采用本地规则：

- 读取 `inventory` 汇总当前库存。
- 读取 `inventory_transaction` 计算近 N 天平均出库量。
- 读取 `config_item` 中 `inventoryWarning/DEFAULT` 和零件级阈值配置。
- 计算风险等级、预计缺货时间和建议补货量。
- `dashboard` 接口合并概览、预测和建议，避免前端连续请求多个慢接口。
- `dashboard` 有 15 秒内存缓存；执行 `analyze` 后会清理缓存。
- `analyze` 批量写入预测快照和建议，避免逐条插入导致慢。

默认库存预警配置：

```json
{"critical":10,"low":30,"attention":60}
```

## 7. 本地启动和验证

启动 MySQL 和后端：

```powershell
cd D:\projects\wms-springcloud
docker compose up -d mysql
.\start-services.ps1
```

验证 Agent 直连：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8085/api/agent/health'
Invoke-RestMethod 'http://127.0.0.1:8085/api/agent/dashboard?days=30'
```

验证通过网关：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/health'
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/dashboard?days=30'
```

执行一次分析：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/analyze?days=30' -Method Post
```

本地问答：

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

## 8. 前端入口

| 文件 | 说明 |
| --- | --- |
| `frontend/src/components/pages/agent/AgentAssistantPage.vue` | Agent 页面 |
| `frontend/src/api/wms.ts` | Agent API 方法 |
| `frontend/src/types/app.ts` | Agent 数据类型 |
| `frontend/src/app/pageRegistry.ts` | `agentAssistant` 页面注册，使用懒加载 |

初始化 SQL 已新增菜单：

```text
智能助手 -> Agent助手
```

`pageRegistry.ts` 已使用 `defineAsyncComponent` 懒加载页面，避免 Agent 和移动扫码等大页面进入首屏主包。

## 9. Docker、Helm 和 GitHub Actions

构建文件：

```text
D:\projects\wms-springcloud\deploy\docker\Dockerfile.service
D:\projects\wms-springcloud\deploy\docker\build-images.ps1
```

Helm 文件：

```text
D:\projects\wms-springcloud\deploy\helm\wms\templates\agent-service.yaml
D:\projects\wms-springcloud\deploy\helm\wms\values.yaml
```

GitHub Actions 已加入：

```text
wms-agent-service-v${GITHUB_RUN_NUMBER}
wms-agent-service-master
```

部署验证：

```powershell
kubectl get pods -n wms -l app=wms-agent-service
kubectl get svc -n wms wms-agent-service
kubectl get endpoints -n wms wms-agent-service
kubectl logs deploy/wms-agent-service -n wms --tail=120
```

## 10. 可选 RAG 组件

`docker-compose.yml` 预留 Qdrant，但默认不会启动：

```powershell
docker compose up -d mysql
docker compose --profile rag up -d mysql qdrant
```

当前代码只写 MySQL 的 `agent_rag_document` 和 `agent_rag_chunk`。后续开启向量检索时，再设置：

```text
AGENT_RAG_ENABLED=true
AGENT_RAG_PROVIDER=qdrant
```

然后增加 Qdrant 客户端和 embedding 写入逻辑。

## 11. 性能验证记录

最近一次本地优化后的参考结果：

```text
dashboard 首次约 725ms，缓存命中约 1ms，平均约 94ms
forecast 平均约 8.2ms
ask 平均约 31.4ms
analyze 平均约 40.6ms
通过 gateway 访问 dashboard 约 299ms
```

这只是本地参考值，不作为生产 SLA。生产慢时优先查看：

```powershell
kubectl logs deploy/wms-agent-service -n wms --tail=200
kubectl top pods -n wms
kubectl exec -n wms mysql-0 -- mysql -uroot -proot123456 -D wms_cloud -e "EXPLAIN SELECT * FROM inventory LIMIT 1;"
```

## 12. 典型故障

| 现象 | 可能原因 | 排查命令 | 修复 |
| --- | --- | --- | --- |
| Agent 页面显示服务不可用 | Agent 未启动或网关路由错误 | `kubectl logs deploy/wms-agent-service -n wms --tail=120` | 启动 Agent 或修正 `WMS_AGENT_SERVICE_URI` |
| `/api/agent/health` 返回 503 | 网关找不到 Agent Service | `kubectl get svc,endpoints -n wms wms-agent-service` | 检查 Service、Pod label 和端口 |
| Agent CrashLoopBackOff | 表缺失或数据库连接失败 | `kubectl logs deploy/wms-agent-service -n wms --previous --tail=200` | 重新执行初始化 SQL 或检查 DB Secret |
| Agent 慢但主业务正常 | Agent 聚合查询慢或缓存未命中 | 查看 Agent 日志和 MySQL 慢查询 | 检查索引、缩小查询范围、增加缓存 |
| Qdrant 没启动 | 没启用 compose profile | `docker compose ps` | `docker compose --profile rag up -d qdrant` |

## 13. 回滚和临时关闭

Helm 回滚：

```powershell
helm history wms -n wms
helm rollback wms <REVISION> -n wms --wait --timeout 10m
```

只停止 Agent，不影响主业务：

```powershell
kubectl scale deployment/wms-agent-service -n wms --replicas=0
```

恢复：

```powershell
kubectl scale deployment/wms-agent-service -n wms --replicas=1
kubectl rollout status deployment/wms-agent-service -n wms --timeout=300s
```