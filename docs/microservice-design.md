# WMS Spring Cloud 微服务架构说明

更新时间：2026-06-30

本文说明 `D:\projects\wms-springcloud` 的微服务拆分、网关路由、数据库边界、本地服务发现和 Kubernetes Service 通信方式。

## 一、整体链路

本地开发链路：

```text
浏览器
-> Vue Vite dev server http://localhost:5173
-> wms-gateway http://127.0.0.1:8080/api
-> Spring Cloud LoadBalancer / Eureka
-> system/masterdata/business/agent 服务
-> MySQL 127.0.0.1:3317/wms_cloud
```

Kubernetes 部署链路：

```text
浏览器
-> wms-frontend Service / NodePort / Ingress
-> nginx
-> /api 转发到 http://wms-gateway:8080/api
-> Spring Cloud Gateway
-> Kubernetes Service DNS
-> system/masterdata/business/agent 服务
-> mysql Service
-> mysql StatefulSet
```

## 二、服务拆分

| 服务 | 端口 | 模块路径 | 职责 | 主要接口 |
| --- | --- | --- | --- | --- |
| `wms-discovery` | `8761` | `wms-discovery` | 本地 Eureka 注册中心 | `/` |
| `wms-gateway` | `8080` | `wms-gateway` | 统一 API 入口、CORS、路由 | `/api/**` |
| `wms-system-service` | `8082` | `wms-system-service` | 登录、注册、用户、角色、菜单、配置 | `/api/auth/**`, `/api/users/**`, `/api/roles/**`, `/api/menus/**`, `/api/config-items/**` |
| `wms-masterdata-service` | `8083` | `wms-masterdata-service` | 供应商、客户、零件、器具、库位 | `/api/suppliers/**`, `/api/customers/**`, `/api/parts/**`, `/api/equipment/**`, `/api/locations/**` |
| `wms-business-service` | `8084` | `wms-business-service` | 入库、出库、看板、库存、流水、扫码、移库、转包、封存 | `/api/inbound-orders/**`, `/api/outbound-orders/**`, `/api/kanbans/**`, `/api/inventory/**`, `/api/mobile/scan/**` |
| `wms-agent-service` | `8085` | `wms-agent-service` | Agent 助手、库存预测、建议、RAG 预留 | `/api/agent/**` |
| `frontend` | `5173`/`80` | `frontend` | Vue 3 前端，生产镜像内由 nginx 承载 | `/` |

## 三、网关路由

网关配置文件：

```text
D:\projects\wms-springcloud\wms-gateway\src\main\resources\application.yml
```

核心路由：

```yaml
routes:
  - id: wms-system-service
    uri: ${WMS_SYSTEM_SERVICE_URI:lb://wms-system-service}
    predicates:
      - Path=/api/auth/**,/api/menus/**,/api/users/**,/api/roles/**,/api/config-items/**
  - id: wms-masterdata-service
    uri: ${WMS_MASTERDATA_SERVICE_URI:lb://wms-masterdata-service}
    predicates:
      - Path=/api/suppliers/**,/api/customers/**,/api/equipment/**,/api/parts/**,/api/locations/**
  - id: wms-business-service
    uri: ${WMS_BUSINESS_SERVICE_URI:lb://wms-business-service}
    predicates:
      - Path=/api/inbound-orders/**,/api/outbound-orders/**,/api/kanbans/**,/api/inventory/**,/api/mobile/scan/**
  - id: wms-agent-service
    uri: ${WMS_AGENT_SERVICE_URI:lb://wms-agent-service}
    predicates:
      - Path=/api/agent/**
```

这段配置有两个运行模式：

| 模式 | 实际行为 |
| --- | --- |
| 本地开发 | 没有注入 `WMS_*_SERVICE_URI` 时，默认走 `lb://服务名`，由 Eureka/Spring Cloud LoadBalancer 找服务 |
| Kubernetes | Helm 注入 `http://wms-system-service:8082` 这类 Service 地址，网关直接通过 Kubernetes DNS 访问服务 |

## 四、Kubernetes 中为什么可以通过 Service 名称通信

Helm 的网关模板：

```text
D:\projects\wms-springcloud\deploy\helm\wms\templates\gateway.yaml
```

其中注入了：

