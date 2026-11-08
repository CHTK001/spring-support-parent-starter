-- =====================================================================
-- V2.3__init_template_provider_log.sql - 提供商与调用日志表
-- 通道: INIT 同步建表
-- 说明:
--   1. template_provider 承载 pages/template 左栏「提供商轨」的数据：
--      凭据 / 端点 / 支持的模板类型 / 是否允许自定义设计 / 绑定的来源同步器。
--      模板通过 template_info.template_info_provider 归到提供商下。
--   2. template_log 承载模板的预览 / 设计 / 打印 / 同步轨迹，只做审计。
--
-- 约束: 脚本会被重复执行，一律使用 CREATE TABLE IF NOT EXISTS，
--       索引随建表语句一起创建，避免二次执行报错。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 模板提供商（一条记录 = 一条模板来源通道）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS template_provider (

    template_provider_id VARCHAR(64) PRIMARY KEY COMMENT '提供商主键 ID',
    template_provider_name VARCHAR(100) NOT NULL COMMENT '提供商名称（显示名，如「阿里云短信」）',
    template_provider_vendor VARCHAR(32) COMMENT '接入方：aliyun、tencent、self',
    template_provider_ak VARCHAR(255) COMMENT 'Access Key',
    template_provider_sk VARCHAR(255) COMMENT 'Secret Key',
    template_provider_endpoint VARCHAR(255) COMMENT '服务端点（域名或自建服务地址）',
    template_provider_types VARCHAR(200) COMMENT '支持的模板类型，逗号分隔：document、sms、email、label、other',
    template_provider_custom_design TINYINT DEFAULT 0 COMMENT '是否支持自定义模板设计：0 否、1 是',
    template_provider_status VARCHAR(16) DEFAULT 'online' COMMENT '状态：online 已启用、offline 已停用',
    template_provider_source VARCHAR(32) COMMENT '绑定的来源同步器：message、paper，空表示本地提供商',
    template_provider_last_sync_at DATETIME COMMENT '最近一次同步时间',
    template_provider_owner VARCHAR(64) COMMENT '负责人',
    template_provider_remark VARCHAR(500) COMMENT '说明',
    template_provider_created_at DATETIME COMMENT '创建时间',
    template_provider_updated_at DATETIME COMMENT '更新时间',

    UNIQUE KEY `uk_template_provider_name` (`template_provider_name`),
    KEY `idx_template_provider_status` (`template_provider_status`),
    KEY `idx_template_provider_source` (`template_provider_source`)

) COMMENT '模板提供商';

-- ---------------------------------------------------------------------
-- 模板调用日志（预览 / 设计 / 打印 / 同步）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS template_log (

    template_log_id VARCHAR(32) PRIMARY KEY COMMENT '日志主键 ID',
    template_log_template_id VARCHAR(64) COMMENT '模板 ID（同步类操作记录提供商 ID）',
    template_log_template_name VARCHAR(200) COMMENT '模板名称（同步类操作记录提供商名称）',
    template_log_action VARCHAR(16) COMMENT '操作：preview、design、print、sync',
    template_log_parameters TEXT COMMENT '调用参数（JSON 串）',
    template_log_ip VARCHAR(64) COMMENT '调用方 IP',
    template_log_operator VARCHAR(64) COMMENT '操作人',
    template_log_created_at DATETIME COMMENT '创建时间',

    KEY `idx_template_log_created_at` (`template_log_created_at`),
    KEY `idx_template_log_template` (`template_log_template_id`, `template_log_created_at`),
    KEY `idx_template_log_action` (`template_log_action`)

) COMMENT '模板调用日志';
