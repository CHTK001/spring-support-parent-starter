-- =====================================================================
-- V1.0__init_all_message.sql - spring-support-message-starter full DDL
-- merged from loose scripts:
--   V1.0__init_message.sql
-- =====================================================================


-- ---------------------------------------------------------------------
-- source: V1.0__init_message.sql
-- ---------------------------------------------------------------------
/*
 * 消息推送模块 - 数据库初始化脚本
 *
 * 包含表:
 *   - sys_message_provider: 消息提供商表
 *   - sys_message_template: 消息模板表
 *   - sys_message_log: 消息推送日志表
 *
 * 用于管理消息服务商配置与消息模板，提供统一的消息推送能力。
 *
 * @author CH
 * @version 1.0
 * @since 2026-08-03
 */

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 消息提供商表 (sys_message_provider)
-- 用途: 管理短信、邮件、微信、钉钉等消息服务商配置
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_message_provider` (

    `sys_message_provider_id` int NOT NULL AUTO_INCREMENT COMMENT '提供商ID - 主键自增',
    `sys_message_provider_code` varchar(255) NULL DEFAULT NULL COMMENT '提供商编码 - 唯一标识，如 aliyun/tencent/smtp',
    `sys_message_provider_name` varchar(255) NULL DEFAULT NULL COMMENT '提供商名称 - 显示名称',
    `sys_message_provider_type` varchar(64) NULL DEFAULT NULL COMMENT '提供商类型 - EMAIL/SMS/WECHAT/DINGDING/FEISHU等',
    `sys_message_provider_vender` varchar(128) NULL DEFAULT NULL COMMENT '厂家编码 - 适配的扩展厂家标识',
    `sys_message_provider_app_id` varchar(255) NULL DEFAULT NULL COMMENT '应用ID - 用于API认证',
    `sys_message_provider_app_secret` varchar(255) NULL DEFAULT NULL COMMENT '应用密钥 - 用于API认证',
    `sys_message_provider_sign` varchar(255) NULL DEFAULT NULL COMMENT '签名 - 短信/接口签名',
    `sys_message_provider_endpoint` varchar(255) NULL DEFAULT NULL COMMENT '端点地址 - API服务端点URL',
    `sys_message_provider_api_address` varchar(255) NULL DEFAULT NULL COMMENT 'API地址 - 接口服务基础地址',
    `sys_message_provider_smtp_host` varchar(255) NULL DEFAULT NULL COMMENT 'SMTP主机 - 邮件服务器主机地址',
    `sys_message_provider_smtp_port` int NULL DEFAULT NULL COMMENT 'SMTP端口 - 邮件服务器端口号',
    `sys_message_provider_smtp_password` varchar(255) NULL DEFAULT NULL COMMENT 'SMTP密码 - 邮件服务器授权码',
    `sys_message_provider_smtp_from` varchar(255) NULL DEFAULT NULL COMMENT 'SMTP发件人 - 发件人邮箱地址',
    `sys_message_provider_webhook_url` varchar(512) NULL DEFAULT NULL COMMENT 'Webhook地址 - 用于钉钉、飞书等机器人',
    `sys_message_provider_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `sys_message_provider_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
    `sys_message_provider_interval` int NULL DEFAULT 1 COMMENT '重复消息间隔 - 分钟，间隔内同一接收人相同模板/内容不重复发送，默认1分钟，0表示不限制',
    `sys_message_provider_sort` int NULL DEFAULT 0 COMMENT '排序号 - 列表显示顺序',
    `sys_message_provider_remark` varchar(255) NULL DEFAULT NULL COMMENT '描述 - 提供商用途说明',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_message_provider_id`),
    UNIQUE KEY `uk_provider_code` (`sys_message_provider_code`),
    KEY `idx_provider_type` (`sys_message_provider_type`),
    KEY `idx_provider_status` (`sys_message_provider_status`)
) ENGINE=InnoDB;