```yaml
env:
  - name: EUREKA_CLIENT_ENABLED
    value: "false"
  - name: WMS_SYSTEM_SERVICE_URI
    value: http://wms-system-service:8082
  - name: WMS_MASTERDATA_SERVICE_URI
    value: http://wms-masterdata-service:8083
  - name: WMS_BUSINESS_SERVICE_URI
    value: http://wms-business-service:8084
  - name: WMS_AGENT_SERVICE_URI
    value: http://wms-agent-service:8085
```

Kubernetes 会为每个 Service 建立集群 DNS 记录。例如 `wms-business-service` 在同一 namespace 内可以解析成该 Service 的 ClusterIP。请求链路是：

```text
wms-gateway Pod
-> DNS 查询 wms-business-service
-> 得到 Service ClusterIP
-> kube-proxy / CNI 将流量转发到后端 Pod Endpoint
-> wms-business-service Pod:8084
```

因此在 Kubernetes 中不应该把 Pod IP 写死。Pod IP 会随着重启、滚动升级变化，Service 名称才是稳定入口。

验证命令：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get svc,endpoints -n wms wms-gateway wms-system-service wms-masterdata-service wms-business-service wms-agent-service
kubectl exec -n wms deploy/wms-gateway -- sh -c "printenv | grep -E 'EUREKA_CLIENT_ENABLED|WMS_.*SERVICE_URI'"
```

预期能看到：

```text
EUREKA_CLIENT_ENABLED=false
WMS_SYSTEM_SERVICE_URI=http://wms-system-service:8082
WMS_MASTERDATA_SERVICE_URI=http://wms-masterdata-service:8083
WMS_BUSINESS_SERVICE_URI=http://wms-business-service:8084
WMS_AGENT_SERVICE_URI=http://wms-agent-service:8085
```

## 五、CORS 边界

CORS 只由 `wms-gateway` 统一处理。下游服务不要再单独添加 CORS 响应头，否则浏览器会报：

```text
Access-Control-Allow-Origin contains multiple values
```

网关中已经配置：

```yaml
default-filters:
  - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials,RETAIN_FIRST
globalcors:
  cors-configurations:
    '[/**]':
      allowedOriginPatterns:
        - http://localhost:*
        - http://127.0.0.1:*
        - http://192.168.*:*
        - https://192.168.*:*
```

## 六、数据库边界

当前阶段多个服务共享同一个新库：

```text
database: wms_cloud
local:    127.0.0.1:3317
k8s:      mysql:3306
```

共享库的原因是仓储核心业务存在强事务关系：

- 入库会同时更新入库单、看板、库存、流水。
- 出库会同时更新出库单、分配记录、看板、库存、流水。
- 移库/转包会改变看板位置、拆分看板、写库存流水。
- 业务服务需要读取零件、供应商、库位、器具等主数据。

后续如果拆物理库，建议按如下方向演进：

```text
wms_system      用户、角色、菜单、配置
wms_masterdata  供应商、客户、零件、器具、库位
wms_business    入库、出库、看板、库存、流水、迁移
wms_agent       预测、建议、RAG、对话记忆
```

拆库后，跨服务读取应改为 Feign/WebClient、事件同步读模型，或专门的查询模型，不能继续直接跨库查表。

## 七、启动顺序

本地建议顺序：

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

`start-services.ps1` 会先执行 Maven 安装，再依次启动服务，并将日志写入：

```text
D:\projects\wms-springcloud\.logs
```

## 八、验证链路

登录接口：

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

基础资料接口：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/suppliers?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/parts?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/locations?page=1&pageSize=10'
```

Agent 健康检查：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/health'
```

## 九、常见误区

| 现象 | 正确理解 |
| --- | --- |
| Kubernetes 中还保留 `wms-discovery` | 保留是为了兼容和本地一致性；业务流量在 K8s 下通过 Service DNS，不依赖 Eureka |
| 网关日志提示连不上 Eureka | 如果 `EUREKA_CLIENT_ENABLED=false` 已注入，业务路由仍应看 `WMS_*_SERVICE_URI` 和 Service Endpoints |
| Pod IP 能访问，Service 不能访问 | 应优先修 Service selector 和 Endpoints，而不是把 Pod IP 写到配置里 |
| Agent 挂了 | Agent 是旁路服务，主业务页面不应因为 Agent 不可用而整体不可用 |
