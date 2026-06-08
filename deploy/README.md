# WMS Kubernetes 和 Helm 部署说明

更新时间：2026-06-08

本文说明如何把 `D:\projects\wms-springcloud` 部署到 Kubernetes。部署方式提供两套：

- 原生 Kubernetes YAML：`deploy/kubernetes`
- Helm Chart：`deploy/helm/wms`

两种方式二选一即可，不要同时部署到同一个 `wms` namespace，避免资源名冲突。

## 1. 当前部署架构

```text
Browser
  |
  | NodePort 30081
  v
wms-frontend (nginx)
  |
  | /api -> wms-gateway:8080
  v
wms-gateway
  |
  | Eureka service discovery
  v
+------------------------+---------------------------+
| wms-system-service     | auth/user/role/menu/config |
| wms-masterdata-service | supplier/customer/part/... |
| wms-business-service   | inbound/outbound/kanban    |
+------------------------+---------------------------+
  |
  v
mysql StatefulSet -> wms_cloud
```

对外入口：

| 入口 | 默认端口 | 作用 |
| --- | --- | --- |
| `wms-frontend` | `NodePort 30081` | 浏览器访问系统 |
| `wms-gateway` | `NodePort 30080` | API 调试入口 |

集群内部：

| 服务 | 端口 |
| --- | --- |
| `wms-discovery` | `8761` |
| `wms-system-service` | `8082` |
| `wms-masterdata-service` | `8083` |
| `wms-business-service` | `8084` |
| `mysql` | `3306` |

## 2. 前置条件

本机需要有：

```powershell
docker version
kubectl version --client
helm version
```

并且已经有可用 Kubernetes 集群，例如 Docker Desktop Kubernetes、kind、minikube 或远程集群。

确认当前 kubectl 指向正确集群：

```powershell
kubectl config current-context
kubectl get nodes
```

## 3. 构建镜像

在项目根目录执行：

```powershell
cd D:\projects\wms-springcloud
.\deploy\docker\build-images.ps1
```

默认生成这些镜像：

```text
wms/wms-discovery:0.0.1
wms/wms-gateway:0.0.1
wms/wms-system-service:0.0.1
wms/wms-masterdata-service:0.0.1
wms/wms-business-service:0.0.1
wms/wms-frontend:0.0.1
```

如果你使用远程镜像仓库，可以设置前缀和版本：

```powershell
$env:WMS_IMAGE_PREFIX = 'registry.example.com/wms'
$env:WMS_IMAGE_TAG = '0.0.1'
.\deploy\docker\build-images.ps1
```

然后推送镜像：

```powershell
docker push registry.example.com/wms/wms-discovery:0.0.1
docker push registry.example.com/wms/wms-gateway:0.0.1
docker push registry.example.com/wms/wms-system-service:0.0.1
docker push registry.example.com/wms/wms-masterdata-service:0.0.1
docker push registry.example.com/wms/wms-business-service:0.0.1
docker push registry.example.com/wms/wms-frontend:0.0.1
```

如果使用 kind，需要把本地镜像导入 kind 集群：

```powershell
kind load docker-image wms/wms-discovery:0.0.1
kind load docker-image wms/wms-gateway:0.0.1
kind load docker-image wms/wms-system-service:0.0.1
kind load docker-image wms/wms-masterdata-service:0.0.1
kind load docker-image wms/wms-business-service:0.0.1
kind load docker-image wms/wms-frontend:0.0.1
```

## 4. 方式一：使用原生 Kubernetes YAML

部署：

```powershell
cd D:\projects\wms-springcloud
kubectl apply -k deploy/kubernetes
```

查看资源：

```powershell
kubectl get pods -n wms
kubectl get svc -n wms
```

等待所有 Pod Ready：

```powershell
kubectl wait --for=condition=Ready pod --all -n wms --timeout=300s
```

访问前端：

```text
http://<NodeIP>:30081
```

Docker Desktop 或 minikube 常见情况下可以使用：

```text
http://localhost:30081
```

访问网关：

```text
http://<NodeIP>:30080/api
```

验证登录：

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

清理：

```powershell
kubectl delete -k deploy/kubernetes
```

如果需要删除 MySQL 数据卷，还要删除 PVC：

```powershell
kubectl delete pvc -n wms -l app=mysql
```

## 5. 方式二：使用 Helm

创建 namespace：

```powershell
kubectl create namespace wms
```

安装：

```powershell
cd D:\projects\wms-springcloud
helm install wms deploy/helm/wms -n wms
```

如果镜像前缀或版本不同：

```powershell
helm install wms deploy/helm/wms -n wms `
  --set image.repository=registry.example.com/wms-cloud `
  --set image.tag=v1
```

查看状态：

```powershell
helm status wms -n wms
kubectl get pods -n wms
kubectl get svc -n wms
```

升级：

```powershell
helm upgrade wms deploy/helm/wms -n wms
```

卸载：

```powershell
helm uninstall wms -n wms
```

删除 namespace 和 PVC：

```powershell
kubectl delete namespace wms
```

## 6. 数据库初始化机制

MySQL 使用 `StatefulSet` 和 PVC 保存数据。

初始化 SQL 来自：

```text
deploy/kubernetes/mysql/wms-cloud-init.sql
deploy/helm/wms/files/wms-cloud-init.sql
```

MySQL 官方镜像只会在数据库目录为空时执行 `/docker-entrypoint-initdb.d` 下的 SQL。也就是说：

- 第一次部署会自动创建 `wms_cloud`、表和默认数据。
- Pod 重启不会重复清库。
- 如果 PVC 已经存在，修改 SQL 后不会自动重新执行。

如果只是补齐默认数据，可以手动执行：

```powershell
kubectl cp deploy/kubernetes/mysql/wms-cloud-init.sql wms/mysql-0:/tmp/wms-cloud-init.sql
kubectl exec -n wms mysql-0 -- sh -c "mysql -uwms -pwms123456 --default-character-set=utf8mb4 wms_cloud < /tmp/wms-cloud-init.sql"
```

## 7. 常见问题

### 7.1 Pod 一直 ImagePullBackOff

原因：

- 集群节点没有这些本地镜像。
- 镜像仓库地址写错。
- 私有仓库没有 imagePullSecret。

处理：

```powershell
kubectl describe pod <pod-name> -n wms
```

如果使用 kind，把镜像导入 kind：

```powershell
kind load docker-image wms/wms-gateway:0.0.1
```

### 7.2 服务启动失败，提示数据库连接失败

检查 MySQL：

```powershell
kubectl get pod mysql-0 -n wms
kubectl logs mysql-0 -n wms
```

进入业务 Pod 检查环境变量：

```powershell
kubectl exec -n wms deploy/wms-system-service -- printenv | Select-String DB_
```

### 7.3 网关 503

原因通常是服务还没注册到 Eureka。

检查 Eureka：

```powershell
kubectl port-forward -n wms svc/wms-discovery 8761:8761
```

浏览器访问：

```text
http://127.0.0.1:8761
```

确认 `WMS-SYSTEM-SERVICE`、`WMS-MASTERDATA-SERVICE`、`WMS-BUSINESS-SERVICE`、`WMS-GATEWAY` 都是 UP。

### 7.4 CORS 重复

当前设计是只由 `wms-gateway` 处理 CORS，下游服务不再配置 CORS。若再次出现：

```text
Access-Control-Allow-Origin contains multiple values
```

检查是否有人给下游服务重新添加了 CORS 配置。