-- =====================================================
-- 2. 消息模板表 (sys_message_template)
-- 用途: 管理各渠道消息模板，支持变量替换与内容/回调配置
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_message_template` (

    `sys_message_template_id` int NOT NULL AUTO_INCREMENT COMMENT '模板ID - 主键自增',
    `sys_message_provider_id` int NULL DEFAULT NULL COMMENT '提供商ID - 关联消息提供商',
    `sys_message_template_code` varchar(255) NULL DEFAULT NULL COMMENT '模板编码 - 唯一标识',
    `sys_message_template_name` varchar(255) NULL DEFAULT NULL COMMENT '模板名称',
    `sys_message_template_title` varchar(255) NULL DEFAULT NULL COMMENT '模板标题(支持变量)',
    `sys_message_template_type` varchar(64) NULL DEFAULT NULL COMMENT '模板类型 - EMAIL/SMS/WECHAT/DINGDING/FEISHU',
    `sys_message_template_category` int NULL DEFAULT NULL COMMENT '模板分类',
    `sys_message_template_content` text NULL COMMENT '模板内容 - 模板内容，变量用${var}表示',
    `sys_message_template_callback_url` varchar(512) NULL DEFAULT NULL COMMENT '回调地址 - 邮件状态回调URL',
    `sys_message_template_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `sys_message_template_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
    `sys_message_template_sort` int NULL DEFAULT 0 COMMENT '模板优先级',
    `sys_message_template_default` int NULL DEFAULT 0 COMMENT '是否默认 - 0:否, 1:是',
    `sys_message_template_remark` varchar(512) NULL DEFAULT NULL COMMENT '备注说明',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_message_template_id`),
    KEY `idx_template_provider` (`sys_message_provider_id`),
    KEY `idx_template_code` (`sys_message_template_code`),
    KEY `idx_template_status` (`sys_message_template_status`)
) ENGINE=InnoDB;

-- =====================================================
-- 3. 消息推送日志表 (sys_message_log)
-- 用途: 记录消息推送历史，包含发送内容、结果、耗时等
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_message_log` (

    `sys_message_log_id` int NOT NULL AUTO_INCREMENT COMMENT '日志ID - 主键自增',
    `sys_message_log_target` varchar(255) NULL DEFAULT NULL COMMENT '接收人 - 手机号/邮箱/用户ID等',
    `sys_message_template_id` int NULL DEFAULT NULL COMMENT '模板ID - 使用的模板',
    `sys_message_provider_id` int NULL DEFAULT NULL COMMENT '提供商ID - 使用的消息提供商',
    `sys_message_log_channel` varchar(64) NULL DEFAULT NULL COMMENT '渠道标识 - 实际发送渠道',
    `sys_message_log_content` longtext NULL COMMENT '发送内容 - 最终发送的消息内容',
    `sys_message_log_param` longtext NULL COMMENT '模板参数 - JSON格式的模板变量',
    `sys_message_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '耗时 - 发送耗时(毫秒)',
    `sys_message_code` varchar(64) NULL DEFAULT NULL COMMENT '返回码 - SUCCESS/FAILURE',
    `sys_message_message` text NULL COMMENT '返回消息 - 错误信息',
    `sys_message_log_subject` varchar(512) NULL DEFAULT NULL COMMENT '消息主题 - 邮件标题等',
    `sys_message_log_message_id` varchar(255) NULL DEFAULT NULL COMMENT '消息ID - 服务商返回的消息标识',
    `sys_message_batch_id` varchar(64) NULL DEFAULT NULL COMMENT '批次号 - 同一次发送请求的接收人共享',
    `sys_message_track_token` varchar(64) NULL DEFAULT NULL COMMENT '追踪标识 - 邮件阅读追踪token，用于回写已读状态',
    `sys_message_read_status` int NULL DEFAULT 0 COMMENT '已读状态 - 0:未读, 1:已读',
    `sys_message_read_time` datetime NULL DEFAULT NULL COMMENT '已读时间 - 收件人打开邮件触发追踪的时间',
    `sys_message_log_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_message_log_id`),
    KEY `idx_log_target` (`sys_message_log_target`),
    KEY `idx_log_template` (`sys_message_template_id`),
    KEY `idx_log_provider` (`sys_message_provider_id`),
    KEY `idx_log_create_time` (`create_time`),
    KEY `idx_log_track_token` (`sys_message_track_token`),
    KEY `idx_log_batch` (`sys_message_batch_id`)
) ENGINE=InnoDB;
