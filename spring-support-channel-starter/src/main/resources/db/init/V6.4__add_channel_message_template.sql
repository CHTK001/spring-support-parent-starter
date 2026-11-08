-- =====================================================================
-- V6.4__add_channel_message_template.sql
-- 通道: INIT 建表 + 内置模板
-- 说明: 渠道管理支持短信 / 邮件「消息模板」的自定义与选择。
--
--   1) 新增 sys_ai_channel_message_template：按种类（SMS / EMAIL）维护多套模板，
--      同一种类仅一条默认模板；模板正文使用 {{变量}} 占位，
--      variables 列（JSON 数组）声明模板引用的变量名，供前端提示与缺失校验。
--   2) 内置四条模板（各两条：验证码优先 / 原文优先），固定主键 + INSERT IGNORE，
--      本脚本可反复执行；默认模板为「验证码优先」两条。
--   3) 渲染时机：临时账号轮询到新短信 / 新邮件时，后端用该种类默认模板渲染，
--      给消息补 display 字段；渠道管理页的短信 / 邮箱组件优先展示 display。
--
-- 写法：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，MySQL 8.4 / MariaDB 11.8 均可重复执行。
-- =====================================================================

-- ---------- 1. 消息模板 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_channel_message_template` (
    `sys_ai_channel_message_template_id`           VARCHAR(64)  NOT NULL COMMENT '模板标识（UUID / 内置固定值）',
    `sys_ai_channel_message_template_kind`         VARCHAR(16)  NOT NULL COMMENT '模板种类（SMS / EMAIL）',
    `sys_ai_channel_message_template_name`         VARCHAR(128) NOT NULL COMMENT '模板名称',
    `sys_ai_channel_message_template_code`         VARCHAR(64)  NOT NULL COMMENT '模板编码（同种类内唯一）',
    `sys_ai_channel_message_template_subject`      VARCHAR(256) DEFAULT NULL COMMENT '邮件主题模板（可含变量）',
    `sys_ai_channel_message_template_content`      TEXT         NOT NULL COMMENT '模板正文（可含 {{变量}}）',
    `sys_ai_channel_message_template_variables`    TEXT         DEFAULT NULL COMMENT '变量名清单 JSON 数组',
    `sys_ai_channel_message_template_default`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否该种类默认模板',
    `sys_ai_channel_message_template_enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_channel_message_template_sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序号（升序）',
    `sys_ai_channel_message_template_created_at`   BIGINT       DEFAULT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_channel_message_template_updated_at`   BIGINT       DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_channel_message_template_id`),
    UNIQUE KEY `uk_message_template_kind_code`
        (`sys_ai_channel_message_template_kind`, `sys_ai_channel_message_template_code`),
    KEY `idx_message_template_kind_default`
        (`sys_ai_channel_message_template_kind`, `sys_ai_channel_message_template_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 渠道短信邮件消息模板';

-- ---------- 2. 内置模板 ----------
INSERT IGNORE INTO `sys_ai_channel_message_template`
(`sys_ai_channel_message_template_id`, `sys_ai_channel_message_template_kind`,
 `sys_ai_channel_message_template_name`, `sys_ai_channel_message_template_code`,
 `sys_ai_channel_message_template_subject`, `sys_ai_channel_message_template_content`,
 `sys_ai_channel_message_template_variables`, `sys_ai_channel_message_template_default`,
 `sys_ai_channel_message_template_enabled`, `sys_ai_channel_message_template_sort_order`,
 `sys_ai_channel_message_template_created_at`, `sys_ai_channel_message_template_updated_at`)
VALUES
('tpl-sms-code', 'SMS', '验证码优先', 'sms-code', NULL,
 '验证码：{{code}}\n来自 {{from}} · {{time}}',
 '["code","from","time"]', 1, 1, 10, UNIX_TIMESTAMP(NOW(3)) * 1000, UNIX_TIMESTAMP(NOW(3)) * 1000),
('tpl-sms-raw', 'SMS', '原文展示', 'sms-raw', NULL,
 '{{content}}\n—— {{from}} · {{time}}',
 '["content","from","time"]', 0, 1, 20, UNIX_TIMESTAMP(NOW(3)) * 1000, UNIX_TIMESTAMP(NOW(3)) * 1000),
('tpl-email-code', 'EMAIL', '验证码优先', 'email-code', '{{subject}}',
 '验证码：{{code}}\n来自 {{from}}\n\n{{body}}',
 '["code","from","body","subject"]', 1, 1, 10, UNIX_TIMESTAMP(NOW(3)) * 1000, UNIX_TIMESTAMP(NOW(3)) * 1000),
('tpl-email-raw', 'EMAIL', '原文展示', 'email-raw', '{{subject}}',
 '{{body}}',
 '["body","subject"]', 0, 1, 20, UNIX_TIMESTAMP(NOW(3)) * 1000, UNIX_TIMESTAMP(NOW(3)) * 1000);
