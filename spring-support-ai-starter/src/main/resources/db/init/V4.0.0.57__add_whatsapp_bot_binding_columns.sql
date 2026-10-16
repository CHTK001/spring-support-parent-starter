-- =====================================================================================
-- 存量库补列：bot 连接器（sys_ai_bot_binding）角色与 WhatsApp Cloud API 字段
-- -------------------------------------------------------------------------------------
-- 【背景】
--   V4.0.0.55 只声明了 role_id、V4.0.0.56 只声明了 app_secret/verify_token，且都以
--   「裸 ALTER」或有条件的方式书写。实测 2026-10-16：目标库为 MySQL 8.4.9，既不支持
--   `ADD COLUMN IF NOT EXISTS`（报 1064），这些版本化脚本在本机部署（plugin.datasource
--   .script.enable=false，FlywayLike 关闭）也**不会自动执行**；而
--   `V1.0__init_all_ai.sql` 的 `CREATE TABLE IF NOT EXISTS` 对已存在的表是空操作，
--   于是存量库缺列，`AiBotChatService.init` 按实体全列 SELECT，启动期抛
--     Unknown column 'sys_ai_bot_binding_app_secret' in 'field list'
--   并中断 refresh（ApplicationListenerMethodAdapter → SpringApplication.run failed）。
--
-- 【写法】
--   沿用 V4.0.0.46 / 47 / 53 的既有模式：查 information_schema.COLUMNS 判存，
--   再用 PREPARE/EXECUTE 执行动态 DDL。可被反复执行，重复运行只走 `SELECT 1` 分支。
--   本文件为 MySQL 专用存量库补列脚本（与 46/47/53 同类）。
--
-- 【登记】
--   本机 monitor 的 `spring.sql.init.schema-locations` 已显式登记本文件；
--   生产若启用 FlywayLike（plugin.datasource.script.enable=true）则按版本号自动执行，
--   两处都可，且重复执行无副作用。
--
-- 【列顺序】
--   AFTER 指向已存在的列，保证与 V1.0__init_all_ai.sql 的 CREATE TABLE 列序一致。
-- =====================================================================================

-- ---------- 1) 平台应用密钥（WhatsApp App Secret，用于 X-Hub-Signature-256 验签） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_bot_binding'
          AND COLUMN_NAME = 'sys_ai_bot_binding_app_secret'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_bot_binding` ADD COLUMN `sys_ai_bot_binding_app_secret` VARCHAR(512) NULL COMMENT ''平台应用密钥（WhatsApp App Secret，用于验签）'' AFTER `sys_ai_bot_binding_bot_token`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2) Webhook 校验令牌（WhatsApp Verify Token，用于 GET 握手） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_bot_binding'
          AND COLUMN_NAME = 'sys_ai_bot_binding_verify_token'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_bot_binding` ADD COLUMN `sys_ai_bot_binding_verify_token` VARCHAR(255) NULL COMMENT ''Webhook 校验令牌（WhatsApp Verify Token）'' AFTER `sys_ai_bot_binding_app_secret`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 3) 绑定的 AI 角色ID（提示词由角色提供，连接器自身 systemPrompt 仅兜底） ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_bot_binding'
          AND COLUMN_NAME = 'sys_ai_bot_binding_role_id'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_bot_binding` ADD COLUMN `sys_ai_bot_binding_role_id` INT NULL COMMENT ''绑定的 AI 角色ID（sys_ai_role.sys_ai_role_id）'' AFTER `sys_ai_bot_binding_model`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
