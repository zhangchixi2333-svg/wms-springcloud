# GitHub Actions 镜像构建与自动部署说明

更新时间：2026-06-29

工作流文件：

```text
.github/workflows/build-and-push-images.yml
```

## 一、触发方式

当前保留两种触发方式：

- 推送到 `master` 分支自动触发。
- 在 GitHub Actions 页面手动执行 `workflow_dispatch`。

自动版本号使用：

```text
v${GITHUB_RUN_NUMBER}
```

也就是本仓库该工作流第 N 次运行时，镜像版本为 `vN`。

## 二、镜像仓库和 tag 规则

统一推送到阿里云 ACR：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
```

因为是单仓库多服务，所以通过 tag 区分服务：

```text
服务名-v运行序号
服务名-master
```

例如第 34 次运行：

```text
wms-discovery-v34
wms-gateway-v34
wms-system-service-v34
wms-masterdata-service-v34
wms-business-service-v34
wms-agent-service-v34
wms-frontend-v34
```

同时更新：

```text
wms-discovery-master
wms-gateway-master
wms-system-service-master
wms-masterdata-service-master
wms-business-service-master
wms-agent-service-master
wms-frontend-master
```

MySQL mirror 固定 tag：

```text
wms-mysql-8.0
```

## 三、Secrets 配置

GitHub 仓库需要配置：

```text
ACR_REGISTRY
ACR_IMAGE_REPOSITORY
ACR_USERNAME
ACR_PASSWORD
SERVER_HOST
SERVER_USER
SERVER_SSH_KEY
SERVER_PORT
```

可选 Ingress 相关：

```text
WMS_INGRESS_ENABLED
WMS_INGRESS_HOST
WMS_INGRESS_TLS_ENABLED
WMS_INGRESS_TLS_SECRET
```

示例：

```text
ACR_REGISTRY=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com
ACR_IMAGE_REPOSITORY=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
WMS_INGRESS_ENABLED=true
WMS_INGRESS_HOST=wms.example.com
```

`SERVER_SSH_KEY` 必须是完整私钥文本，包括：

```text
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

## 四、工作流阶段

### 1. Prepare version

生成：

```text
v${GITHUB_RUN_NUMBER}
```

### 2. Build and push Docker images

构建并推送：

- `wms-discovery`
- `wms-gateway`
- `wms-system-service`
- `wms-masterdata-service`
- `wms-business-service`
- `wms-agent-service`
- `wms-frontend`

后端服务使用同一个 Dockerfile：

```text
deploy/docker/Dockerfile.service
```

通过 `MODULE=服务名` 决定打包哪个 Maven 模块。

### 3. Mirror MySQL image

工作流会把官方 `mysql:8.0` 重新打 tag 推送到 ACR：

```text
${IMAGE_REPOSITORY}:wms-mysql-8.0
```

这样云端 k3s 只需要从同一个 ACR 拉取镜像，减少 Docker Hub 网络不稳定带来的失败。

### 4. Deploy

部署阶段会：

```text
SSH 登录服务器
-> 上传 deploy/helm/wms
-> 设置 KUBECONFIG=/etc/rancher/k3s/k3s.yaml
-> 创建/更新 aliyun-acr 镜像拉取 Secret
-> 检查 Helm release 是否 pending
-> 如有可用 revision 自动回滚
-> helm lint
-> helm upgrade --install
-> 等待 mysql StatefulSet Ready
-> 等待 wms-mysql-init Job 完成
-> 打印初始化 Job 日志
-> 等待 discovery/gateway/system/masterdata/business/agent/frontend 全部 rollout
-> 打印 Pod 状态
```

## 五、数据库初始化说明

部署使用 Helm Job 初始化数据库：

```text
deploy/helm/wms/templates/mysql-init-job.yaml
deploy/helm/wms/files/wms-cloud-init.sql
```

SQL 是幂等脚本，会补齐库表、字段、索引和系统基础数据。它不会插入业务演示数据。

初始化内容：

- 表结构。
- 默认账号、角色、菜单、角色菜单。
- 系统配置和默认库存预警。
- Agent 表结构和 Agent 菜单。

默认库存预警：

```json
{"critical":10,"low":30,"attention":60}
```

## 六、服务器前置要求

服务器需要：

- `kubectl`
- `helm`
- k3s 或其它可用 Kubernetes 集群
- 当前用户能读取 `/etc/rancher/k3s/k3s.yaml`

手工检查：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get nodes
helm version
```

## 七、当前工作流重点处理的问题

- 使用 `concurrency.group = wms-master-build-deploy` 避免多个部署互相覆盖。
- Helm release 卡在 `pending-*` 时自动尝试回滚。
- MySQL 镜像 mirror 到 ACR，避免云端直接拉 Docker Hub 超时。
- 部署失败时打印 Helm、Pod、Event、MySQL、初始化 Job、各服务日志。
- Agent 被纳入镜像矩阵、Helm 部署和 rollout 检查。
- 可通过 Secrets 控制 Ingress 开关和域名。

## 八、常见问题

### 1. `another operation (install/upgrade/rollback) is in progress`

说明上一轮 Helm 操作未结束或卡住。当前工作流会先检查 pending 状态并回滚。如果手动处理：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
helm history wms -n wms
helm rollback wms <REVISION> -n wms --wait --timeout 10m
```

### 2. `Run Command Timeout`

说明远端部署脚本整体超时，常见原因：

- 某个镜像拉取太慢。
- MySQL 首次启动或初始化慢。
- 某个 Deployment CrashLoopBackOff。
- Helm upgrade 等待资源但资源一直未 Ready。

排查：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get pods -n wms -o wide
kubectl get events -n wms --sort-by=.lastTimestamp | tail -n 80
kubectl describe pod <pod-name> -n wms
kubectl logs job/wms-mysql-init -n wms --tail=200
kubectl logs deploy/wms-gateway -n wms --tail=200
kubectl logs deploy/wms-agent-service -n wms --tail=200
```

### 3. MySQL 初始化后没有业务数据

这是设计目标，不是故障。初始化库只保留系统骨架数据，供应商、零件、入库、出库、库存、看板都需要在系统中真实录入。

### 4. Agent 接口失败但其它业务正常

Agent 是旁路服务。先查：

```bash
kubectl get svc,endpoints -n wms wms-agent-service
kubectl logs deploy/wms-agent-service -n wms --tail=200
kubectl exec -n wms deploy/wms-gateway -- sh -c "printenv | grep WMS_AGENT_SERVICE_URI"
```

如果只是不需要 Agent，可以临时关闭：

```bash
kubectl scale deployment/wms-agent-service -n wms --replicas=0
```