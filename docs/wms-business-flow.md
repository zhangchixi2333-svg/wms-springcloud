# WMS 入库、出库、扫码与库存流水说明

更新时间：2026-06-30

本文说明当前 WMS 的核心业务流程。它以“单据负责业务批次、看板负责实际箱子”为边界，避免把入库单或出库单错误建模成父看板。

## 一、模型原则

```text
入库单 = 一次计划收货业务
出库单 = 一次计划发货业务
看板 = 一个实际包装箱
库存 = 看板数量在库位上的汇总
库存流水 = 每一次数量或位置变化的审计记录
```

入库单二维码和出库单二维码是单据码，不写入 `kanban` 表。看板二维码才代表箱子。

## 二、核心表

| 表 | 作用 |
| --- | --- |
| `inbound_order` | 入库单头，保存供应商、状态、创建时间、退回状态 |
| `inbound_order_item` | 入库明细，保存零件、计划数量、箱数、每箱数量、器具、计划库位 |
| `kanban` | 箱级看板，一行代表一个箱子 |
| `outbound_order` | 出库单头，保存客户、状态、出库批次 |
| `outbound_order_item` | 出库计划明细，保存要出哪个零件、计划数量、箱数、目标包装方式 |
| `outbound_allocation` | 出库分配明细，保存本出库单从哪个看板扣多少数量 |
| `inventory` | 当前库存汇总，按零件和库位统计 |
| `inventory_transaction` | 库存流水，记录入库、出库、移库、转包、封存等变化 |

## 三、入库流程

1. 操作员创建入库单，选择供应商和入库明细。
2. 每条明细填写总数量，系统根据器具默认容量计算箱数：
   ```text
   箱数 = 向上取整(总数量 / 每箱容量)
   ```
3. 后端按箱数生成箱级看板，每个看板代表一个箱子。
4. 打印入库单时展示：
   - 入库单号。
   - 入库单二维码：`WMS-INBOUND|入库单号`。
   - 按零件分组的箱级看板。
   - 每个箱级看板二维码：`WMS-KANBAN|看板号|条码`。
5. 扫描入库单二维码时，系统批量入库该入库单下所有待入库箱。
6. 扫描箱级看板二维码时，系统只入库该箱。
7. 入库成功后：
   - 看板状态变为已入库。
   - 写入看板库位和入库时间。
   - 增加入库明细已收数量。
   - 增加库存汇总。
   - 写入库存流水。
8. 入库单状态按明细收货数量同步：
   - 全部未入库：已创建。
   - 部分箱子已入库：部分入库。
   - 全部箱子已入库：已入库。

关键代码：

```text
D:\projects\wms-springcloud\wms-business-service\src\main\java\com\example\wms\service\WmsService.java
createInboundOrder()
generateKanbans()
scanInbound()
scanInboundBatch()
applyInboundForSingleKanban()
syncInboundOrderStatus()
```

关键页面：

```text
D:\projects\wms-springcloud\frontend\src\components\pages\operations\InboundPage.vue
D:\projects\wms-springcloud\frontend\src\components\pages\operations\MobileScanPage.vue
```

## 四、入库退回

只有已创建且尚未入库的入库单允许退回。退回后：

- 入库单状态变为已退回。
- 该入库单生成的待入库看板标记为已退回。
- 已入库的箱子不能被退回，防止库存和流水不一致。

批量退回时，前端只允许选择满足条件的入库单。

## 五、出库流程

出库不再手工绑定入库单。创建出库单时只指定：

- 客户。
- 出库零件。
- 出库数量。
- 出库箱数或包装方式。
- 可选库位范围。

后端按 FIFO 自动分配库存：

1. 查询对应零件的已入库、未封存、可出库看板。
2. 按入库时间、创建时间、看板序号排序。
3. 从最早入库的箱子开始锁定数量。
4. 写入 `outbound_allocation`。
5. 增加看板的出库锁定数量。
6. 创建出库单后只是锁定，不立即扣减库存。

扫码出库时：

1. 扫描出库单二维码：`WMS-OUTBOUND|出库单号`。
2. 后端找到该出库单所有未完成分配。
3. 按分配数量扣减对应看板。
4. 更新 `outbound_allocation` 已出库数量和状态。
5. 更新 `outbound_order_item` 已扫码数量。
6. 扣减库存汇总。
7. 写入库存流水。
8. 同步看板状态：
   - 箱内仍有剩余数量：部分出库。
   - 箱内数量全部扣完：已出库。
9. 同步出库单状态：
   - 部分分配完成：部分出库。
   - 全部分配完成：已完成。

关键代码：

```text
D:\projects\wms-springcloud\wms-business-service\src\main\java\com\example\wms\service\WmsService.java
createOutboundOrder()
allocateOutboundQty()
scanOutbound()
resolveOutboundOrderForScan()
resolveOutboundScanTargets()
applyOutboundAllocationScan()
refreshKanbanQuantityState()
syncOutboundOrderStatus()
```

