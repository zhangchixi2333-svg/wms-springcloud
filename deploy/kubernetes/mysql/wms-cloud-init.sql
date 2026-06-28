-- WMS Spring Cloud MySQL 完整初始化脚本。
-- 适用场景：本地 Docker Compose、原生 Kubernetes、Helm 初始化 Job。
-- 幂等原则：只创建缺失库表、补齐缺失字段和索引、更新系统基础配置；不会 DROP 或 TRUNCATE 任何业务表。
-- 数据原则：只保留账号、角色、菜单、权限、系统配置和默认库存预警；不写入供应商、客户、零件、入库、出库、库存、看板等业务演示数据。
-- 同步要求：修改本文件后必须同步到 deploy/helm/wms/files/wms-cloud-init.sql 和 deploy/kubernetes/mysql/wms-cloud-init.sql。

-- 一、数据库和字符集。
CREATE DATABASE IF NOT EXISTS `wms_cloud`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `wms_cloud`;
SET NAMES utf8mb4;

-- 二、基础资料、系统权限和配置表结构。
CREATE TABLE IF NOT EXISTS `supplier` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `supplier_code` VARCHAR(64) NOT NULL,
  `supplier_name` VARCHAR(128) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `customer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `customer_code` VARCHAR(64) NOT NULL,
  `customer_name` VARCHAR(128) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_code` (`customer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `avatar_color` VARCHAR(16) NOT NULL,
  `display_name` VARCHAR(128) NOT NULL,
  `role_name` VARCHAR(64) NOT NULL,
  `username` VARCHAR(64) NOT NULL,
  `auth_token` VARCHAR(128) DEFAULT NULL,
  `password_hash` VARCHAR(128) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `app_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `enabled` BIT(1) NOT NULL,
  `permission_level` VARCHAR(32) NOT NULL,
  `role_code` VARCHAR(64) NOT NULL,
  `role_name` VARCHAR(128) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `menu_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `icon_key` VARCHAR(64) DEFAULT NULL,
  `menu_key` VARCHAR(64) NOT NULL,
  `menu_name` VARCHAR(128) NOT NULL,
  `menu_type` VARCHAR(32) NOT NULL,
  `page_key` VARCHAR(64) DEFAULT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `path_key` VARCHAR(64) DEFAULT NULL,
  `sort_order` INT NOT NULL,
  `visible` BIT(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_item_key` (`menu_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `menu_id` BIGINT NOT NULL,
  `role_code` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_code`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `config_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `item_name` VARCHAR(128) NOT NULL,
  `module_key` VARCHAR(64) NOT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_module_code` (`module_key`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `part` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `part_code` VARCHAR(64) NOT NULL,
  `part_name` VARCHAR(128) NOT NULL,
  `unit` VARCHAR(32) NOT NULL,
  `supplier_id` BIGINT DEFAULT NULL,
  `default_equipment_code` VARCHAR(64) DEFAULT NULL,
  `default_package_capacity` DECIMAL(18,3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_code` (`part_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @part_default_equipment_code_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'wms_cloud'
    AND TABLE_NAME = 'part'
    AND COLUMN_NAME = 'default_equipment_code'
);
SET @part_default_equipment_code_sql := IF(
  @part_default_equipment_code_exists = 0,
  'ALTER TABLE `part` ADD COLUMN `default_equipment_code` VARCHAR(64) DEFAULT NULL AFTER `supplier_id`',
  'SELECT 1'
);
PREPARE stmt_part_default_equipment_code FROM @part_default_equipment_code_sql;
EXECUTE stmt_part_default_equipment_code;
DEALLOCATE PREPARE stmt_part_default_equipment_code;

CREATE TABLE IF NOT EXISTS `equipment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `capacity` DECIMAL(18,3) DEFAULT NULL,
  `equipment_code` VARCHAR(64) NOT NULL,
  `equipment_model` VARCHAR(64) NOT NULL,
  `equipment_name` VARCHAR(128) NOT NULL,
  `equipment_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `warehouse_name` VARCHAR(128) DEFAULT NULL,
  `zone_name` VARCHAR(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_code` (`equipment_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `location` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `location_code` VARCHAR(64) NOT NULL,
  `location_name` VARCHAR(128) NOT NULL,
  `warehouse_name` VARCHAR(128) NOT NULL,
  `zone_name` VARCHAR(128) NOT NULL,
  `warehouse_type` VARCHAR(32) NOT NULL DEFAULT 'OWN',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_code` (`location_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 三、仓储业务表结构：入库、出库、看板、库存和库存流水。
CREATE TABLE IF NOT EXISTS `inbound_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6) NOT NULL,
  `inbound_no` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `supplier_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_no` (`inbound_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `inbound_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `inbound_order_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `planned_qty` DECIMAL(18,3) NOT NULL,
  `received_qty` DECIMAL(18,3) NOT NULL,
  `box_count` INT NOT NULL,
  `equipment_code` VARCHAR(64) DEFAULT NULL,
  `unit_per_box` DECIMAL(18,3) DEFAULT NULL,
  `pending_repack` BIT(1) NOT NULL,
  `warehouse_zone` VARCHAR(128) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `outbound_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6) NOT NULL,
  `customer_id` BIGINT DEFAULT NULL,
  `outbound_no` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `inbound_order_nos` VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbound_no` (`outbound_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `outbound_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `outbound_order_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `kanban_id` BIGINT DEFAULT NULL,
  `kanban_no` VARCHAR(64) DEFAULT NULL,
  `planned_qty` DECIMAL(18,3) NOT NULL,
  `scanned_qty` DECIMAL(18,3) NOT NULL,
  `warehouse_name` VARCHAR(128) DEFAULT NULL,
  `zone_name` VARCHAR(128) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `kanban` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `barcode` VARCHAR(128) NOT NULL,
  `batch_no` VARCHAR(64) DEFAULT NULL,
  `box_index` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(6) NOT NULL,
  `frozen` BIT(1) NOT NULL,
  `inbound_order_id` BIGINT NOT NULL,
  `inbound_order_item_id` BIGINT NOT NULL,
  `inbound_time` DATETIME(6) DEFAULT NULL,
  `kanban_no` VARCHAR(64) NOT NULL,
  `location_id` BIGINT DEFAULT NULL,
  `outbound_time` DATETIME(6) DEFAULT NULL,
  `parent_kanban` BIT(1) NOT NULL DEFAULT b'0',
  `parent_kanban_id` BIGINT DEFAULT NULL,
  `part_id` BIGINT NOT NULL,
  `qty` DECIMAL(18,3) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `supplier_id` BIGINT NOT NULL,
  `outbound_order_no` VARCHAR(64) DEFAULT NULL,
  `qr_content` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kanban_barcode` (`barcode`),
  UNIQUE KEY `uk_kanban_no` (`kanban_no`),
  UNIQUE KEY `uk_kanban_qr_content` (`qr_content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 四、历史库升级补字段：兼容旧版本已存在的数据库，重复执行安全。
SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'inbound_order_item'
        AND COLUMN_NAME = 'unit_per_box'
    ),
    'SELECT 1',
    'ALTER TABLE `inbound_order_item` ADD COLUMN `unit_per_box` DECIMAL(18,3) DEFAULT NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'outbound_order_item'
        AND COLUMN_NAME = 'kanban_id'
    ),
    'SELECT 1',
    'ALTER TABLE `outbound_order_item` ADD COLUMN `kanban_id` BIGINT DEFAULT NULL AFTER `part_id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'outbound_order_item'
        AND COLUMN_NAME = 'kanban_no'
    ),
    'SELECT 1',
    'ALTER TABLE `outbound_order_item` ADD COLUMN `kanban_no` VARCHAR(64) DEFAULT NULL AFTER `kanban_id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'kanban'
        AND COLUMN_NAME = 'parent_kanban_id'
    ),
    'SELECT 1',
    'ALTER TABLE `kanban` ADD COLUMN `parent_kanban_id` BIGINT DEFAULT NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'kanban'
        AND COLUMN_NAME = 'parent_kanban'
    ),
    'SELECT 1',
    'ALTER TABLE `kanban` ADD COLUMN `parent_kanban` BIT(1) NOT NULL DEFAULT b''0'''
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'kanban'
        AND COLUMN_NAME = 'box_index'
    ),
    'SELECT 1',
    'ALTER TABLE `kanban` ADD COLUMN `box_index` INT NOT NULL DEFAULT 0'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'inbound_order_item'
        AND COLUMN_NAME = 'package_capacity'
    ),
    'UPDATE `inbound_order_item` SET `unit_per_box` = COALESCE(`unit_per_box`, `package_capacity`) WHERE `unit_per_box` IS NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 五、库存余额和库存流水表结构。
CREATE TABLE IF NOT EXISTS `inventory` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `location_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `qty` DECIMAL(18,3) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_location` (`part_id`, `location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `inventory_transaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `barcode` VARCHAR(128) NOT NULL,
  `business_no` VARCHAR(64) NOT NULL,
  `business_type` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `location_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `qty_change` DECIMAL(18,3) NOT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `transaction_no` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_transaction_no` (`transaction_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 六、查询性能索引：库存看板、流水趋势和配置查询会使用这些索引。
SET @idx_inventory_part_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'inventory'
    AND INDEX_NAME = 'idx_inventory_part'
);
SET @idx_inventory_part_sql := IF(
  @idx_inventory_part_exists = 0,
  'ALTER TABLE `inventory` ADD INDEX `idx_inventory_part` (`part_id`)',
  'SELECT 1'
);
PREPARE stmt_idx_inventory_part FROM @idx_inventory_part_sql;
EXECUTE stmt_idx_inventory_part;
DEALLOCATE PREPARE stmt_idx_inventory_part;

SET @idx_inventory_tx_part_created_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'inventory_transaction'
    AND INDEX_NAME = 'idx_inventory_tx_part_created'
);
SET @idx_inventory_tx_part_created_sql := IF(
  @idx_inventory_tx_part_created_exists = 0,
  'ALTER TABLE `inventory_transaction` ADD INDEX `idx_inventory_tx_part_created` (`part_id`, `created_at`, `qty_change`)',
  'SELECT 1'
);
PREPARE stmt_idx_inventory_tx_part_created FROM @idx_inventory_tx_part_created_sql;
EXECUTE stmt_idx_inventory_tx_part_created;
DEALLOCATE PREPARE stmt_idx_inventory_tx_part_created;

SET @idx_config_module_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'config_item'
    AND INDEX_NAME = 'idx_config_module_status'
);
SET @idx_config_module_status_sql := IF(
  @idx_config_module_status_exists = 0,
  'ALTER TABLE `config_item` ADD INDEX `idx_config_module_status` (`module_key`, `status`)',
  'SELECT 1'
);
PREPARE stmt_idx_config_module_status FROM @idx_config_module_status_sql;
EXECUTE stmt_idx_config_module_status;
DEALLOCATE PREPARE stmt_idx_config_module_status;

-- 七、Agent 智能助手表结构：旁路读取业务数据，结果只写入 agent_* 表。
CREATE TABLE IF NOT EXISTS `agent_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_no` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `call_api` BIT(1) NOT NULL DEFAULT b'0',
  `forecast_days` INT NOT NULL,
  `suggestion_count` INT NOT NULL DEFAULT 0,
  `started_at` DATETIME(6) NOT NULL,
  `finished_at` DATETIME(6) DEFAULT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_no` (`run_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_forecast_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT NOT NULL,
  `part_id` BIGINT NOT NULL,
  `part_code` VARCHAR(64) NOT NULL,
  `part_name` VARCHAR(128) NOT NULL,
  `location_id` BIGINT DEFAULT NULL,
  `location_code` VARCHAR(64) DEFAULT NULL,
  `current_qty` DECIMAL(18,3) NOT NULL,
  `avg_daily_out_qty` DECIMAL(18,3) NOT NULL,
  `forecast_days` INT NOT NULL,
  `forecast_qty` DECIMAL(18,3) NOT NULL,
  `estimated_stockout_date` DATE DEFAULT NULL,
  `risk_level` VARCHAR(32) NOT NULL,
  `suggested_replenish_qty` DECIMAL(18,3) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_forecast_run` (`run_id`),
  KEY `idx_agent_forecast_part` (`part_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_suggestion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `run_id` BIGINT DEFAULT NULL,
  `suggestion_type` VARCHAR(64) NOT NULL,
  `risk_level` VARCHAR(32) NOT NULL,
  `part_id` BIGINT DEFAULT NULL,
  `part_code` VARCHAR(64) DEFAULT NULL,
  `title` VARCHAR(255) NOT NULL,
  `content` VARCHAR(1000) NOT NULL,
  `action_key` VARCHAR(64) DEFAULT NULL,
  `target_page_key` VARCHAR(64) DEFAULT NULL,
  `target_business_no` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_suggestion_run` (`run_id`),
  KEY `idx_agent_suggestion_part` (`part_code`),
  KEY `idx_agent_suggestion_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(64) NOT NULL,
  `role` VARCHAR(32) NOT NULL,
  `content` TEXT NOT NULL,
  `call_api` BIT(1) NOT NULL DEFAULT b'0',
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_chat_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_rag_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_key` VARCHAR(128) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `content` MEDIUMTEXT NOT NULL,
  `metadata_json` JSON DEFAULT NULL,
  `enabled` BIT(1) NOT NULL DEFAULT b'1',
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_doc_key` (`doc_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_rag_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL,
  `content` TEXT NOT NULL,
  `embedding_json` JSON DEFAULT NULL,
  `metadata_json` JSON DEFAULT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_rag_chunk` (`document_id`, `chunk_index`),
  KEY `idx_agent_rag_chunk_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(128) NOT NULL,
  `config_value` VARCHAR(1000) NOT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 八、Agent 运行记录索引。
SET @idx_agent_run_started_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agent_run'
    AND INDEX_NAME = 'idx_agent_run_started'
);
SET @idx_agent_run_started_sql := IF(
  @idx_agent_run_started_exists = 0,
  'ALTER TABLE `agent_run` ADD INDEX `idx_agent_run_started` (`started_at`, `id`)',
  'SELECT 1'
);
PREPARE stmt_idx_agent_run_started FROM @idx_agent_run_started_sql;
EXECUTE stmt_idx_agent_run_started;
DEALLOCATE PREPARE stmt_idx_agent_run_started;

-- 九、系统基础数据：默认角色、默认账号、菜单和角色菜单绑定。
INSERT INTO `app_role` (`role_code`, `role_name`, `permission_level`, `description`, `enabled`, `created_at`) VALUES
('SUPER_ADMIN', '超级管理员', 'ADMIN', '拥有全部菜单和系统管理权限', b'1', NOW(6)),
('WAREHOUSE_MANAGER', '仓库主管', 'MANAGER', '可管理仓储业务和基础资料', b'1', NOW(6)),
('WAREHOUSE_OPERATOR', '仓库操作员', 'OPERATOR', '可执行入库、出库、移库和转包操作', b'1', NOW(6)),
('VIEWER', '只读查看', 'VIEWER', '仅可查看看板、库存和记录', b'1', NOW(6))
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `permission_level` = VALUES(`permission_level`),
  `description` = VALUES(`description`),
  `enabled` = b'1';

INSERT INTO `app_user` (`username`, `display_name`, `password_hash`, `role_name`, `avatar_color`, `auth_token`) VALUES
('admin', '系统管理员', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'SUPER_ADMIN', '#0f766e', NULL),
('manager', '仓库主管', '866485796cfa8d7c0cf7111640205b83076433547577511d81f8030ae99ecea5', 'WAREHOUSE_MANAGER', '#2563eb', NULL),
('operator', '仓库操作员', 'ec6e1c25258002eb1c67d15c7f45da7945fa4c58778fd7d88faa5e53e3b4698d', 'WAREHOUSE_OPERATOR', '#7c3aed', NULL)
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `role_name` = VALUES(`role_name`),
  `avatar_color` = VALUES(`avatar_color`);

INSERT INTO `menu_item` (`parent_id`, `menu_key`, `menu_name`, `menu_type`, `path_key`, `page_key`, `icon_key`, `sort_order`, `visible`) VALUES
(NULL, 'home', '首页', 'LEAF', 'home', 'home', 'home', 10, b'1'),
(NULL, 'inventory-ops', '入/出/移库', 'PARENT', 'inventory-ops', NULL, 'workflow', 20, b'1'),
(NULL, 'inventory-board', '库存看板', 'LEAF', 'inventory-board', 'inventoryBoard', 'board', 30, b'1'),
(NULL, 'kanban-info', '看板信息', 'LEAF', 'kanban-info', 'kanbanInfo', 'kanban', 40, b'1'),
(NULL, 'io-records', '出入记录', 'LEAF', 'io-records', 'records', 'records', 50, b'1'),
(NULL, 'equipment-info', '器具信息', 'PARENT', 'equipment-info', NULL, 'equipment', 60, b'1'),
(NULL, 'part-info', '零件信息', 'PARENT', 'part-info', NULL, 'parts', 70, b'1'),
(NULL, 'partner', '供应商/客户', 'PARENT', 'partner', NULL, 'partner', 80, b'1'),
(NULL, 'warehouse-zone', '仓库/库区', 'LEAF', 'warehouse-zone', 'warehouseZone', 'warehouse', 90, b'1'),
(NULL, 'ai-agent', '智能助手', 'PARENT', 'ai-agent', NULL, 'monitor', 95, b'1'),
(NULL, 'system-tools', '系统工具', 'LEAF', 'system-tools', 'systemTools', 'tools', 100, b'1'),
(NULL, 'system-monitor', '系统监控', 'LEAF', 'system-monitor', 'systemMonitor', 'monitor', 110, b'1'),
(NULL, 'system-management', '系统管理', 'PARENT', 'system-management', NULL, 'system', 120, b'1')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = VALUES(`menu_type`),
  `path_key` = VALUES(`path_key`),
  `page_key` = VALUES(`page_key`),
  `icon_key` = VALUES(`icon_key`),
  `sort_order` = VALUES(`sort_order`),
  `visible` = VALUES(`visible`);

INSERT INTO `menu_item` (`parent_id`, `menu_key`, `menu_name`, `menu_type`, `path_key`, `page_key`, `icon_key`, `sort_order`, `visible`) VALUES
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'inventory-ops') p), 'inbound', '入库', 'LEAF', 'inbound', 'inbound', 'inbound', 21, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'inventory-ops') p), 'outbound', '出库', 'LEAF', 'outbound', 'outbound', 'outbound', 22, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'inventory-ops') p), 'mobile-scan', '移动扫码', 'LEAF', 'mobile-scan', 'mobileScan', 'scan', 23, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'inventory-ops') p), 'repack', '转包', 'LEAF', 'repack', 'repack', 'repack', 23, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'inventory-ops') p), 'repack-balance', '转包结余', 'LEAF', 'repack-balance', 'repackBalance', 'balance', 24, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'inventory-ops') p), 'transfer-freeze', '移库/封存', 'LEAF', 'transfer-freeze', 'transferFreeze', 'transfer', 25, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'equipment-info') p), 'equipment-normal', '普通器具', 'LEAF', 'equipment-normal', 'equipmentNormal', 'box', 61, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'equipment-info') p), 'equipment-repack', '转包器具', 'LEAF', 'equipment-repack', 'equipmentRepack', 'package', 62, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'part-info') p), 'part-management', '零件管理', 'LEAF', 'part-management', 'partManagement', 'parts', 71, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'part-info') p), 'category-management', '分类管理', 'LEAF', 'category-management', 'categoryManagement', 'category', 72, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'partner') p), 'supplier-management', '供应商', 'LEAF', 'supplier-management', 'supplierManagement', 'supplier', 81, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'partner') p), 'customer-management', '客户', 'LEAF', 'customer-management', 'customerManagement', 'customer', 82, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'ai-agent') p), 'agent-assistant', 'Agent助手', 'LEAF', 'agent-assistant', 'agentAssistant', 'monitor', 96, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'user-management', '用户管理', 'LEAF', 'user-management', 'userManagement', 'user', 121, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'role-management', '角色管理', 'LEAF', 'role-management', 'roleManagement', 'role', 122, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'menu-management', '菜单管理', 'LEAF', 'menu-management', 'menuManagement', 'menu', 123, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'department-management', '部门管理', 'LEAF', 'department-management', 'departmentManagement', 'department', 124, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'post-management', '岗位管理', 'LEAF', 'post-management', 'postManagement', 'post', 125, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'dictionary-management', '字典管理', 'LEAF', 'dictionary-management', 'dictionaryManagement', 'dictionary', 126, b'1'),
((SELECT `id` FROM (SELECT `id` FROM `menu_item` WHERE `menu_key` = 'system-management') p), 'parameter-settings', '参数设置', 'LEAF', 'parameter-settings', 'parameterSettings', 'settings', 127, b'1')
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = VALUES(`menu_type`),
  `path_key` = VALUES(`path_key`),
  `page_key` = VALUES(`page_key`),
  `icon_key` = VALUES(`icon_key`),
  `sort_order` = VALUES(`sort_order`),
  `visible` = VALUES(`visible`);

