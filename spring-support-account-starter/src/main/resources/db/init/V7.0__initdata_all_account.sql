-- =====================================================================
-- V7.0__initdata_all_account.sql - spring-support-account-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。原 V6.4__add_channel_message_template.sql
--   的内置四条模板 INSERT 与原 V2.4__add_template_source_code.sql 的存量回填 UPDATE
--   已在本次收敛中并入本文件，原增量脚本已删除。
-- 约束: 全部语句必须幂等（INSERT IGNORE 按主键/唯一索引去重；UPDATE 条件流转），
--   可反复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- [1] AI 渠道短信邮件消息模板 · 内置四条（原 V6.4__add_channel_message_template.sql）
-- 固定主键 + INSERT IGNORE，可反复执行；默认模板为「验证码优先」两条。
-- 渲染时机：临时账号轮询到新短信 / 新邮件时，后端用该种类默认模板渲染，
-- 给消息补 display 字段；渠道管理页的短信 / 邮箱组件优先展示 display。
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- [2] template_info 存量回填：三方模板 ID 搬至独立字段（原 V2.4__add_template_source_code.sql）
-- 此前「消息」模板的三方模板 ID 写在 subtitle 上，搬回新字段；
-- 只处理 source='message'（组卷 paper 的 subtitle 是样式名，不是三方模板 ID，
-- 搬过来会变成错的编码）。
-- 回填自身幂等：搬过一次后 subtitle 已被置空，再跑不会命中。
-- ---------------------------------------------------------------------
UPDATE template_info
SET template_info_source_code = template_info_subtitle,
    template_info_subtitle = NULL
WHERE template_info_source = 'message'
  AND template_info_subtitle IS NOT NULL
  AND template_info_subtitle <> '';