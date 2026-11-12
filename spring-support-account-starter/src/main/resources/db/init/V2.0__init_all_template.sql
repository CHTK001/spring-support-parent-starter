-- =====================================================================
-- V2.0__init_all_template.sql - spring-support-template-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。
--
-- 合并方式:
--   1. 原来靠 ALTER 补的列，一律折进建表语句 —— 新库一次建全，
--      不再依赖「先建表再补列」的两段式；
--   2. 存量库补列的 ALTER 仍原样保留在文件末尾（information_schema 判存 + 动态 DDL），
--      否则老库缺列；新建库上这些语句会因列已存在而跳过。
--
-- 约束: MySQL 专属语法（COMMENT 内联注释 / ENGINE=InnoDB / SET @var + PREPARE 动态 DDL）。
--   MySQL 8.4 不支持 ADD COLUMN IF NOT EXISTS、MariaDB 11.8 同样拒绝，
--   故存量补列一律沿用 information_schema 判存的动态 DDL 写法。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 模板信息表（hiprint 模板描述以 JSON 存取，按语言/类型/状态/删除建索引）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS template_info (

    template_info_id VARCHAR(32) PRIMARY KEY COMMENT '模板主键 ID',
    template_info_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    template_info_subtitle VARCHAR(200) COMMENT '模板副标题',
    template_info_language VARCHAR(8) NOT NULL COMMENT '模板语言：html、vue、jsx、json',
    template_info_type VARCHAR(32) NOT NULL COMMENT '模板类型：document、sms、email、label、other',
    template_info_status VARCHAR(16) DEFAULT 'draft' COMMENT '模板状态：draft、online、offline',
    template_info_content TEXT COMMENT '模板 JSON 描述（设计器产物）',
    template_info_remark VARCHAR(500) COMMENT '备注',
    template_info_creator VARCHAR(64) COMMENT '创建者',
    template_info_modifier VARCHAR(64) COMMENT '最后修改者',
    template_info_deleted TINYINT DEFAULT 0 COMMENT '软删除：0 正常、1 删除',
    template_info_created_at DATETIME COMMENT '创建时间',
    template_info_updated_at DATETIME COMMENT '更新时间',

    KEY `idx_template_info_language` (`template_info_language`),
    KEY `idx_template_info_type` (`template_info_type`),
    KEY `idx_template_info_status` (`template_info_status`),
    KEY `idx_template_info_deleted` (`template_info_deleted`, `template_info_updated_at`),
    KEY `idx_template_info_creator` (`template_info_creator`)

) COMMENT '模板信息';

-- 模板名称唯一（防止重名模板破坏列表遍历时取错数据）
CREATE UNIQUE INDEX uk_template_info_name
    ON template_info (template_info_name);

-- =====================================================================
-- V2.2__init_template_printer.sql - 打印机配置表
-- 通道: INIT 同步建表
-- 说明: 打印机配置由内存缓存改为落库，应用重启后默认打印机等参数不丢。
--
-- 约束: 脚本会被重复执行，一律使用 CREATE TABLE IF NOT EXISTS，
--       索引随建表语句一起创建，避免二次执行报错。
-- =====================================================================

CREATE TABLE IF NOT EXISTS template_printer (

    template_printer_id VARCHAR(32) PRIMARY KEY COMMENT '打印机主键 ID',
    template_printer_name VARCHAR(100) NOT NULL COMMENT '打印机设备名',
    template_printer_address VARCHAR(255) COMMENT '连接地址（IP / 主机名）',
    template_printer_port INT COMMENT '连接端口',
    template_printer_token VARCHAR(255) COMMENT '连接 Token',
    template_printer_username VARCHAR(64) COMMENT '连接用户',
    template_printer_type VARCHAR(64) COMMENT '打印机型号',
    template_printer_remark VARCHAR(500) COMMENT '备注',
    template_printer_default TINYINT DEFAULT 0 COMMENT '是否默认：0 否、1 是',
    template_printer_created_at DATETIME COMMENT '创建时间',
    template_printer_updated_at DATETIME COMMENT '更新时间',

    UNIQUE KEY `uk_template_printer_name` (`template_printer_name`),
    KEY `idx_template_printer_default` (`template_printer_default`)

) COMMENT '打印机配置';

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

