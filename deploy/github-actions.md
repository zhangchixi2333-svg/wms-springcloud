# GitHub Actions 镜像构建推送说明

更新时间：2026-06-08

工作流文件：

```text
.github/workflows/build-and-push-images.yml
```

## 1. 触发方式

推送到 `master` 分支时自动触发构建和推送。

工作流不再保留手动触发入口，这样版本号可以尽量对应“第 N 次推送 master 分支”。

## 2. 镜像仓库

当前使用一个阿里云 ACR 仓库保存所有服务镜像：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
```

因为是单仓库，所以不同服务通过 tag 区分。

## 3. Tag 规则

每次推送 `master` 后，所有服务使用同一个版本号：

```text
v${GITHUB_RUN_NUMBER}
```

`GITHUB_RUN_NUMBER` 是 GitHub Actions 对当前 workflow 的运行序号。由于当前 workflow 只在 `master` push 时触发，所以它可以作为 `master` 推送构建版本号使用。

每个服务会生成两个 tag：

```text
服务名-v运行序号
服务名-master
```

示例：第 12 次触发 workflow 时，会生成：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-discovery-v12
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-gateway-v12
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-system-service-v12
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-masterdata-service-v12
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-business-service-v12
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-frontend-v12
```

滚动最新版本 tag：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-discovery-master
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-gateway-master
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-system-service-master
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-masterdata-service-master
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-business-service-master
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-frontend-master
```

生产或测试部署建议使用 `服务名-v运行序号` 固定版本，方便回滚。`服务名-master` 适合临时测试环境跟随最新构建。

## 4. GitHub Secrets

仓库需要配置以下 Secrets：

```text
ACR_REGISTRY=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com
ACR_IMAGE_REPOSITORY=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
ACR_USERNAME=你的阿里云镜像仓库用户名
ACR_PASSWORD=你的阿里云镜像仓库密码或访问凭证
```

## 5. 后续云服务器自动部署

购买云服务器后，可以继续增加 deploy job：

```text
GitHub Actions -> SSH 登录云服务器 -> docker compose pull/up 或 helm upgrade
```

常见新增 Secrets：

```text
SERVER_HOST
SERVER_USER
SERVER_SSH_KEY
SERVER_PORT
```

当前 workflow 已经包含自动部署 job。部署 job 会在镜像全部推送成功后执行：

```text
SSH 登录云服务器
-> 清理 /opt/wms-springcloud/deploy/helm/wms
-> 上传 deploy/helm/wms
-> helm upgrade --install wms
-> 等待 mysql StatefulSet 和 mysql-0 Ready
-> 等待 6 个 Deployment rollout 完成
```

服务器需要提前安装并配置好：

```text
kubectl
helm
可用的 Kubernetes 集群，例如 k3s
```

如果使用 k3s，workflow 默认读取：

```text
/etc/rancher/k3s/k3s.yaml
```

首次部署前，需要在服务器 Kubernetes 集群里创建命名空间和阿里云 ACR 拉取密钥：

```bash
kubectl create namespace wms

kubectl create secret docker-registry aliyun-acr \
  -n wms \
  --docker-server=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com \
  --docker-username='你的阿里云 ACR 用户名' \
  --docker-password='你的阿里云 ACR 密码或访问凭证'
```

部署时 Helm 会使用当前 workflow 版本号，例如第 12 次 master 推送会部署：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-gateway-v12
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-frontend-v12
```
