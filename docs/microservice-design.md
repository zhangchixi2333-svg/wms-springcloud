# WMS Spring Cloud 微服务化说明

更新时间：2026-06-08

本文说明 `D:\projects\wms-springcloud` 如何从原单体 WMS 拆分为 Spring Cloud 微服务项目，以及新数据库如何初始化。

## 1. 服务拆分

现有单体项目的业务边界比较清晰：

| 服务 | 端口 | 职责 | 主要接口 |
| --- | --- | --- | --- |
| `wms-discovery` | `8761` | Eureka 注册中心 | `/` |
| `wms-gateway` | `8080` | 前端统一入口，按路径路由到后端服务 | `/api/**` |
| `wms-system-service` | `8082` | 登录、注册、用户、角色、菜单、配置项 | `/api/auth/**`、`/api/users/**`、`/api/roles/**`、`/api/menus/**`、`/api/config-items/**` |
| `wms-masterdata-service` | `8083` | 供应商、客户、零件、器具、仓库/库区 | `/api/suppliers/**`、`/api/customers/**`、`/api/parts/**`、`/api/equipment/**`、`/api/locations/**` |
| `wms-business-service` | `8084` | 入库、出库、看板、库存、扫码、转包、移库 | `/api/inbound-orders/**`、`/api/outbound-orders/**`、`/api/kanbans/**`、`/api/inventory/**`、`/api/mobile/scan/**` |

前端仍然只访问一个地址：

```text
http://127.0.0.1:8080/api
```

跨域也只由 `wms-gateway` 统一处理。下游的 `wms-system-service`、`wms-masterdata-service`、`wms-business-service` 不再配置 CORS，避免响应头被重复写成：

```text
Access-Control-Allow-Origin: http://localhost:5173, http://localhost:5173
```

## 2. 为什么这样拆

第一阶段目标是先完成可运行的微服务化，而不是一上来强行把所有数据库关系拆断。

当前仓储业务 `WmsService` 同时依赖：

- 入库单、出库单、看板、库存流水。
- 零件、供应商、客户、库位、器具等主数据。
- 出库扫码、转包、移库需要在同一个业务事务内更新库存、看板和流水。

因此当前版本采用：

```text
服务按业务边界拆分
数据库新建为 wms_cloud
第一阶段多服务共享同一个新库
```

后续如果继续演进，可以把数据库再拆为：

```text
wms_system     用户、角色、菜单、配置
wms_masterdata 供应商、客户、零件、器具、库位
wms_business   入库、出库、看板、库存、流水
```

拆分数据库后，需要把业务服务对主数据的直接 JPA 查询改为 OpenFeign 调用或本地冗余读模型。

## 3. 新数据库

数据库容器：

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

初始化：

```powershell
docker cp D:\projects\wms-springcloud\sql\wms-cloud-init.sql wms-cloud-mysql:/tmp/wms-cloud-init.sql
docker exec wms-cloud-mysql sh -c "mysql -uwms -pwms123456 --default-character-set=utf8mb4 < /tmp/wms-cloud-init.sql"
```

验证：

```powershell
docker exec wms-cloud-mysql mysql -uwms -pwms123456 --default-character-set=utf8mb4 wms_cloud -e "SELECT COUNT(*) AS tables_count FROM information_schema.tables WHERE table_schema='wms_cloud'; SELECT username, display_name, role_name FROM app_user ORDER BY id;"
```

默认账号：

```text
admin / admin123
manager / manager123
operator / operator123
```

## 4. 启动顺序

推荐顺序：

```text
1. MySQL
2. wms-discovery
3. wms-system-service
4. wms-masterdata-service
5. wms-business-service
6. wms-gateway
7. frontend
```

一键启动后端服务：

```powershell
cd D:\projects\wms-springcloud
.\start-services.ps1
```

启动前端：

```powershell
cd D:\projects\wms-springcloud\frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

`frontend/vite.config.ts` 已固定 `server.host=127.0.0.1`、`server.port=5173`，直接运行 `npm run dev` 也会监听同一地址。

停止前后端服务：

```powershell
cd D:\projects\wms-springcloud
.\stop-services.ps1
```

停止 MySQL 但保留数据：

```powershell
docker compose stop mysql
```

不要执行：

```powershell
docker compose down -v
```

否则会删除 `wms_cloud` 的 Docker volume。

## 5. 验证网关

登录接口走网关：

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

预期返回：

```text
success = true
data.token 有值
data.user.username = admin
```

## 6. 当前边界和后续优化

当前已经完成：

- 从单体项目拆出 Eureka、Gateway、系统服务、基础资料服务、仓储业务服务。
- 新建 `wms_cloud` 数据库和初始化 SQL。
- 前端 API 默认走网关 `8080`。
- 后端多模块 Maven 编译通过。

后续建议：

- 在网关新增统一鉴权过滤器，调用系统服务校验 token 和权限。
- 将业务服务对主数据的直接数据库读取改为 Feign 调用或事件同步读模型。
- 进一步拆分物理数据库，避免服务之间共享表。
- 增加接口契约测试，保证网关路由和服务接口不漂移。
