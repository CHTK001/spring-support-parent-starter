-- =====================================================================
-- 分组默认角色（V6.12）
--
-- sys_ai_group 增加 sys_ai_group_default_role_id：分组级默认角色，指向
-- sys_ai_role.sys_ai_role_id。组内 Key 调模型且调用方没带 system 提示词时，
-- 用该角色在当前形态下的提示词当系统提示词；为空则沿用全局默认。
--
-- 为什么单独成文件：V6.11 已被执行过的库不会再跑第二遍（FlywayLike 按
-- 脚本名 + 版本记账，success 后跳过），补列必须放在新版本脚本里才会生效。
-- 建表侧（V6.11 CREATE TABLE）已含该列；本脚本只负责存量库补列。
--
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态 DDL。
-- MySQL 8.4 / MariaDB 11.8 均不支持 ADD COLUMN IF NOT EXISTS（报 1064），
-- 故补列一律用动态 DDL；可反复执行，重复运行只走 SELECT 1 分支。
-- =====================================================================

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_group'
          AND COLUMN_NAME = 'sys_ai_group_default_role_id'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_group` ADD COLUMN `sys_ai_group_default_role_id` BIGINT NULL COMMENT ''默认角色 ID（sys_ai_role.sys_ai_role_id），空 = 不设分组级角色'' AFTER `sys_ai_group_app_key_ids`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
