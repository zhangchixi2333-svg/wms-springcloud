# WMS 仓储管理系统

更新时间：2026-06-30

本项目是一个基于 Spring Cloud、Vue 3、MySQL、Kubernetes/Helm 的仓储管理系统。当前版本已经从早期单体思路重构为微服务项目，并围绕“入库单、出库单、箱级看板、库存流水、移库/转包、封存、移动扫码、Agent 助手”形成完整业务链路。

## 一、核心业务模型

当前系统采用箱级看板模型：

```text
一个看板 = 一个实际包装箱 = 一个当前数量 = 一个当前库位
```

入库单、出库单有自己的单据二维码，但这些二维码不是看板。扫描入库单二维码表示批量入库该入库单下所有待入库箱；扫描出库单二维码表示按出库分配批量执行出库。看板二维码只代表某一个箱子。

重要边界：

| 对象 | 说明 |
| --- | --- |
| 入库单 | 可以是已创建、部分入库、已入库、已退回；部分入库只属于入库单 |
| 看板 | 代表箱子，入库前待扫码，入库后在库；可以部分出库、完全出库、封存、拆分转包 |
| 出库单 | 创建时按 FIFO 锁定可用库存，扫码后才真正扣减库存 |
| 移库/转包 | 不减少系统总库存，只改变库存所在库位；部分转包会拆出新的第三方箱级看板 |
| 封存 | 锁定某个箱级看板，禁止出库、移库或转包 |

详细业务流程见 [docs/wms-business-flow.md](docs/wms-business-flow.md) 和 [docs/kanban-transfer-design.md](docs/kanban-transfer-design.md)。

## 二、项目结构

| 路径 | 说明 |
| --- | --- |
| `wms-discovery` | 本地 Eureka 注册中心，主要用于本地 Spring Cloud 服务发现 |
| `wms-gateway` | 统一 API 网关、CORS、路由入口 |
| `wms-system-service` | 登录、注册、登出、用户、角色、菜单、系统配置 |
| `wms-masterdata-service` | 供应商、客户、零件、器具、仓库/库区 |
| `wms-business-service` | 入库、出库、看板、库存、流水、扫码、移库、转包、封存 |
| `wms-agent-service` | Agent 助手、库存预测、建议、RAG 预留 |
| `wms-common` | 公共响应、异常和通用类 |
| `frontend` | Vue 3 前端 |
| `sql` | 本地数据库初始化和重置 SQL |
| `deploy` | Docker、Kubernetes、Helm、GitHub Actions 部署文件 |
| `docs` | 项目架构、业务流程、部署手册和排障文档 |

## 三、本地快速启动

本地数据库默认配置：

```text
container: wms-cloud-mysql
database:  wms_cloud
host:      127.0.0.1
port:      3317
username:  wms
password:  wms123456
root:      root123456
```

在 PowerShell 中执行：

```powershell
cd D:\projects\wms-springcloud
docker compose up -d mysql
docker cp D:\projects\wms-springcloud\sql\wms-cloud-init.sql wms-cloud-mysql:/tmp/wms-cloud-init.sql
docker exec wms-cloud-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 -D wms_cloud -e "SOURCE /tmp/wms-cloud-init.sql;"
.\start-services.ps1
```

启动前端：

```powershell
cd D:\projects\wms-springcloud\frontend
npm.cmd install
npm.cmd run dev -- --host 0.0.0.0
```

访问地址：

```text
前端：http://localhost:5173
网关：http://127.0.0.1:8080/api
```

默认账号：

```text
admin / admin123
manager / manager123
operator / operator123
```

停止本地后端和前端进程：

```powershell
cd D:\projects\wms-springcloud
.\stop-services.ps1
```

## 四、初始化数据说明

`sql/wms-cloud-init.sql` 会清空入库单、出库单、看板、库存、库存流水和 Agent 运行记录等生产业务数据，然后写入基础资料：

- 5 个供应商。
- 3 个客户。
- 7 个库位，包含自有仓和第三方仓。
- 5 个器具，带默认包装容量。
- 40 个零件，分配到 5 个供应商。
- 默认角色、用户、菜单、角色菜单关系。
- 默认库存预警配置。

初始状态下不会保留入库单、出库单、看板、库存和库存流水。测试业务需要从系统页面重新创建。

## 五、常用验证命令

后端编译：

```powershell
cd D:\projects\wms-springcloud
mvn.cmd -pl wms-business-service,wms-masterdata-service,wms-system-service,wms-agent-service,wms-gateway,wms-discovery -am test -DskipTests
```

前端构建：

```powershell
cd D:\projects\wms-springcloud\frontend
npm.cmd run build
```

检查初始化结果：

```powershell
docker exec wms-cloud-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 -D wms_cloud -e "SELECT COUNT(*) suppliers FROM supplier; SELECT COUNT(*) parts FROM part; SELECT COUNT(*) inbound_orders FROM inbound_order; SELECT COUNT(*) outbound_orders FROM outbound_order; SELECT COUNT(*) kanbans FROM kanban; SELECT COUNT(*) inventory_rows FROM inventory;"
```

预期初始状态：

```text
suppliers = 5
parts = 40
inbound_orders = 0
outbound_orders = 0
kanbans = 0
inventory_rows = 0
```

## 六、文档导航

| 文档 | 内容 |
| --- | --- |
| [docs/README.md](docs/README.md) | 文档总索引 |
| [docs/microservice-design.md](docs/microservice-design.md) | 微服务架构、服务边界、Service 发现方式 |
| [docs/wms-business-flow.md](docs/wms-business-flow.md) | 入库、出库、扫码、打印、库存流水 |
| [docs/kanban-transfer-design.md](docs/kanban-transfer-design.md) | 箱级看板、移库、转包、封存模型 |
| [docs/agent-system-design.md](docs/agent-system-design.md) | Agent 助手设计和降级策略 |
| [deploy/README.md](deploy/README.md) | Docker、Kubernetes、Helm 部署总说明 |
| [deploy/github-actions.md](deploy/github-actions.md) | GitHub Actions 自动构建、推送、部署说明 |
