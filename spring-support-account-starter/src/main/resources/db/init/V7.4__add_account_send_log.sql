-- =====================================================================
-- V7.4__add_account_send_log.sql - 统一发送日志表
-- 通道: INIT 同步建表
-- 说明: MailboxService 统一发信门面的通道级完整日志（邮件 / 短信）。
--       发送成功与失败都落一条，异步写入，失败不影响发送主流程。
--       业务侧留痕（验证码 sys_sms_log / 消息推送 sys_message_log）仍在
--       各业务表，本表只记录「通道视角」的每一次真实发送。
--
-- 约束: MySQL 专属语法（COMMENT 内联注释 / ENGINE=InnoDB）。
--       脚本可重复执行：CREATE TABLE IF NOT EXISTS，索引随建表语句一起创建。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_account_send_log (

    sys_account_send_log_id VARCHAR(32) PRIMARY KEY COMMENT '日志主键 ID（UUID）',
    sys_account_send_log_time DATETIME COMMENT '发送时间',
    sys_account_send_log_kind VARCHAR(16) COMMENT '账号种类：EMAIL / SMS',
    sys_account_send_log_account_id VARCHAR(64) COMMENT '发信账号 ID',
    sys_account_send_log_account_name VARCHAR(128) COMMENT '发信账号名称',
    sys_account_send_log_provider VARCHAR(64) COMMENT '发送通道：email / alibaba-sms / tencent-sms / baidu-sms',
    sys_account_send_log_target VARCHAR(255) COMMENT '收件人（邮箱地址 / 手机号）',
    sys_account_send_log_cc VARCHAR(512) COMMENT '抄送（仅邮件，逗号分隔）',
    sys_account_send_log_subject VARCHAR(512) COMMENT '主题（邮件）',
    sys_account_send_log_content MEDIUMTEXT COMMENT '完整正文',
    sys_account_send_log_template_code VARCHAR(128) COMMENT '模板编码',
    sys_account_send_log_success TINYINT DEFAULT 0 COMMENT '是否成功：0 失败、1 成功',
    sys_account_send_log_message_id VARCHAR(255) COMMENT '服务商消息标识',
    sys_account_send_log_error VARCHAR(2000) COMMENT '失败原因',
    sys_account_send_log_cost_ms BIGINT COMMENT '耗时（毫秒）',
    sys_account_send_log_source VARCHAR(64) COMMENT '调用来源：mailbox / system:sms-code / system:message-push',
    sys_account_send_log_created_at DATETIME COMMENT '写入时间',

    KEY `idx_account_send_log_time` (`sys_account_send_log_time`),
    KEY `idx_account_send_log_kind` (`sys_account_send_log_kind`),
    KEY `idx_account_send_log_target` (`sys_account_send_log_target`),
    KEY `idx_account_send_log_success` (`sys_account_send_log_success`),
    KEY `idx_account_send_log_account` (`sys_account_send_log_account_id`),
    KEY `idx_account_send_log_source` (`sys_account_send_log_source`)

) COMMENT '账号发送日志（邮件 / 短信通道级）';
