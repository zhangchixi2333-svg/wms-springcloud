# GitHub Actions 镜像构建推送说明

更新时间：2026-06-08

工作流文件：

```text
.github/workflows/build-and-push-images.yml
```

触发方式：

- 推送到 `main` 分支时自动触发。
- GitHub Actions 页面手动 `Run workflow`。

## 1. 镜像仓库

当前使用一个阿里云 ACR 仓库保存所有服务镜像：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud
```

因为是单仓库，所以不同服务通过 tag 区分。

## 2. Tag 规则

每次推送会为每个服务生成两类 tag：

```text
服务名-短提交号
服务名-main
```

示例：

```text
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-discovery-a1b2c3d
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-discovery-main

crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-gateway-a1b2c3d
crpi-i0bdeprulhtq3581.cn-guangzhou.personal.cr.aliyuncs.com/fuliang-hub/wms-cloud:wms-gateway-main
```

`服务名-main` 适合测试环境一直跟随 main 最新版本。

`服务名-短提交号` 适合生产环境固定版本和回滚。

## 3. 后续云服务器自动部署

购买云服务器后，可以继续增加一个 deploy job：

```text
GitHub Actions -> SSH 登录云服务器 -> docker compose pull/up 或 helm upgrade
```

需要新增的 Secret 通常包括：

```text
SERVER_HOST
SERVER_USER
SERVER_SSH_KEY
SERVER_PORT
```

当前本次只完成“构建镜像并推送到阿里云 ACR”。
