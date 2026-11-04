-- =====================================================================
-- V6.1__alter_service_access_log_app_key.sql
-- 通道: ALTER 变更
-- 说明: 服务实例改用 appKey 鉴权后，访问日志必须能回答「这笔调用是谁花的」。
--
--   1) app_key  本次调用使用的 AppKey（服务实例与网关统一按 AppKey 鉴权）。
--               该列是「这笔调用算谁的」唯一依据，不能用 instance_id 之外的
--               隐含约定来区分归属。
--   2) 索引     台账按 Key 维度核对调用量（「这个 Key 到底调了多少次」），
--               单列索引 + 时间列排序，避免全表扫描。
--
-- 写法：information_schema 判存 + 动态 DDL。MySQL 8.4 与 MariaDB 11.8 都不支持
--   ADD COLUMN IF NOT EXISTS，本脚本可反复执行，重复运行只走 SELECT 1 分支。
-- =====================================================================

-- ---------- 1. 调用方 AppKey ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_access_log'
          AND COLUMN_NAME = 'app_key'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_access_log` ADD COLUMN `app_key` VARCHAR(128) DEFAULT NULL
             COMMENT ''本次调用使用的 AppKey（老实例令牌调用为空）'' AFTER `user_id`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2. 按 Key 查调用记录 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_service_access_log'
          AND INDEX_NAME = 'sys_ai_service_access_log_app_key'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_service_access_log` ADD KEY `sys_ai_service_access_log_app_key` (`app_key`, `create_time`)'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
