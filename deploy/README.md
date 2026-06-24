# WMS Kubernetes 与 Helm 部署说明

更新时间：2026-06-24

本文说明如何将 `D:\projects\wms-springcloud` 部署到 Kubernetes，并说明当前数据库初始化、镜像构建、服务暴露、排障方式。

## 一、部署目录结构

```text
deploy
├─ docker
│  ├─ Dockerfile.frontend
│  ├─ Dockerfile.service
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
│        └─ frontend.yaml
└─ kubernetes
   ├─ kustomization.yaml
   ├─ mysql
   │  ├─ mysql.yaml
   │  └─ wms-cloud-init.sql
   └─ ...
```

## 二、当前部署链路

```text
浏览器
  -> wms-frontend Service
  -> nginx
  -> /api 转发到 wms-gateway Service
  -> Spring Cloud Gateway
  -> Eureka 注册发现
  -> system/masterdata/business 微服务
  -> mysql Service
  -> mysql StatefulSet
```

对外默认端口：

- 前端：`NodePort 30081`
- 网关：`NodePort 30080`

集群内部 Service：

- `wms-discovery:8761`
- `wms-gateway:8080`
- `wms-system-service:8082`
- `wms-masterdata-service:8083`
- `wms-business-service:8084`
- `mysql:3306`

## 三、部署前准备

本地或服务器需要具备：

```powershell
docker version
kubectl version --client
helm version
```

确认 `kubectl` 已连接到目标集群：

```powershell
kubectl config current-context
kubectl get nodes
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
wms/wms-frontend:0.0.1
```

如需使用远端仓库前缀：

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

- 前端：`http://<NodeIP>:30081`
- 网关：`http://<NodeIP>:30080/api`

清理：

```powershell
kubectl delete -k deploy/kubernetes
kubectl delete pvc -n wms -l app=mysql
```

## 六、使用 Helm 部署

首次创建命名空间：

```powershell
kubectl create namespace wms
```

安装：

```powershell
cd D:\projects\wms-springcloud
helm install wms deploy/helm/wms -n wms
```

自定义镜像仓库与版本：

```powershell
helm upgrade --install wms deploy/helm/wms -n wms `
  --set image.repository=registry.example.com/wms-cloud `
  --set image.tag=v1 `
  --set mysql.image=registry.example.com/wms-cloud:wms-mysql-8.0
```

查看状态：

```powershell
helm status wms -n wms
kubectl get pods -n wms
kubectl get svc -n wms
kubectl get job -n wms
```

卸载：

```powershell
helm uninstall wms -n wms
kubectl delete pvc -n wms -l app=mysql
```

## 七、数据库初始化机制

### 1. 初始化 SQL 文件位置

- `sql/wms-cloud-init.sql`
- `deploy/helm/wms/files/wms-cloud-init.sql`
- `deploy/kubernetes/mysql/wms-cloud-init.sql`

这三份 SQL 现在已经保持一致。

### 2. 初始化触发方式

有两条初始化链路：

1. MySQL 容器首次启动时，如果数据目录为空，会执行挂载到 `/docker-entrypoint-initdb.d` 的 SQL。
2. Helm 部署时还会执行 `wms-mysql-init` Job，再次幂等执行同一份 SQL，确保升级场景也能补齐结构和系统基础数据。

Helm 初始化 Job 模板文件：

- `deploy/helm/wms/templates/mysql-init-job.yaml`

### 3. 当前初始化内容

当前初始化 SQL 只保留以下内容：

- 数据库与表结构
- 用户、角色、菜单、角色菜单
- 系统配置表基础数据
- 默认库存预警模板

默认库存预警模板内容：

```json
{"critical":10,"low":30,"attention":60}
```

对应 `config_item`：

- `module_key = inventoryWarning`
- `item_code = DEFAULT`

### 4. 当前不会再初始化的内容

当前初始化 SQL 不再写入任何业务演示数据，包括但不限于：

- 供应商
- 客户
- 零件
- 库区
- 器具
- 入库单
- 出库单
- 看板
- 库存
- 库存流水

也就是说，新库初始化后是“干净库”，后续业务数据全部由系统实际操作产生。

## 八、镜像与数据库配置

Helm 默认值文件：

- `deploy/helm/wms/values.yaml`

当前关键默认值：

- 业务镜像仓库：`image.repository`
- 业务镜像版本：`image.tag`
- MySQL 镜像：`mysql.image`
- MySQL 库名：`mysql.database`
- MySQL 存储：`mysql.storage`

MySQL 在 Helm 中由以下模板定义：

- `deploy/helm/wms/templates/mysql.yaml`

MySQL 初始化 SQL 在 Helm 中通过 ConfigMap 注入：

- `deploy/helm/wms/templates/mysql-init-configmap.yaml`

## 九、访问与验证

验证登录接口：

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

验证前端：

```text
http://localhost:30081
```

验证 Eureka：

```powershell
kubectl port-forward -n wms svc/wms-discovery 8761:8761
```

浏览器访问：

```text
http://127.0.0.1:8761
```

## 十、常见问题

### 1. Pod 卡在 `ImagePullBackOff`

排查：

```powershell
kubectl describe pod <pod-name> -n wms
```

重点检查：

- 镜像地址是否正确
- 私有仓库拉取密钥是否存在
- 集群节点是否能访问镜像仓库

### 2. MySQL 正常但系统服务起不来

排查：

```powershell
kubectl get pod mysql-0 -n wms
kubectl logs mysql-0 -n wms
kubectl logs job/wms-mysql-init -n wms
kubectl exec -n wms deploy/wms-system-service -- printenv
```

### 3. 网关返回 503

通常表示下游服务尚未注册到 Eureka。

排查：

```powershell
kubectl get pods -n wms
kubectl logs deploy/wms-gateway -n wms --tail=200
kubectl logs deploy/wms-discovery -n wms --tail=200
```

### 4. CORS 重复

当前设计为只在网关处理 CORS。若再次出现：

```text
Access-Control-Allow-Origin contains multiple values
```

说明某个下游服务又单独加了 CORS 配置，需要删除重复配置。
