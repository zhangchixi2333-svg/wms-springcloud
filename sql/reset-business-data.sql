-- 本脚本用于将 WMS 数据库重置为“仅保留基础资料”的状态。
-- 会清空：入库单、出库单、出库分配、看板、库存余额、库存流水。
-- 会保留：用户、角色、菜单、供应商、客户、零件、仓库库区、器具、系统配置。

USE `wms_cloud`;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `outbound_allocation`;
TRUNCATE TABLE `inventory_operation_order`;
TRUNCATE TABLE `inventory_transaction`;
TRUNCATE TABLE `inventory`;
TRUNCATE TABLE `kanban`;
TRUNCATE TABLE `outbound_order_item`;
TRUNCATE TABLE `outbound_order`;
TRUNCATE TABLE `inbound_order_item`;
TRUNCATE TABLE `inbound_order`;
SET FOREIGN_KEY_CHECKS = 1;

SELECT 'inbound_order' AS `table_name`, COUNT(*) AS `row_count` FROM `inbound_order`
UNION ALL SELECT 'inbound_order_item', COUNT(*) FROM `inbound_order_item`
UNION ALL SELECT 'outbound_order', COUNT(*) FROM `outbound_order`
UNION ALL SELECT 'outbound_order_item', COUNT(*) FROM `outbound_order_item`
UNION ALL SELECT 'outbound_allocation', COUNT(*) FROM `outbound_allocation`
UNION ALL SELECT 'inventory_operation_order', COUNT(*) FROM `inventory_operation_order`
UNION ALL SELECT 'kanban', COUNT(*) FROM `kanban`
UNION ALL SELECT 'inventory', COUNT(*) FROM `inventory`
UNION ALL SELECT 'inventory_transaction', COUNT(*) FROM `inventory_transaction`
UNION ALL SELECT 'supplier', COUNT(*) FROM `supplier`
UNION ALL SELECT 'customer', COUNT(*) FROM `customer`
UNION ALL SELECT 'part', COUNT(*) FROM `part`
UNION ALL SELECT 'location', COUNT(*) FROM `location`
UNION ALL SELECT 'equipment', COUNT(*) FROM `equipment`;
