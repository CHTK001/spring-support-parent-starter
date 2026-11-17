-- =====================================================================
-- 用量明细增加「状态」列：success(成功) / failed(超时截断)（V6.16）
--
-- 为什么需要：三方用量（opencode/cline/atomcode 等）的耗时存在脏数据 ——
-- 解析时 started_at 字段缺失会让耗时被算成 epoch 毫秒（约 1.79e12 ms），
-- 直接入库会把统计页的耗时与吞吐全部污染。修复口径：耗时超过「会话超时」
-- （默认 5 分钟）的记录一律截断为该上限值，并把该行标记为失败，数据保留。
-- 状态列让脏行与正常行在明细里可区分。
--
-- 为什么单独成文件：V6.11 已被执行过的库不会再跑第二遍（FlywayLike 按脚本名 +
-- 版本记账，success 后跳过），补列必须放在新版本脚本里才会生效。
-- 建表侧（V6.11 CREATE TABLE）保持原样，本脚本只负责存量库补列。
--
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态
-- DDL；可反复执行，字段已存在时只走 SELECT 1 分支。
-- 默认值 success：存量行补列后自动落为成功，超限行由数据修复脚本改标 failed。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_usage'
          AND COLUMN_NAME = 'sys_ai_usage_status'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_status` VARCHAR(16) NULL DEFAULT ''success'' COMMENT '' 用量状态: success(成功)/failed(超时截断)'''));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
