# GitHub Actions 镜像构建与自动部署说明

更新时间：2026-06-30

本文说明 `.github/workflows/build-and-push-images.yml` 如何在推送 `master` 分支时构建镜像、推送到阿里云 ACR，并通过 SSH 部署到 k3s 服务器。

## 一、工作流文件

```text
D:\projects\wms-springcloud\.github\workflows\build-and-push-images.yml
```

触发方式：

```text
push 到 master 分支
GitHub Actions 页面手动 workflow_dispatch
```

并发控制：

```yaml
concurrency:
  group: wms-master-build-deploy
  cancel-in-progress: false
```

这表示同一时间只允许一个 master 部署流程运行，避免两个部署互相覆盖。

## 二、版本号规则

工作流使用：

```text
v${GITHUB_RUN_NUMBER}
```

例如工作流第 34 次运行，版本就是：

```text
v34
```

每个服务会同时推两个 tag：

```text
服务名-v34
服务名-master
```

示例：

```text
wms-discovery-v34
wms-gateway-v34
wms-system-service-v34
wms-masterdata-service-v34
wms-business-service-v34
wms-agent-service-v34
wms-frontend-v34
```

## 三、镜像仓库

当前仓库：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
```

这是单仓库多 tag 规则。服务名写在 tag 中，而不是每个服务一个仓库。

MySQL mirror 固定 tag：

```text
wms-mysql-8.0
```

完整 MySQL 镜像名：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-mysql-8.0
```

## 四、需要配置的 GitHub Secrets

在 GitHub 仓库：

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

必填：

| Secret | 示例值 | 说明 |
| --- | --- | --- |
| `ACR_REGISTRY` | `crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com` | ACR registry 域名 |
| `ACR_IMAGE_REPOSITORY` | `crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud` | 完整镜像仓库 |
| `ACR_USERNAME` | `<你的 ACR 用户名>` | ACR 用户名 |
| `ACR_PASSWORD` | `<你的 ACR 密码或访问凭证>` | ACR 密码 |
| `SERVER_HOST` | `<服务器公网 IP>` | 云服务器 IP |
| `SERVER_USER` | `root` | SSH 用户 |
| `SERVER_SSH_KEY` | `-----BEGIN OPENSSH PRIVATE KEY-----...` | SSH 私钥完整内容 |
| `SERVER_PORT` | `22` | SSH 端口 |

可选 Ingress：

| Secret | 示例值 | 说明 |
| --- | --- | --- |
| `WMS_INGRESS_ENABLED` | `true` 或 `false` | 是否启用 Ingress |
| `WMS_INGRESS_HOST` | `wms.example.com` | 域名 |
| `WMS_INGRESS_TLS_ENABLED` | `true` 或 `false` | 是否启用 TLS |
| `WMS_INGRESS_TLS_SECRET` | `wms-frontend-tls` | TLS Secret 名称 |

`SERVER_SSH_KEY` 必须是私钥原文，包含头尾：

```text
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

如果填成公钥或只填一行，会出现：

```text
ssh.ParsePrivateKey: ssh: no key found
```

## 五、工作流阶段

### 1. Prepare version

生成版本：

```bash
VERSION="v${GITHUB_RUN_NUMBER}"
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

后端使用：

```text
deploy/docker/Dockerfile.service
```

通过 `MODULE=服务模块名` 选择 Maven 模块。

前端使用：

```text
deploy/docker/Dockerfile.frontend
```

并注入：

```text
VITE_API_BASE=/api
```

### 3. Mirror MySQL

工作流会拉取：

```text
mysql:8.0
```

然后重新打 tag 并推送到 ACR：

```text
${ACR_IMAGE_REPOSITORY}:wms-mysql-8.0
```

这样云服务器只需要从 ACR 拉镜像，减少直接访问 Docker Hub 的失败概率。

### 4. Upload Helm chart

上传：

```text
deploy/helm/wms
```

到服务器：

