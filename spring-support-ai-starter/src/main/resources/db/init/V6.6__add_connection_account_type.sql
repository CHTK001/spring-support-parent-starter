-- =====================================================================
-- V6.6__add_connection_account_type.sql
-- 通道: ALTER 变更
-- 说明: 账号类型维度落到 AI 自己的连接表上（FIXED 固定 / TEMPORARY 临时）。
--   该列被 SysAiProviderConnection.accountType 读取，因此 DDL 必须由 AI 模块
--   自己维护，不能依赖任何账号/渠道模块。
--
-- 写法：information_schema 判存 + 动态 DDL（MySQL 8.4 / MariaDB 11.8 不支持
--   ADD COLUMN IF NOT EXISTS），本脚本可反复执行。
-- =====================================================================
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider_connection'
          AND COLUMN_NAME = 'sys_ai_provider_connection_account_type'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_provider_connection` ADD COLUMN `sys_ai_provider_connection_account_type` VARCHAR(16) NOT NULL DEFAULT ''FIXED''
             COMMENT ''账号类型（FIXED 固定 / TEMPORARY 临时）'' AFTER `sys_ai_provider_connection_role`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