关键页面：

```text
D:\projects\wms-springcloud\frontend\src\components\pages\operations\OutboundPage.vue
D:\projects\wms-springcloud\frontend\src\components\pages\operations\outbound-modals\OutboundCreateModal.vue
D:\projects\wms-springcloud\frontend\src\components\pages\operations\MobileScanPage.vue
```

## 六、出库取消

只有已创建状态的出库单允许取消。取消后：

- 出库单状态变为已取消。
- 释放 `outbound_allocation` 锁定数量。
- 回滚看板上的出库锁定数量。
- 不写出库扣减流水，因为库存尚未真正减少。

部分出库或已完成出库单不允许取消，必须通过后续业务流程处理。

## 七、打印结构

入库打印结构：

```text
入库单卡片
  单据二维码紧贴单据信息
  入库单基本信息
  零件分组 A
    箱级看板 1
    箱级看板 2
  零件分组 B
    箱级看板 3
```

出库打印结构：

```text
出库单卡片
  单据二维码紧贴单据信息
  出库汇总信息
  零件分组 A
    分配看板 1：计划出库数量、已出库数量、剩余数量
    分配看板 2：计划出库数量、已出库数量、剩余数量
```

二维码组件：

```text
D:\projects\wms-springcloud\frontend\src\components\shared\QrCodeImage.vue
```

## 八、扫码接口

| 接口 | 用途 |
| --- | --- |
| `POST /api/mobile/scan/inbound` | 扫入库单二维码或箱级看板二维码，直接执行入库 |
| `POST /api/mobile/scan/inbound-batch` | 入库页面手动选择多个箱级看板后批量入库 |
| `POST /api/mobile/scan/outbound` | 扫出库单二维码或箱级看板二维码，按出库单分配执行出库 |

二维码示例：

```text
WMS-INBOUND|IN20260630101000
WMS-OUTBOUND|OUT20260630101100
WMS-KANBAN|KB202606301010000101|BC-XXXXXXXXXXXX
```

移动扫码页面：

```text
D:\projects\wms-springcloud\frontend\src\components\pages\operations\MobileScanPage.vue
```

浏览器摄像头要求：

- `localhost` 通常允许摄像头。
- 局域网 IP 和云端 IP 通常需要 HTTPS，否则浏览器可能没有 `navigator.mediaDevices`。
- 如果手机访问远端仍提示没有 `mediaDevices`，优先检查是否通过 HTTPS 打开页面。

## 九、库存流水

库存流水记录每一次库存变化。典型业务类型：

| 业务 | 是否改变总库存 | 说明 |
| --- | --- | --- |
| 入库 | 增加 | 箱级看板入库后增加库存 |
| 出库 | 减少 | 客户出库后库存离开系统 |
| 自有移库 | 不变 | 自有仓之间移动，只改变库位 |
| 转包 | 不变 | 自有仓移动到第三方仓 |
| 转包返还 | 不变 | 第三方仓移动回自有仓 |
| 封存 | 不变 | 改变可用性，不改变数量 |

流水查询接口支持分页：

```text
GET /api/inventory/transactions/page
```

前端在库存明细浮窗中按需拉取流水，避免首次进入页面一次性加载所有历史记录。

## 十、状态解释

| 范围 | 状态 | 中文含义 |
| --- | --- | --- |
| 入库单 | `CREATED` | 已创建 |
| 入库单 | `PARTIAL_INBOUND` | 部分入库 |
| 入库单 | `INBOUND` | 已入库 |
| 入库单 | `RETURNED` | 已退回 |
| 出库单 | `CREATED` | 已创建 |
| 出库单 | `PARTIAL_OUTBOUND` | 部分出库 |
| 出库单 | `COMPLETED` | 已完成 |
| 出库单 | `CANCELLED` | 已取消 |
| 看板 | `WAIT_SCAN` / `CREATED` | 待入库 |
| 看板 | `INBOUND` / `IN_STOCK` | 已入库或在库 |
| 看板 | `ALLOCATED` | 已分配待出库 |
| 看板 | `PARTIAL_OUTBOUND` | 部分出库 |
| 看板 | `OUTBOUND` | 已出库 |
| 看板 | `THIRD_PARTY_STOCK` | 第三方在库 |
| 看板 | `FROZEN` | 已封存 |
| 看板 | `CONSUMED` | 已消耗 |
| 看板 | `RETURNED` | 已退回 |

## 十一、验证思路

最小验证链路：

```text
1. 登录系统。
2. 创建入库单，确认自动生成箱级看板。
3. 打印入库单，确认入库单二维码和看板二维码都出现。
4. 扫描入库单二维码，确认所有待入库箱入库。
5. 创建出库单，确认后端按 FIFO 生成出库分配。
6. 打印出库单，确认每个分配看板显示出库数量。
7. 扫描出库单二维码，确认库存减少、看板状态更新、流水生成。
8. 打开库存看板和看板信息，确认分页查询正常。
```
