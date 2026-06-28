# WMS Kubernetes 与 Helm 部署说明

更新时间：2026-06-29

本文说明如何将 `D:\projects\wms-springcloud` 部署到 Kubernetes/k3s，并统一说明数据库初始化、镜像构建、Service 暴露、Ingress、Agent 服务和排障方式。

## 一、部署目录结构

```text
deploy
├─ docker
│  ├─ Dockerfile.frontend
│  ├─ Dockerfile.service
│  ├─ nginx.conf
│  └─ build-images.ps1
├─ helm
│  └─ wms
│     ├─ Chart.yaml
│     ├─ values.yaml
│     ├─ files
│     │  └─ wms-cloud-init.sql
│     └─ templates
│        ├─ _helpers.tpl
│        ├─ secret.yaml
│        ├─ mysql.yaml
│        ├─ mysql-init-configmap.yaml
│        ├─ mysql-init-job.yaml
│        ├─ discovery.yaml
│        ├─ gateway.yaml
│        ├─ system-service.yaml
│        ├─ masterdata-service.yaml
│        ├─ business-service.yaml
│        ├─ agent-service.yaml
│        ├─ frontend.yaml
│        └─ ingress.yaml
└─ kubernetes
   ├─ kustomization.yaml
   ├─ base
   ├─ mysql
   │  ├─ mysql.yaml
   │  └─ wms-cloud-init.sql
   ├─ frontend
   └─ services
      ├─ discovery.yaml
      ├─ gateway.yaml
      ├─ system-service.yaml
      ├─ masterdata-service.yaml
      ├─ business-service.yaml
      └─ agent-service.yaml
```

## 二、当前部署链路

```text
浏览器
  -> wms-frontend Service / Ingress
  -> 前端 nginx
  -> /api 转发到 wms-gateway Service
  -> Spring Cloud Gateway
  -> Kubernetes Service DNS
  -> system/masterdata/business/agent 微服务
  -> mysql Service
  -> mysql StatefulSet
```

集群内部 Service：

| Service | 端口 | 说明 |
| --- | --- | --- |
| `wms-discovery` | `8761` | Eureka，K8s 下保留但业务流量不依赖它 |
| `wms-gateway` | `8080` | 后端 API 网关，NodePort 默认 `30080` |
| `wms-system-service` | `8082` | 系统服务 |
| `wms-masterdata-service` | `8083` | 基础资料服务 |
| `wms-business-service` | `8084` | 仓储业务服务 |
| `wms-agent-service` | `8085` | 智能分析服务，挂了不影响主业务 |
| `wms-frontend` | `80` | 前端 nginx，NodePort 默认 `30081` |
| `mysql` | `3306` | MySQL StatefulSet |

## 三、部署前准备

本地或服务器需要具备：

```powershell
docker version
kubectl version --client
helm version
```

k3s 服务器上通常需要先设置：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get nodes
```

如果从私有 ACR 拉取镜像，需要创建镜像拉取密钥：

```bash
kubectl create namespace wms --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret docker-registry aliyun-acr \
  -n wms \
  --docker-server=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com \
  --docker-username='你的 ACR 用户名' \
  --docker-password='你的 ACR 密码或访问凭证' \
  --dry-run=client -o yaml | kubectl apply -f -
```

## 四、本地构建镜像

在项目根目录执行：

```powershell
cd D:\projects\wms-springcloud
.\deploy\docker\build-images.ps1
```

默认镜像：

```text
wms/wms-discovery:0.0.1
wms/wms-gateway:0.0.1
wms/wms-system-service:0.0.1
wms/wms-masterdata-service:0.0.1
wms/wms-business-service:0.0.1
wms/wms-agent-service:0.0.1
wms/wms-frontend:0.0.1
```

如需远端仓库前缀：

```powershell
$env:WMS_IMAGE_PREFIX = 'registry.example.com/wms'
$env:WMS_IMAGE_TAG = '0.0.1'
.\deploy\docker\build-images.ps1
```

## 五、使用原生 Kubernetes YAML 部署

```powershell
cd D:\projects\wms-springcloud
kubectl apply -k deploy/kubernetes
kubectl get pods -n wms
kubectl get svc -n wms
kubectl wait --for=condition=Ready pod --all -n wms --timeout=300s
```

访问地址：

```text
前端：http://<NodeIP>:30081
网关：http://<NodeIP>:30080/api
```

清理：

```powershell
kubectl delete -k deploy/kubernetes
kubectl delete pvc -n wms -l app=mysql
```

## 六、使用 Helm 部署

安装或升级：

```powershell
cd D:\projects\wms-springcloud
helm upgrade --install wms deploy/helm/wms -n wms --create-namespace
```

指定业务镜像和 MySQL 镜像：

```powershell
helm upgrade --install wms deploy/helm/wms -n wms --create-namespace `
  --set image.repository=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud `
  --set image.tag=v1 `
  --set mysql.image=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-mysql-8.0 `
  --set image.pullSecrets[0].name=aliyun-acr
```

开启 Ingress 示例：

```powershell
helm upgrade --install wms deploy/helm/wms -n wms --create-namespace `
  --set ingress.enabled=true `
  --set ingress.host=wms.example.com
