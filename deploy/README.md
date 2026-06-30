# WMS 部署目录说明

更新时间：2026-06-30

本文是 `D:\projects\wms-springcloud\deploy` 的部署入口，说明 Docker 镜像、原生 Kubernetes YAML、Helm chart、GitHub Actions 自动部署之间的关系。

## 一、目录结构

```text
deploy
├─ docker
│  ├─ Dockerfile.service
│  ├─ Dockerfile.frontend
│  ├─ nginx.conf
│  └─ build-images.ps1
├─ helm
│  └─ wms
│     ├─ Chart.yaml
│     ├─ values.yaml
│     ├─ files
│     │  └─ wms-cloud-init.sql
│     └─ templates
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
   ├─ frontend
   └─ services
```

## 二、部署链路

```text
浏览器
-> wms-frontend Service / NodePort / Ingress
-> nginx
-> /api 转发到 wms-gateway Service
-> Spring Cloud Gateway
-> Kubernetes Service DNS
-> wms-system-service / wms-masterdata-service / wms-business-service / wms-agent-service
-> mysql Service
```

集群内服务：

| Service | 端口 | 说明 |
| --- | --- | --- |
| `wms-discovery` | `8761` | Eureka，Kubernetes 下保留但业务流量不依赖它 |
| `wms-gateway` | `8080` | API 网关，NodePort 默认 `30080` |
| `wms-system-service` | `8082` | 系统服务 |
| `wms-masterdata-service` | `8083` | 基础资料服务 |
| `wms-business-service` | `8084` | 仓储业务服务 |
| `wms-agent-service` | `8085` | Agent 服务 |
| `wms-frontend` | `80` | 前端 nginx，NodePort 默认 `30081` |
| `mysql` | `3306` | MySQL StatefulSet |

## 三、Docker 镜像

后端服务使用同一个 Dockerfile：

```text
deploy/docker/Dockerfile.service
```

通过 `MODULE` 构建参数决定打包哪个 Maven 模块：

```powershell
cd D:\projects\wms-springcloud
docker build -f deploy\docker\Dockerfile.service --build-arg MODULE=wms-business-service -t wms/wms-business-service:0.0.1 .
```

前端镜像：

```powershell
docker build -f deploy\docker\Dockerfile.frontend --build-arg VITE_API_BASE=/api -t wms/wms-frontend:0.0.1 .
```

一键构建本地镜像：

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

指定仓库和版本：

```powershell
$env:WMS_IMAGE_PREFIX = 'crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud'
$env:WMS_IMAGE_TAG = 'v34'
.\deploy\docker\build-images.ps1
```

## 四、前端 nginx 转发

配置文件：

```text
deploy/docker/nginx.conf
```

关键配置：

```nginx
location /api/ {
  proxy_pass http://wms-gateway:8080/api/;
}
```

因此在 Kubernetes 中，前端容器访问的是 Service 名称 `wms-gateway`，不是 Pod IP。

## 五、原生 Kubernetes YAML

适合手工验证或不用 Helm 的场景：

```powershell
cd D:\projects\wms-springcloud
kubectl apply -k deploy/kubernetes
kubectl get pods -n wms
kubectl get svc -n wms
```

等待：

```powershell
kubectl wait --for=condition=Ready pod --all -n wms --timeout=300s
```

访问：

```text
前端：http://<NodeIP>:30081
网关：http://<NodeIP>:30080/api
```

清理：

```powershell
kubectl delete -k deploy/kubernetes
```

如果确认要删除数据库数据：

```powershell
kubectl delete pvc -n wms -l app=mysql
```

## 六、Helm 部署

核心 values：

```text
deploy/helm/wms/values.yaml
```

关键字段：

| 字段 | 说明 |
| --- | --- |
| `image.repository` | 单仓库多 tag 的业务镜像仓库 |
| `image.tag` | 业务镜像版本，如 `v34` |
| `image.pullPolicy` | 镜像拉取策略 |
| `image.pullSecrets` | 私有仓库拉取 Secret |
| `mysql.image` | MySQL 8.0 镜像 |
| `mysql.database` | 数据库名，默认 `wms_cloud` |
| `service.gatewayNodePort` | 网关 NodePort，默认 `30080` |
| `service.frontendNodePort` | 前端 NodePort，默认 `30081` |
| `ingress.enabled` | 是否启用 Ingress |
| `services.agent.port` | Agent 服务端口，默认 `8085` |

本地或服务器手工部署：

```powershell
cd D:\projects\wms-springcloud
helm upgrade --install wms deploy/helm/wms -n wms --create-namespace
```

指定 ACR 镜像：

```powershell
helm upgrade --install wms deploy/helm/wms -n wms --create-namespace `
  --set image.repository=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud `
  --set image.tag=v34 `
  --set mysql.image=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-mysql-8.0 `
  --set image.pullSecrets[0].name=aliyun-acr
```

k3s 服务器上使用 Bash 时：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
helm upgrade --install wms /opt/wms-springcloud/deploy/helm/wms \
  -n wms \
  --create-namespace \
  --set image.repository=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud \
  --set image.tag=v34 \
  --set mysql.image=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-mysql-8.0 \
  --set image.pullSecrets[0].name=aliyun-acr
```

完整 k3s/Helm 手册见：

```text
docs/deployment/kubernetes-helm-runbook.md
```

## 七、数据库初始化

三份 SQL 必须保持一致：

```text
sql/wms-cloud-init.sql
deploy/helm/wms/files/wms-cloud-init.sql
deploy/kubernetes/mysql/wms-cloud-init.sql
```

Helm 使用：

```text
deploy/helm/wms/templates/mysql-init-configmap.yaml
deploy/helm/wms/templates/mysql-init-job.yaml
```

当前初始化 SQL 会清空生产业务数据并写入基础资料。它适合本阶段重置测试环境；如果云端已经有真实业务数据，升级前必须改造成非破坏式迁移脚本。

## 八、GitHub Actions 自动部署

工作流：

```text
.github/workflows/build-and-push-images.yml
```

触发：

```text
push 到 master
手动 workflow_dispatch
```

版本规则：

```text
v${GITHUB_RUN_NUMBER}
```

镜像 tag 示例：

```text
wms-business-service-v34
wms-business-service-master
wms-frontend-v34
wms-frontend-master
wms-mysql-8.0
```

详细说明见：

```text
deploy/github-actions.md
```

## 九、部署验证

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
helm status wms -n wms
kubectl get pods -n wms -o wide
kubectl get svc,endpoints -n wms
kubectl logs job/wms-mysql-init -n wms --tail=120
```

登录接口：

```powershell
$body = @{
  username = 'admin'
  password = 'admin123'
} | ConvertTo-Json

Invoke-RestMethod 'http://<NodeIP>:30080/api/auth/login' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

## 十、相关文档

| 文档 | 内容 |
| --- | --- |
| `docs/deployment/local-development.md` | 本地开发启动 |
| `docs/deployment/kubernetes-helm-runbook.md` | k3s + Helm 部署手册 |
| `docs/deployment/troubleshooting.md` | 部署排障 |
| `deploy/github-actions.md` | GitHub Actions 自动部署 |
