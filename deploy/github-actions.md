# GitHub Actions 镜像构建与自动部署说明

更新时间：2026-06-24

工作流文件：

```text
.github/workflows/build-and-push-images.yml
```

## 一、触发方式

当前保留两种触发方式：

- 推送到 `master` 分支时自动触发
- 在 GitHub Actions 页面手动执行 `workflow_dispatch`

其中自动版本号仍然使用：

```text
v${GITHUB_RUN_NUMBER}
```

这表示本工作流第 N 次运行对应版本 `vN`。

## 二、镜像仓库

当前统一推送到阿里云 ACR：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
```

因为使用单仓库，所以通过 tag 区分不同服务。

## 三、镜像 tag 规则

每个服务会生成两类 tag：

```text
服务名-v运行序号
服务名-master
```

例如第 18 次运行，会生成：

```text
wms-discovery-v18
wms-gateway-v18
wms-system-service-v18
wms-masterdata-service-v18
wms-business-service-v18
wms-frontend-v18
```

同时还会更新：

```text
wms-discovery-master
wms-gateway-master
wms-system-service-master
wms-masterdata-service-master
wms-business-service-master
wms-frontend-master
```

MySQL 额外镜像 tag：

```text
wms-mysql-8.0
```

## 四、Secrets 配置

GitHub 仓库至少需要以下 Secrets：

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

示例：

```text
ACR_REGISTRY=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com
ACR_IMAGE_REPOSITORY=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
```

## 五、工作流执行阶段

### 1. Prepare version

生成：

```text
v${GITHUB_RUN_NUMBER}
```

### 2. Build and push Docker images

分别构建并推送：

- `wms-discovery`
- `wms-gateway`
- `wms-system-service`
- `wms-masterdata-service`
- `wms-business-service`
- `wms-frontend`

### 3. Mirror MySQL image

将官方 `mysql:8.0` 拉取后重新打 tag 推送到 ACR：

```text
${IMAGE_REPOSITORY}:wms-mysql-8.0
```

这样云端集群只需要从同一个 ACR 拉取镜像。

### 4. Deploy

部署阶段会做这些事情：

```text
SSH 登录服务器
-> 上传 deploy/helm/wms
-> 检查 Helm release 是否处于 pending 状态
-> 如有需要自动回滚到最近一个可用 revision
-> helm upgrade --install
-> 等待 mysql StatefulSet Ready
-> 等待 wms-mysql-init Job 完成
-> 查看初始化 Job 日志
-> 等待所有业务 Deployment rollout 完成
```

## 六、数据库初始化说明

当前部署链路使用 Helm Job 初始化数据库：

- 模板：`deploy/helm/wms/templates/mysql-init-job.yaml`
- SQL：`deploy/helm/wms/files/wms-cloud-init.sql`

初始化 SQL 现在是干净初始化，只包含：

- 表结构
- 用户、角色、菜单、权限基础数据
- 系统配置
- 默认库存预警模板

不会再写入任何入库、出库、库存、供应商、零件等演示业务数据。

默认库存预警模板：

```json
{"critical":10,"low":30,"attention":60}
```

对应配置项：

```text
module_key = inventoryWarning
item_code = DEFAULT
```

## 七、服务器前置要求

服务器需要提前安装并配置：

- `kubectl`
- `helm`
- 可用的 Kubernetes 集群
- k3s 场景下可访问 `/etc/rancher/k3s/k3s.yaml`

还需要在集群中提前创建镜像拉取密钥：

```bash
kubectl create namespace wms

kubectl create secret docker-registry aliyun-acr \
  -n wms \
  --docker-server=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com \
  --docker-username='你的 ACR 用户名' \
  --docker-password='你的 ACR 密码或访问凭证'
```

## 八、当前工作流重点实现

当前工作流已经对以下问题做了处理：

- 避免并发部署互相覆盖：`concurrency.group = wms-master-build-deploy`
- Helm release 若卡在 `pending-*`，会先尝试回滚
- 部署后等待 MySQL、初始化 Job、各微服务全部 Ready
- MySQL 镜像与业务镜像统一从 ACR 拉取

## 九、常见问题

### 1. `another operation (install/upgrade/rollback) is in progress`

说明上一轮 Helm 操作未结束或卡住。当前工作流已经增加 pending 状态检查与自动回滚。

### 2. `Run Command Timeout`

说明远端部署脚本整体超时，常见原因：

- 镜像拉取太慢
- MySQL 首次初始化耗时较长
- 某个 Deployment 一直未 Ready

建议检查：

```bash
kubectl get pods -n wms
kubectl describe pod <pod-name> -n wms
kubectl logs job/wms-mysql-init -n wms
```

### 3. MySQL 初始化后没有业务数据

这是当前设计目标，不是故障。初始化库现在只保留系统骨架数据，业务数据需要后续在系统中真实录入。
