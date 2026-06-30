# WMS 部署与运行常见问题排查

更新时间：2026-06-30

本文用于排查本地、Docker、Kubernetes、Helm、GitHub Actions 和浏览器扫码问题。

## 一、先看当前状态

服务器 Bash：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get pods -n wms -o wide
kubectl get svc,endpoints -n wms
kubectl get events -n wms --sort-by=.lastTimestamp | tail -n 80
helm status wms -n wms || true
helm history wms -n wms || true
```

原则：

```text
Pod Running 不代表服务可用。
Service 存在不代表有 Endpoints。
Helm deployed 不代表业务链路正常。
老日志不代表当前故障。
```

尽量看最近日志：

```bash
kubectl logs deploy/wms-gateway -n wms --since=3m
```

CrashLoopBackOff 看上一次崩溃日志：

```bash
kubectl logs deploy/wms-business-service -n wms --previous --tail=200
```

## 二、ImagePullBackOff / ErrImagePull

现象：

```text
mysql-0 0/1 ImagePullBackOff
wms-business-service 0/1 Init:ImagePullBackOff
```

证据命令：

```bash
kubectl describe pod <pod-name> -n wms
kubectl get secret aliyun-acr -n wms
kubectl get pod <pod-name> -n wms -o jsonpath='{.spec.containers[*].image}'; echo
kubectl get pod <pod-name> -n wms -o jsonpath='{.spec.initContainers[*].image}'; echo
```

常见原因：

- ACR Secret 不存在或密码错误。
- 镜像 tag 没推上去。
- MySQL mirror `wms-mysql-8.0` 被删除。
- Helm values 中 `mysql.image` 或 `image.repository` 设置错。

修复 Secret：

```bash
kubectl delete secret aliyun-acr -n wms --ignore-not-found
kubectl create secret docker-registry aliyun-acr \
  -n wms \
  --docker-server=crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com \
  --docker-username='<ACR用户名>' \
  --docker-password='<ACR密码>'
```

验证 MySQL 镜像名：

```bash
helm get values wms -n wms
kubectl get statefulset mysql -n wms -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
```

## 三、CrashLoopBackOff

现象：

```text
wms-business-service 0/1 CrashLoopBackOff
wms-masterdata-service 0/1 CrashLoopBackOff
```

证据命令：

```bash
kubectl logs deploy/wms-business-service -n wms --previous --tail=240
kubectl logs deploy/wms-masterdata-service -n wms --previous --tail=240
kubectl describe pod -n wms -l app=wms-business-service
```

常见原因：

| 日志特征 | 原因 | 修复 |
| --- | --- | --- |
| `Unknown column` | SQL 没同步或 init job 没执行 | 同步三份 SQL，重新执行初始化 |
| `Table doesn't exist` | 数据库未初始化 | 查看 `wms-mysql-init` Job 日志 |
| `Access denied` | MySQL Secret 用户密码不匹配 | 检查 `wms-mysql-secret` |
| `Communications link failure` | 服务连不上 mysql Service | 检查 `mysql` Pod、Service、Endpoints |

查看初始化日志：

```bash
kubectl get job -n wms
kubectl logs job/wms-mysql-init -n wms --tail=200
```

## 四、网关 503 或后端不可达

现象：

```text
GET /api/... 503
gateway cannot route to service
```

先查网关环境变量：

```bash
kubectl exec -n wms deploy/wms-gateway -- sh -c "printenv | grep -E 'EUREKA_CLIENT_ENABLED|WMS_.*SERVICE_URI'"
```

预期：

```text
EUREKA_CLIENT_ENABLED=false
WMS_SYSTEM_SERVICE_URI=http://wms-system-service:8082
WMS_MASTERDATA_SERVICE_URI=http://wms-masterdata-service:8083
WMS_BUSINESS_SERVICE_URI=http://wms-business-service:8084
WMS_AGENT_SERVICE_URI=http://wms-agent-service:8085
```

查 Service Endpoints：

```bash
kubectl get svc,endpoints -n wms wms-system-service wms-masterdata-service wms-business-service wms-agent-service
```

如果 Endpoints 为空，说明 Service selector 没匹配到 Ready Pod，继续查对应 Pod：

```bash
kubectl get pods -n wms --show-labels
kubectl describe svc wms-business-service -n wms
```

## 五、Helm pending 或 another operation in progress

现象：

```text
UPGRADE FAILED: another operation (install/upgrade/rollback) is in progress
STATUS: pending-upgrade
```

查看：

```bash
helm status wms -n wms
helm history wms -n wms
```

回滚到最近正常版本：

```bash
helm rollback wms <REVISION> -n wms --wait --timeout 10m
```

如果没有可用 revision，说明 release 初始安装都没成功，可以考虑卸载重装：

```bash
helm uninstall wms -n wms
```

谨慎：不要直接删除 PVC，除非确认要重置数据库。

## 六、MySQL 初始化 Job 超时

现象：

```text
kubectl wait --for=condition=complete job/wms-mysql-init -n wms --timeout=600s
```

超时。

排查：

```bash
kubectl get pod -n wms -l app=wms-mysql-init
kubectl describe pod -n wms -l app=wms-mysql-init
kubectl logs job/wms-mysql-init -n wms --tail=200
kubectl logs mysql-0 -n wms --tail=200
```

常见原因：

- MySQL 还没 Ready。
- MySQL 密码 Secret 不匹配。
- SQL 语法错误。
- SQL 正在清理大量数据，执行时间过长。

## 七、浏览器提示没有 mediaDevices

现象：

```text
当前浏览器没有 mediaDevices，通常是浏览器过旧或页面环境不安全
```

原因通常不是代码逻辑，而是浏览器安全策略：

- `localhost` 可用摄像头。
- `http://局域网IP` 不一定可用。
- `http://公网IP` 通常不可用。
- HTTPS 页面更稳定。

处理：

1. 本机先用 `http://localhost:5173` 验证摄像头。
2. 手机局域网访问失败时，考虑给前端配置 HTTPS。
3. 云端使用 Ingress + TLS 或反向代理 HTTPS。

## 八、GitHub Actions 构建拉 Docker Hub 超时

现象：

```text
failed to fetch oauth token
read: connection reset by peer
```

原因：GitHub runner 拉 `maven`、`eclipse-temurin` 或 `mysql:8.0` 时访问 Docker Hub 不稳定。

当前 Dockerfile 支持构建参数：

```text
MAVEN_IMAGE
JRE_IMAGE
NODE_IMAGE
NGINX_IMAGE
```

可以在 workflow 中按需切到镜像加速源或 ACR mirror。MySQL 已在 workflow 中 mirror 到：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-mysql-8.0
```

## 九、前端打开但 API 失败

排查顺序：

```bash
kubectl logs deploy/wms-frontend -n wms --since=3m
kubectl logs deploy/wms-gateway -n wms --since=3m
kubectl get endpoints -n wms wms-gateway
```

nginx 配置：

```text
D:\projects\wms-springcloud\deploy\docker\nginx.conf
```

关键转发：

```nginx
location /api/ {
  proxy_pass http://wms-gateway:8080/api/;
}
```

因此前端容器必须能通过 Service 名称 `wms-gateway` 访问网关。

## 十、排障完成检查

每次修复后至少确认三层：

```text
1. 资源层：Pod、Service、Endpoints、Secret、ConfigMap 正确。
2. 运行层：最近日志没有当前错误，rollout 已完成。
3. 用户层：浏览器页面或 API 请求实际成功。
```
