# WMS Spring Cloud 微服务化说明

更新时间：2026-06-29

本文说明 `D:\projects\wms-springcloud` 当前后端如何从单体 WMS 拆成 Spring Cloud 多模块服务，以及数据库、网关、Kubernetes Service 和 Agent 智能助手之间的边界。

## 1. 当前服务拆分

| 服务 | 端口 | 职责 | 主要接口 |
| --- | --- | --- | --- |
| `wms-discovery` | `8761` | 本地 Spring Cloud/Eureka 注册中心；Kubernetes 下保留但业务流量不依赖它 | `/` |
| `wms-gateway` | `8080` | 前端统一 API 入口、CORS、路径路由 | `/api/**` |
| `wms-system-service` | `8082` | 登录、注册、用户、角色、菜单、系统配置 | `/api/auth/**`、`/api/users/**`、`/api/roles/**`、`/api/menus/**`、`/api/config-items/**` |
| `wms-masterdata-service` | `8083` | 供应商、客户、零件、器具、仓库/库区 | `/api/suppliers/**`、`/api/customers/**`、`/api/parts/**`、`/api/equipment/**`、`/api/locations/**` |
| `wms-business-service` | `8084` | 入库、出库、看板、库存、扫码、转包、移库 | `/api/inbound-orders/**`、`/api/outbound-orders/**`、`/api/kanbans/**`、`/api/inventory/**`、`/api/mobile/scan/**` |
| `wms-agent-service` | `8085` | 库存预测、补货建议、呆滞提示、本地问答和 RAG 预留 | `/api/agent/**` |

前端默认只访问一个后端入口：

```text
http://127.0.0.1:8080/api
```

部署到 Kubernetes 后，前端 nginx 使用同源 `/api` 转发到集群内的 `wms-gateway` Service。

## 2. 网关和跨域边界

跨域只由 `wms-gateway` 统一处理。下游服务不再单独配置 CORS，避免响应头变成：

```text
Access-Control-Allow-Origin: http://localhost:5173, http://localhost:5173
```

网关路由文件：

```text
D:\projects\wms-springcloud\wms-gateway\src\main\resources\application.yml
```

关键路由模式：

```yaml
uri: ${WMS_SYSTEM_SERVICE_URI:lb://wms-system-service}
uri: ${WMS_MASTERDATA_SERVICE_URI:lb://wms-masterdata-service}
uri: ${WMS_BUSINESS_SERVICE_URI:lb://wms-business-service}
uri: ${WMS_AGENT_SERVICE_URI:lb://wms-agent-service}
```

这表示：

- 本地没有注入环境变量时，默认走 `lb://服务名`，可配合 Eureka/Spring Cloud LoadBalancer。
- Kubernetes 部署时，Helm 注入 `http://ServiceName:Port`，网关直接通过 Kubernetes Service DNS 转发。

## 3. 为什么第一阶段共用一个新库

当前仓储核心业务存在强事务关系：

- 入库、出库、扫码会同时修改看板、库存和库存流水。
- 看板生命周期依赖入库单、出库单和箱级子看板状态。
- 业务服务还需要读取零件、供应商、客户、库位、器具等主数据。

因此当前阶段采用：

```text
服务按业务边界拆分
数据库新建为 wms_cloud
多个服务暂时共享同一个新库
```

后续若继续演进，可以拆成：

```text
wms_system      用户、角色、菜单、配置
wms_masterdata  供应商、客户、零件、器具、库位
wms_business    入库、出库、看板、库存、流水
wms_agent       预测快照、建议、知识库切块
```

拆库后，需要把业务服务直接读主数据的逻辑改为 OpenFeign、WebClient、事件同步读模型或专门的查询模型。

## 4. 数据库和初始化 SQL

本地 MySQL：

```text
container: wms-cloud-mysql
database:  wms_cloud
host:      127.0.0.1
port:      3317
username:  wms
password:  wms123456
```

启动 MySQL：

```powershell
cd D:\projects\wms-springcloud
docker compose up -d mysql
```

手动初始化：

```powershell
docker cp D:\projects\wms-springcloud\sql\wms-cloud-init.sql wms-cloud-mysql:/tmp/wms-cloud-init.sql
docker exec wms-cloud-mysql sh -c "mysql -uwms -pwms123456 --default-character-set=utf8mb4 < /tmp/wms-cloud-init.sql"
```

三份初始化 SQL 必须保持一致：

```text
D:\projects\wms-springcloud\sql\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\helm\wms\files\wms-cloud-init.sql
D:\projects\wms-springcloud\deploy\kubernetes\mysql\wms-cloud-init.sql
```

初始化脚本只写入系统骨架数据：账号、角色、菜单、角色菜单、系统配置、默认库存预警和 `agent_*` 表结构。它不写入供应商、客户、零件、入库单、出库单、看板、库存、库存流水等业务演示数据。

默认账号：

```text
admin / admin123
manager / manager123
operator / operator123
```

## 5. 启动顺序

推荐顺序：

```text
1. MySQL
2. wms-discovery
3. wms-system-service
4. wms-masterdata-service
5. wms-business-service
6. wms-agent-service
7. wms-gateway
8. frontend
```

一键启动后端：

```powershell
cd D:\projects\wms-springcloud
.\start-services.ps1
```

启动前端：

```powershell
cd D:\projects\wms-springcloud\frontend
npm install
npm run dev
```

停止本地服务：

```powershell
cd D:\projects\wms-springcloud
.\stop-services.ps1
```

停止 MySQL 但保留数据：

```powershell
docker compose stop mysql
```

不要执行 `docker compose down -v`，否则会删除数据库 volume。

## 6. 验证命令

验证后端编译：

```powershell
cd D:\projects\wms-springcloud
mvn.cmd -DskipTests compile
```

验证登录接口走网关：

```powershell
$body = @{
  username = 'admin'
  password = 'admin123'
} | ConvertTo-Json

Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/login' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

验证 Agent 旁路服务：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/health'
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/dashboard?days=30'
```

## 7. 当前边界

已经完成：

- 后端多模块拆分：Discovery、Gateway、System、Masterdata、Business、Agent。
- 新库 `wms_cloud` 和幂等初始化 SQL。
- Kubernetes 下通过 Service DNS 访问下游服务。
- Agent 默认不调用外部 API，挂了不影响主业务链路。
- 前端页面按需加载，避免首屏一次性加载所有业务页面。

仍建议后续演进：

- 继续细化网关鉴权与菜单权限。
- 将跨服务数据读取逐步收敛为 Feign/WebClient 或事件同步读模型。
- 逐步拆分物理数据库。
- 增加接口契约测试和关键扫码流程自动化测试。