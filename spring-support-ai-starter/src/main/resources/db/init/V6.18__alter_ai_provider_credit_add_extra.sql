-- =====================================================================
-- 供应商余额快照表补充「供应商专属结构化数据」列（V6.18）
--
-- 为什么需要：sub2api 这类订阅制供应商的「订阅明细」挂在逐 Key 读数的
-- extra.subscriptions 里（含 starts_at / expires_at，驱动前端实时倒计时环）。
-- 落库快照原先不存 extra：进程重启 / 5 分钟缓存过期后，面板回放快照时
-- 订阅明细会消失（extra 为 null）。本列按行存 extra 的 JSON 原文，
-- 快照回放时还原，订阅明细不再因缓存路径丢失。
--
-- 为什么单独成文件：V6.17 已被执行过的库不会再跑第二遍（FlywayLike 按脚本名 +
-- 版本记账，success 后跳过），补列必须放在新版本脚本里才会生效。
-- 建表侧（V6.17 CREATE TABLE）保持原样，本脚本只负责存量库补列。
--
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态
-- DDL；可反复执行，字段已存在时只走 SELECT 1 分支。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider_credit'
          AND COLUMN_NAME = 'sys_ai_provider_credit_extra'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_provider_credit` ADD COLUMN `sys_ai_provider_credit_extra` TEXT NULL COMMENT ''供应商专属结构化数据 JSON（sub2api 订阅明细等）'' AFTER `sys_ai_provider_credit_parts`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
