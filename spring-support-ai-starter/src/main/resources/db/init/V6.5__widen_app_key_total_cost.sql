-- =====================================================================
-- V6.5__widen_app_key_total_cost.sql
-- 类型: ALTER 列
-- 说明: 累计费用列与余额 / 流水同精度（6 位小数）。
--
--   V5.8 建列时把 sys_ai_app_key_total_cost 定为 DECIMAL(12,4)，
--   而余额与流水的金额都是 DECIMAL(12,6)，单次调用费用又常常小于
--   0.0001 元（例如 0.000876）。按 4 位小数入库，每一笔都会被四舍五入，
--   「流水金额之和」与「累计费用」永远对不上账，调用量越大偏差越大。
--   这里与 V6.2 同样的写法（information_schema 判断 + 动态 DDL），
--   可安全重复执行，不依赖 checksum。
-- =====================================================================

SET @ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_app_key'
        AND COLUMN_NAME = 'sys_ai_app_key_total_cost'
        AND NUMERIC_SCALE >= 6) > 0,
    'SELECT 1',
    'ALTER TABLE `sys_ai_app_key` MODIFY COLUMN `sys_ai_app_key_total_cost` DECIMAL(12,6) DEFAULT 0.000000 COMMENT ''累计费用（元）'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