INSERT IGNORE INTO `role_menu` (`role_code`, `menu_id`)
SELECT 'SUPER_ADMIN', `id` FROM `menu_item`;

INSERT IGNORE INTO `role_menu` (`role_code`, `menu_id`)
SELECT 'VIEWER', `id` FROM `menu_item`
WHERE `menu_key` IN ('home', 'inventory-board', 'kanban-info', 'io-records');

INSERT IGNORE INTO `role_menu` (`role_code`, `menu_id`)
SELECT 'WAREHOUSE_OPERATOR', `id` FROM `menu_item`
WHERE `menu_key` IN (
  'home', 'inventory-ops', 'inbound', 'outbound', 'repack', 'repack-balance', 'transfer-freeze',
  'inventory-board', 'kanban-info', 'io-records'
);

INSERT IGNORE INTO `role_menu` (`role_code`, `menu_id`)
SELECT 'WAREHOUSE_MANAGER', `id` FROM `menu_item`
WHERE `menu_key` IN (
  'home', 'inventory-ops', 'inbound', 'outbound', 'repack', 'repack-balance', 'transfer-freeze',
  'inventory-board', 'kanban-info', 'io-records',
  'equipment-info', 'equipment-normal', 'equipment-repack',
  'part-info', 'part-management', 'category-management',
  'partner', 'supplier-management', 'customer-management',
  'warehouse-zone', 'ai-agent', 'agent-assistant'
);

