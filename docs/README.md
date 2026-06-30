# WMS 项目文档索引

更新时间：2026-06-30

本文档目录用于承载项目架构、业务模型、部署运行和问题排查说明。阅读顺序建议从项目总览开始，再进入业务流程和部署手册。

## 一、架构与业务文档

| 文档 | 适合什么时候看 |
| --- | --- |
| [../README.md](../README.md) | 第一次打开项目，了解项目定位、目录和本地启动方式 |
| [microservice-design.md](microservice-design.md) | 需要理解 Spring Cloud 微服务拆分、网关、Kubernetes Service 通信方式 |
| [wms-business-flow.md](wms-business-flow.md) | 需要理解入库、出库、打印、扫码、库存扣减和状态变化 |
| [kanban-transfer-design.md](kanban-transfer-design.md) | 需要理解一个看板为什么只代表一个箱子，以及移库、转包、封存如何建模 |
| [agent-system-design.md](agent-system-design.md) | 需要理解 Agent 助手、库存预测、RAG 预留和不影响主业务的降级策略 |

## 二、部署文档

| 文档 | 内容 |
| --- | --- |
| [deployment/local-development.md](deployment/local-development.md) | Windows 本地开发启动、数据库初始化、接口验证 |
| [../deploy/README.md](../deploy/README.md) | Docker、原生 Kubernetes、Helm 部署总入口 |
| [deployment/kubernetes-helm-runbook.md](deployment/kubernetes-helm-runbook.md) | k3s/Helm 一步一步部署、验证、回滚、清理 |
| [../deploy/github-actions.md](../deploy/github-actions.md) | GitHub Actions 构建镜像、推送 ACR、部署云服务器 |
| [deployment/troubleshooting.md](deployment/troubleshooting.md) | 常见故障：ImagePullBackOff、CrashLoopBackOff、网关 503、Helm pending、摄像头 HTTPS |

## 三、当前固定环境参数

| 项 | 值 |
| --- | --- |
| 项目根目录 | `D:\projects\wms-springcloud` |
| 本地数据库 | `127.0.0.1:3317/wms_cloud` |
| Kubernetes 命名空间 | `wms` |
| Helm release | `wms` |
| Helm chart | `deploy/helm/wms` |
| 前端 NodePort | `30081` |
| 网关 NodePort | `30080` |
| 云端镜像仓库 | `crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud` |

## 四、服务端口

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| `wms-discovery` | `8761` | 本地 Eureka 注册中心 |
| `wms-gateway` | `8080` | API 网关 |
| `wms-system-service` | `8082` | 系统管理 |
| `wms-masterdata-service` | `8083` | 基础资料 |
| `wms-business-service` | `8084` | 仓储业务 |
| `wms-agent-service` | `8085` | Agent 助手 |
| `wms-frontend` | `80` | 前端 nginx |
| `mysql` | `3306` | 集群内 MySQL |

## 五、维护原则

- 修改数据库结构后，同步更新三份 SQL：
  - `sql/wms-cloud-init.sql`
  - `deploy/helm/wms/files/wms-cloud-init.sql`
  - `deploy/kubernetes/mysql/wms-cloud-init.sql`
- 修改部署参数后，同步检查：
  - `deploy/helm/wms/values.yaml`
  - `.github/workflows/build-and-push-images.yml`
  - `deploy/README.md`
  - `docs/deployment/kubernetes-helm-runbook.md`
- 修改业务状态或扫码规则后，同步更新：
  - `docs/wms-business-flow.md`
  - `docs/kanban-transfer-design.md`
