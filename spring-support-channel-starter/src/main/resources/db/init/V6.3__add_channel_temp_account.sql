-- =====================================================================
-- V6.3__add_channel_temp_account.sql
-- 通道: INIT / ALTER 建表
-- 说明: 渠道类型化改造 —— 引入「账号类型」维度并把 monitor 的临时账号收敛进渠道管理。
--
--   1) sys_ai_provider_connection 新增 account_type 列（FIXED 固定 / TEMPORARY 临时），
--      默认 FIXED，历史连接行无感（AI 固定账号）。
--   2) 新增 sys_ai_channel_temp_identity：临时身份（组合资源 = 一个短信渠道 + 一个邮件渠道），
--      引用渠道标识与领取到的身份载荷 JSON，支撑进程重启后恢复。
--   3) 新增 sys_ai_channel_temp_message：临时身份收到的短信 / 邮件增量，
--      按 (identity_id, fingerprint) 唯一索引去重。
--
-- 写法：information_schema 判存 + 动态 DDL（MySQL 8.4 / MariaDB 11.8 不支持
--   ADD COLUMN IF NOT EXISTS），本脚本可反复执行；新表用 CREATE TABLE IF NOT EXISTS。
-- =====================================================================

-- ---------- 1. 连接表补 account_type 列 ----------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider_connection'
          AND COLUMN_NAME = 'sys_ai_provider_connection_account_type'),
        'SELECT 1',
        'ALTER TABLE `sys_ai_provider_connection` ADD COLUMN `sys_ai_provider_connection_account_type` VARCHAR(16) NOT NULL DEFAULT ''FIXED''
             COMMENT ''账号类型（FIXED 固定 / TEMPORARY 临时）'' AFTER `sys_ai_provider_connection_role`'));
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2. 临时身份 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_channel_temp_identity` (
    `sys_ai_channel_temp_identity_id`             VARCHAR(64) NOT NULL COMMENT '身份标识（UUID）',
    `sys_ai_channel_temp_identity_sms_channel`    VARCHAR(64) DEFAULT NULL COMMENT '短信渠道标识（如 sms-freephonenum）',
    `sys_ai_channel_temp_identity_sms_identity`   TEXT        DEFAULT NULL COMMENT '短信身份载荷 JSON（含 phone / provider）',
    `sys_ai_channel_temp_identity_email_channel`  VARCHAR(64) DEFAULT NULL COMMENT '邮件渠道标识（如 email-mail-tm）',
    `sys_ai_channel_temp_identity_email_identity` TEXT        DEFAULT NULL COMMENT '邮件身份载荷 JSON（含 email / provider）',
    `sys_ai_channel_temp_identity_user_id`        VARCHAR(64) DEFAULT NULL COMMENT '归属用户（账号隔离）',
    `sys_ai_channel_temp_identity_created_at`     BIGINT      DEFAULT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`sys_ai_channel_temp_identity_id`),
    KEY `sys_ai_channel_temp_identity_user` (`sys_ai_channel_temp_identity_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 渠道临时身份';

-- ---------- 3. 临时身份消息增量 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_channel_temp_message` (
    `sys_ai_channel_temp_message_id`          VARCHAR(64)  NOT NULL COMMENT '消息标识（UUID）',
    `sys_ai_channel_temp_message_identity_id` VARCHAR(64)  NOT NULL COMMENT '所属身份标识',
    `sys_ai_channel_temp_message_kind`        VARCHAR(16)  NOT NULL COMMENT '消息种类（SMS / EMAIL）',
    `sys_ai_channel_temp_message_fingerprint` VARCHAR(128) NOT NULL COMMENT '去重指纹',
    `sys_ai_channel_temp_message_payload`     TEXT         DEFAULT NULL COMMENT '消息载荷 JSON',
    `sys_ai_channel_temp_message_created_at`  BIGINT       DEFAULT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`sys_ai_channel_temp_message_id`),
    UNIQUE KEY `uk_temp_identity_fingerprint` (`sys_ai_channel_temp_message_identity_id`, `sys_ai_channel_temp_message_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 渠道临时身份消息';