INSERT IGNORE INTO `role_menu` (`role_code`, `menu_id`)
SELECT 'VIEWER', `id` FROM `menu_item`
WHERE `menu_key` IN ('ai-agent', 'agent-assistant');

INSERT IGNORE INTO `role_menu` (`role_code`, `menu_id`)
SELECT 'WAREHOUSE_OPERATOR', `id` FROM `menu_item`
WHERE `menu_key` IN ('ai-agent', 'agent-assistant');

-- 十、系统配置项：包含中文状态说明和默认库存预警阈值。
INSERT INTO `config_item` (`module_key`, `item_code`, `item_name`, `status`, `remark`, `created_at`) VALUES
('userManagement', 'admin', '系统管理员', 'ENABLED', '默认系统管理员账号', NOW(6)),
('roleManagement', 'SUPER_ADMIN', '超级管理员', 'ENABLED', '拥有全部菜单和系统管理权限', NOW(6)),
('departmentManagement', 'WMS', '仓储中心', 'ENABLED', '默认仓储组织', NOW(6)),
('postManagement', 'MANAGER', '仓库主管', 'ENABLED', '默认仓储管理岗位', NOW(6)),
('dictionaryManagement', 'KANBAN_STATUS', '看板状态', 'ENABLED', 'WAIT_SCAN 待扫码 / INBOUND 已入库 / ALLOCATED 已分配待出库 / OUTBOUND 已出库 / PARTIAL_INBOUND 部分入库 / PARTIAL_OUTBOUND 部分出库', NOW(6)),
('parameterSettings', 'defaultLocation', '默认库位', 'ENABLED', '', NOW(6)),
('systemTools', 'qrPrinter', '二维码打印', 'ENABLED', '用于看板二维码和条码打印', NOW(6)),
('categoryManagement', 'DEFAULT', '默认分类', 'ENABLED', '默认零件分类', NOW(6)),
('inventoryWarning', 'DEFAULT', '默认库存预警', 'ENABLED', '{"critical":10,"low":30,"attention":60}', NOW(6))
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`);

