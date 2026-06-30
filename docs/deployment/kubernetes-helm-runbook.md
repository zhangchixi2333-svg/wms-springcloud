# k3s + Helm 部署手册

更新时间：2026-06-30

本文说明如何在 k3s 服务器上使用 Helm 部署 WMS。当前云端命名空间为 `wms`，Helm release 为 `wms`。

## 一、真实路径和资源名

| 项 | 值 |
| --- | --- |
| 本地项目路径 | `D:\projects\wms-springcloud` |
| 服务器部署路径 | `/opt/wms-springcloud` |
| Helm chart | `/opt/wms-springcloud/deploy/helm/wms` |
| namespace | `wms` |
| release | `wms` |
| 镜像仓库 | `crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud` |
| MySQL 镜像 | `crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-mysql-8.0` |
| 前端 NodePort | `30081` |
| 网关 NodePort | `30080` |

## 二、服务器预检查

在服务器 Bash 中执行：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get nodes
helm version
kubectl get ns
```

预期：

```text
节点 Ready
helm 能输出版本
kubectl 能访问集群
```

如果 `kubectl get nodes` 报证书错误或连接 `127.0.0.1:6443` 失败，先确认当前用户能读取：

```bash
ls -l /etc/rancher/k3s/k3s.yaml
```

## 三、创建镜像拉取 Secret

把 `<ACR用户名>` 和 `<ACR密码>` 换成真实值：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl create namespace wms --dry-run=client -o yaml | kubectl apply -f -
kubectl delete secret aliyun-acr -n wms --ignore-not-found
kubectl create secret docker-registry aliyun-acr \
  -n wms \
  --docker-server=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com \
  --docker-username='<ACR用户名>' \
  --docker-password='<ACR密码>'
```

验证：

```bash
kubectl get secret aliyun-acr -n wms
```

## 四、部署 Helm

如果是 GitHub Actions，workflow 会自动上传 chart 到：

```text
/opt/wms-springcloud/deploy/helm/wms
```

手工部署示例：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
export IMAGE_REPOSITORY='crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud'
export VERSION='v34'

helm lint /opt/wms-springcloud/deploy/helm/wms

helm upgrade --install wms /opt/wms-springcloud/deploy/helm/wms \
  -n wms \
  --create-namespace \
  --set "image.repository=${IMAGE_REPOSITORY}" \
  --set "image.tag=${VERSION}" \
  --set "mysql.image=${IMAGE_REPOSITORY}:wms-mysql-8.0" \
  --set "image.pullSecrets[0].name=aliyun-acr"
```

说明：

| 参数 | 作用 |
| --- | --- |
| `image.repository` | 单仓库多 tag 的业务镜像仓库 |
| `image.tag` | 工作流版本号，如 `v34` |
| `mysql.image` | ACR 中的 MySQL 8.0 mirror 镜像 |
| `image.pullSecrets[0].name` | 私有镜像拉取 Secret |

## 五、等待资源 Ready

```bash
kubectl get pods -n wms -o wide
kubectl rollout status statefulset/mysql -n wms --timeout=600s
kubectl wait --for=condition=Ready pod/mysql-0 -n wms --timeout=600s
kubectl wait --for=condition=complete job/wms-mysql-init -n wms --timeout=600s
kubectl logs job/wms-mysql-init -n wms --tail=120
```

等待服务：

```bash
kubectl rollout status deployment/wms-discovery -n wms --timeout=600s
kubectl rollout status deployment/wms-gateway -n wms --timeout=600s
kubectl rollout status deployment/wms-system-service -n wms --timeout=600s
kubectl rollout status deployment/wms-masterdata-service -n wms --timeout=600s
kubectl rollout status deployment/wms-business-service -n wms --timeout=600s
kubectl rollout status deployment/wms-agent-service -n wms --timeout=600s
kubectl rollout status deployment/wms-frontend -n wms --timeout=600s
```

最终检查：

```bash
kubectl get pods -n wms -o wide
kubectl get svc,endpoints -n wms
```

预期 Pod：

```text
mysql-0                         1/1 Running
wms-discovery-...               1/1 Running
wms-gateway-...                 1/1 Running
wms-system-service-...          1/1 Running
wms-masterdata-service-...      1/1 Running
wms-business-service-...        1/1 Running
wms-agent-service-...           1/1 Running
wms-frontend-...                1/1 Running
wms-mysql-init-...              Completed
```

## 六、访问

NodePort：

```text
前端：http://<服务器IP>:30081
网关：http://<服务器IP>:30080/api
```

如果使用 Ingress，需要在 Helm 中开启：

```bash
helm upgrade --install wms /opt/wms-springcloud/deploy/helm/wms \
  -n wms \
  --create-namespace \
  --set ingress.enabled=true \
  --set ingress.host=wms.example.com
```

手机扫码摄像头通常要求 HTTPS。公网 IP 的 HTTP 页面常见问题是浏览器提示没有 `mediaDevices`。

## 七、数据库初始化机制

Helm 相关文件：

```text
D:\projects\wms-springcloud\deploy\helm\wms\templates\mysql.yaml
D:\projects\wms-springcloud\deploy\helm\wms\templates\mysql-init-configmap.yaml
D:\projects\wms-springcloud\deploy\helm\wms\templates\mysql-init-job.yaml
D:\projects\wms-springcloud\deploy\helm\wms\files\wms-cloud-init.sql
```

当前有两条初始化链路：

1. MySQL 数据目录为空时，容器会执行挂载到 `/docker-entrypoint-initdb.d` 的 SQL。
2. Helm `post-install,post-upgrade` 的 `wms-mysql-init` Job 会再次执行 SQL，补齐表结构和基础数据。

注意：当前 SQL 包含 `TRUNCATE` 生产业务表的逻辑，用于重置测试环境：

```text
inbound_order
outbound_order
kanban
inventory
inventory_transaction
agent_* 运行数据
```

如果云端已经承载真实业务数据，升级前必须移除或改造这些清空逻辑，改成迁移脚本或 Flyway/Liquibase。

## 八、回滚

查看历史：

```bash
helm history wms -n wms
```

回滚到指定版本：

```bash
helm rollback wms <REVISION> -n wms --wait --timeout 10m
kubectl get pods -n wms -o wide
```

如果只是某个 Deployment 镜像有问题，仍建议优先 Helm rollback。`kubectl rollout undo` 会被下一次 Helm upgrade 覆盖。

## 九、卸载和彻底清理

卸载 Helm：

```bash
helm uninstall wms -n wms
```

删除 PVC 会清空数据库：

```bash
kubectl delete pvc -n wms -l app=mysql
```

删除 namespace：

```bash
kubectl delete namespace wms
```

谨慎：删除 PVC 或 namespace 会删除 MySQL 数据。

## 十、部署完成检查清单

- `helm status wms -n wms` 为 deployed。
- `kubectl get pods -n wms` 中服务 Pod 都是 Running。
- `wms-mysql-init` Job 为 Completed。
- `kubectl get endpoints -n wms` 中每个 Service 都有后端地址。
- 浏览器能打开 `http://<服务器IP>:30081`。
- 登录接口 `POST /api/auth/login` 成功。
- 基础资料能看到供应商、零件、库位和器具。