```text
/opt/wms-springcloud/deploy/helm/wms
```

### 5. Deploy by Helm

远端脚本会执行：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
helm lint /opt/wms-springcloud/deploy/helm/wms
helm upgrade --install wms /opt/wms-springcloud/deploy/helm/wms \
  -n wms \
  --create-namespace \
  --set "image.repository=${IMAGE_REPOSITORY}" \
  --set "image.tag=${VERSION}" \
  --set "mysql.image=${IMAGE_REPOSITORY}:${MYSQL_ACR_TAG}" \
  --set "image.pullSecrets[0].name=aliyun-acr"
```

然后等待：

```text
mysql StatefulSet
wms-mysql-init Job
wms-discovery
wms-gateway
wms-system-service
wms-masterdata-service
wms-business-service
wms-agent-service
wms-frontend
```

## 六、服务器前置要求

服务器需要：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get nodes
helm version
```

如果服务器没有 Helm：

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

服务器部署目录由 workflow 自动创建：

```text
/opt/wms-springcloud/deploy/helm
```

## 七、失败时工作流会打印什么

部署脚本设置了错误处理，会打印：

```text
Helm list
Helm status
Pods
Jobs
Events
MySQL describe
MySQL logs
MySQL init logs
各服务日志
```

这些日志用于定位：

- 镜像拉取失败。
- MySQL 初始化失败。
- 服务 CrashLoopBackOff。
- Service 没有 Endpoints。
- Helm release pending。

## 八、常见错误

### 1. `Run Command Timeout`

说明远端脚本超过 `command_timeout`。常见原因：

- 镜像拉取很慢。
- MySQL 首次启动或初始化很慢。
- 某个服务 CrashLoopBackOff，rollout 等不到 Ready。
- Helm 升级卡在资源未就绪。

服务器上排查：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get pods -n wms -o wide
kubectl get events -n wms --sort-by=.lastTimestamp | tail -n 80
kubectl logs job/wms-mysql-init -n wms --tail=200
kubectl logs deploy/wms-gateway -n wms --tail=200
```

### 2. `another operation is in progress`

说明 Helm 上一次操作卡住：

```bash
helm status wms -n wms
helm history wms -n wms
helm rollback wms <REVISION> -n wms --wait --timeout 10m
```

当前 workflow 已内置 pending 检查和自动回滚逻辑。

### 3. `ImagePullBackOff`

排查：

```bash
kubectl describe pod <pod-name> -n wms
kubectl get secret aliyun-acr -n wms
kubectl get statefulset mysql -n wms -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
```

如果 MySQL mirror 被删，重新运行 workflow 会重新推送：

```text
${ACR_IMAGE_REPOSITORY}:wms-mysql-8.0
```

### 4. Docker Hub 连接重置

构建时报：

```text
failed to fetch oauth token
read: connection reset by peer
```

说明 GitHub runner 访问 Docker Hub 不稳定。当前 Dockerfile 支持通过构建参数替换基础镜像：

```text
MAVEN_IMAGE
JRE_IMAGE
NODE_IMAGE
NGINX_IMAGE
```

必要时可以把基础镜像也 mirror 到 ACR，再在 workflow 中传入这些 build args。

## 九、部署后验证

服务器：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get pods -n wms -o wide
kubectl get svc,endpoints -n wms
kubectl logs job/wms-mysql-init -n wms --tail=120
```

浏览器：

```text
http://<服务器IP>:30081
```

网关：

```text
http://<服务器IP>:30080/api
```

登录账号：

```text
admin / admin123
```

## 十、重要提醒

当前 Helm 初始化 SQL 包含清空业务表逻辑，适合重置测试环境。如果云端已经产生真实业务数据，不能直接用当前 SQL 做生产升级，需要先拆分为：

```text
schema migration：只建表、补字段、补索引
seed migration：只补系统菜单、角色、配置等基础数据
business reset：只在明确重置测试库时执行
```
