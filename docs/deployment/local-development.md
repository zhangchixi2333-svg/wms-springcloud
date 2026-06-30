# 本地开发启动与验证手册

更新时间：2026-06-30

本文适用于在 Windows PowerShell 中启动 `D:\projects\wms-springcloud` 的本地开发环境。

## 一、前置条件

在 PowerShell 中确认工具存在：

```powershell
docker version
mvn.cmd -version
node --version
npm.cmd --version
```

推荐版本：

| 工具 | 当前项目使用 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.7 |
| Spring Cloud | 2025.0.0 |
| Node | 24 或兼容版本 |
| Vue | 3 |
| MySQL | 8.0 |

## 二、启动 MySQL

项目根目录：

```powershell
cd D:\projects\wms-springcloud
docker compose up -d mysql
```

确认容器：

```powershell
docker ps --filter "name=wms-cloud-mysql"
```

预期看到 `wms-cloud-mysql` 正在运行，并映射端口：

```text
0.0.0.0:3317->3306/tcp
```

## 三、初始化数据库

执行初始化脚本：

```powershell
cd D:\projects\wms-springcloud
docker cp D:\projects\wms-springcloud\sql\wms-cloud-init.sql wms-cloud-mysql:/tmp/wms-cloud-init.sql
docker exec wms-cloud-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 -D wms_cloud -e "SOURCE /tmp/wms-cloud-init.sql;"
```

这个脚本会清空生产业务数据，然后写入基础资料：

- 供应商、客户、库位、器具、零件。
- 用户、角色、菜单、角色菜单。
- 系统配置、默认库存预警。
- Agent 相关表结构和菜单。

检查数据：

```powershell
docker exec wms-cloud-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 -D wms_cloud -e "SELECT COUNT(*) AS suppliers FROM supplier; SELECT COUNT(*) AS parts FROM part; SELECT COUNT(*) AS inbound_orders FROM inbound_order; SELECT COUNT(*) AS outbound_orders FROM outbound_order; SELECT COUNT(*) AS kanbans FROM kanban; SELECT COUNT(*) AS inventory_rows FROM inventory;"
```

预期：

```text
suppliers = 5
parts = 40
inbound_orders = 0
outbound_orders = 0
kanbans = 0
inventory_rows = 0
```

## 四、启动后端服务

一键启动后端：

```powershell
cd D:\projects\wms-springcloud
.\start-services.ps1
```

脚本会启动：

```text
wms-discovery        8761
wms-system-service   8082
wms-masterdata       8083
wms-business         8084
wms-agent            8085
wms-gateway          8080
```

日志目录：

```text
D:\projects\wms-springcloud\.logs
```

查看端口：

```powershell
Get-NetTCPConnection -LocalPort 8761,8080,8082,8083,8084,8085 -State Listen
```

## 五、启动前端

```powershell
cd D:\projects\wms-springcloud\frontend
npm.cmd install
npm.cmd run dev -- --host 0.0.0.0
```

访问：

```text
http://localhost:5173
```

如果手机要在局域网访问，先查看电脑 IP：

```powershell
ipconfig
```

找到无线网卡或以太网卡下的 IPv4 地址，例如：

```text
192.168.1.25
```

手机访问：

```text
http://192.168.1.25:5173
```

注意：手机摄像头扫码在局域网 HTTP 页面上可能被浏览器拦截。`localhost` 通常允许摄像头，局域网 IP 或公网 IP 通常需要 HTTPS。

## 六、接口验证

登录：

```powershell
$body = @{
  username = 'admin'
  password = 'admin123'
} | ConvertTo-Json

Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/login' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

基础资料：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/suppliers?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/parts?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/equipment?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/locations?page=1&pageSize=10'
```

业务分页：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/inbound-orders/page?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/outbound-orders/page?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/kanbans/page?page=1&pageSize=10'
Invoke-RestMethod 'http://127.0.0.1:8080/api/inventory/page?page=1&pageSize=10'
```

Agent：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/agent/health'
```

## 七、停止服务

停止后端和前端进程：

```powershell
cd D:\projects\wms-springcloud
.\stop-services.ps1
```

停止 MySQL 但保留数据：

```powershell
docker compose stop mysql
```

删除 MySQL 数据卷会丢失数据库：

```powershell
docker compose down -v
```

只有明确要重建数据库时才执行 `down -v`。

## 八、常见问题

| 现象 | 排查命令 | 处理 |
| --- | --- | --- |
| 后端连接不上数据库 | `docker ps`, `docker logs wms-cloud-mysql --tail 100` | 确认 MySQL 正常运行并映射 `3317` |
| 登录返回 500 | 查看 `.logs/wms-system-service.err.log` | 多数是 SQL 未初始化或表结构不匹配 |
| 前端 API 跨域 | 看浏览器 Console 和网关日志 | CORS 只应由 `wms-gateway` 处理 |
| 手机不能调用摄像头 | 浏览器提示没有 `mediaDevices` | 使用 HTTPS，或在电脑本机用 `localhost` 测试 |
| 页面数据很慢 | 看 Network 是否某个分页接口挂起 | 优先检查 `kanbans/page`、`inventory/details/page`、`transactions/page` |