```

查看状态：

```powershell
helm status wms -n wms
kubectl get pods -n wms -o wide
kubectl get svc -n wms -o wide
kubectl get job -n wms
```

卸载：

```powershell
helm uninstall wms -n wms
kubectl delete pvc -n wms -l app=mysql
```

## 七、数据库初始化机制

三份 SQL 必须保持一致：

```text
sql/wms-cloud-init.sql
deploy/helm/wms/files/wms-cloud-init.sql
deploy/kubernetes/mysql/wms-cloud-init.sql
```

Helm 部署有两条初始化链路：

1. MySQL 数据目录为空时，容器会执行挂载到 `/docker-entrypoint-initdb.d` 的 SQL。
2. `wms-mysql-init` Job 会再次幂等执行同一份 SQL，保证升级时也能补齐新增表、字段、索引和系统配置。

初始化 Job：

```text
deploy/helm/wms/templates/mysql-init-job.yaml
```

初始化内容：

- 数据库和表结构。
- 用户、角色、菜单、角色菜单。
- 系统配置和默认库存预警模板。
- Agent 表结构和 Agent 菜单。
- 查询性能索引。

不会初始化任何业务演示数据，包括供应商、客户、零件、仓库/库区、器具、入库单、出库单、看板、库存、库存流水。

默认库存预警：

```json
{"critical":10,"low":30,"attention":60}
```

## 八、镜像与 Helm 值

关键默认值文件：

```text
deploy/helm/wms/values.yaml
```

关键字段：

| 字段 | 说明 |
| --- | --- |
| `image.repository` | 业务镜像仓库 |
| `image.tag` | 业务镜像版本，如 `v34` |
| `image.pullSecrets` | 私有仓库拉取 Secret |
| `mysql.image` | MySQL 镜像，默认走 ACR mirror |
| `mysql.database` | 数据库名，默认 `wms_cloud` |
| `service.gatewayNodePort` | 网关 NodePort，默认 `30080` |
| `service.frontendNodePort` | 前端 NodePort，默认 `30081` |
| `ingress.enabled` | 是否启用 Ingress |
| `services.agent.port` | Agent Service 端口，默认 `8085` |

## 九、访问与验证

登录接口：

```powershell
$body = @{
  username = 'admin'
  password = 'admin123'
} | ConvertTo-Json

Invoke-RestMethod 'http://localhost:30080/api/auth/login' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

前端：

```text
http://localhost:30081
```

Agent 健康检查：

```powershell
Invoke-RestMethod 'http://localhost:30080/api/agent/health'
```

验证 Service 和 Endpoint：

```powershell
kubectl get svc,endpoints -n wms wms-gateway wms-system-service wms-masterdata-service wms-business-service wms-agent-service mysql
```

查看初始化结果：

```powershell
kubectl logs job/wms-mysql-init -n wms --tail=120
kubectl exec -n wms mysql-0 -- mysql -uroot -proot123456 -D wms_cloud -e "SELECT COUNT(*) AS user_count FROM app_user; SELECT COUNT(*) AS menu_count FROM menu_item; SELECT COUNT(*) AS agent_tables FROM information_schema.tables WHERE table_schema='wms_cloud' AND table_name LIKE 'agent\_%';"
```

## 十、常见问题

### 1. Pod 卡在 `ImagePullBackOff`

```powershell
kubectl describe pod <pod-name> -n wms
```

重点看镜像地址、Secret、节点是否能访问镜像仓库。

### 2. MySQL 正常但系统服务起不来

```powershell
kubectl get pod mysql-0 -n wms
kubectl logs mysql-0 -n wms
kubectl logs job/wms-mysql-init -n wms --tail=120
kubectl logs deploy/wms-system-service -n wms --previous --tail=200
kubectl logs deploy/wms-masterdata-service -n wms --previous --tail=200
kubectl logs deploy/wms-business-service -n wms --previous --tail=200
```

### 3. 网关返回 503

Kubernetes 模式下优先确认网关是否拿到 Service URI：

```powershell
kubectl exec -n wms deploy/wms-gateway -- sh -c "printenv | grep -E 'EUREKA_CLIENT_ENABLED|WMS_.*SERVICE_URI'"
kubectl get svc,endpoints -n wms wms-system-service wms-masterdata-service wms-business-service wms-agent-service
```

预期包含：

```text
EUREKA_CLIENT_ENABLED=false
WMS_SYSTEM_SERVICE_URI=http://wms-system-service:8082
WMS_MASTERDATA_SERVICE_URI=http://wms-masterdata-service:8083
WMS_BUSINESS_SERVICE_URI=http://wms-business-service:8084
WMS_AGENT_SERVICE_URI=http://wms-agent-service:8085
```

### 4. Helm 卡在 pending 状态

```bash
helm status wms -n wms
helm history wms -n wms
helm rollback wms <最近一个 deployed 或 superseded revision> -n wms --wait --timeout 10m
```

当前 GitHub 工作流会自动检测 `pending-install`、`pending-upgrade`、`pending-rollback` 并尝试回滚。

### 5. CORS 重复

当前设计为只在网关处理 CORS。如果再次出现：

```text
Access-Control-Allow-Origin contains multiple values
```

说明某个下游服务又单独加了 CORS，需要删除下游重复配置。