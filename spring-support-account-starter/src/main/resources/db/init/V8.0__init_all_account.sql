-- =====================================================================
-- V8.0__init_all_account.sql - spring-support-account-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。
--
-- 版本递进: V7.0 -> V8.0。FlywayLike 按「脚本名 + 版本」记账，命中成功记录
--   即跳过（checksum 变化只做校验、不会触发重跑），故本次脚本收敛必须
--   递进版本号，否则存量库不会重跑、并入的建表不会生效。
--
-- 收敛口径（原 V7.2 / V7.3 / V7.4 / V7.5 的处置）:
--   1. V7.2__migrate_channel_to_account.sql —— 渠道 / 渠道模型 / 渠道连接 /
--      渠道健康四表的建表部分原样并入本文件；其「INSERT...SELECT 旧
--      sys_ai_provider* / sys_ai_channel_health 表」的存量数据复制不并入，
--      那是存量库的一次性搬迁（新库没有旧表），随 V7.2 一并退役。
--   2. V7.3__drop_legacy_channel_tables.sql —— 旧渠道表（sys_ai_provider /
--      sys_ai_provider_model / sys_ai_provider_connection /
--      sys_ai_channel_health）的清表语句不并入：旧表不再自动删除，
--      需要清理的存量库请手工执行。
--   3. V7.4__add_account_send_log.sql —— 统一发送日志表
--      （sys_account_send_log）建表部分原样并入本文件；该脚本无 DML。
--   4. V7.5__add_channel_health_log.sql —— 渠道健康探针历史表
--      （sys_account_channel_health_log）建表部分原样并入本文件。
--
-- 动态 DDL 移除: 原 V7.0 尾部「先判存、再动态执行补列」的存量兼容段
--   全部删除 —— 其补列 / 补索引 / 唯一约束迁移（V2.1 / V2.4 /
--   V6.7 / V6.8 / V6.10）早已内联进对应 CREATE TABLE 的列定义，
--   新库一次建全。本文件只保留通用 SQL
--   （CREATE TABLE IF NOT EXISTS + 内联列 / 索引 / 表注释），
--   不再含任何动态 DDL 语句。
--
-- 幂等: 建表 IF NOT EXISTS，可反复执行。
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

    -- 以下三列来源扩展（原 V2.1__add_template_source.sql，已并入本文件）：
    -- 消息模板/组卷模板等非绘制模板也能进入统一模板库（pages/template）展示
    template_info_source VARCHAR(32) NOT NULL DEFAULT 'hiprint' COMMENT '来源：hiprint(绘制)/message(消息)/paper(组卷)',
    template_info_source_id VARCHAR(64) COMMENT '来源行 ID（如短信模板的 sys_message_template.id，与 template_info_id 对应）',
    template_info_provider VARCHAR(64) COMMENT '来源提供方（消息提供商 ID / 组卷归属用户 ID，与 template_info_id 对应）',

    -- 三方模板 ID（原 V2.4__add_template_source_code.sql，已并入本文件）：
    -- 固定模板（source=message/paper）内容归三方，按 ID 回查三方模板信息
    template_info_source_code VARCHAR(128) COMMENT '三方模板 ID（来源侧模板编码，如短信模板 sys_message_template_code；hiprint 自建模板为空）',

    KEY `idx_template_info_language` (`template_info_language`),
    KEY `idx_template_info_type` (`template_info_type`),
    KEY `idx_template_info_status` (`template_info_status`),
    KEY `idx_template_info_deleted` (`template_info_deleted`, `template_info_updated_at`),
    KEY `idx_template_info_creator` (`template_info_creator`),
    KEY `idx_template_info_source` (`template_info_source`),
    KEY `idx_template_info_source_id` (`template_info_source_id`),
    KEY `idx_template_info_provider` (`template_info_provider`),
    KEY `idx_template_info_source_code` (`template_info_source_code`),

    -- 模板名称 + 来源唯一（原唯一名称约束改为（名称 + 来源）唯一，避免不同来源模板同名）
    UNIQUE KEY `uk_template_info_name_source` (`template_info_name`, `template_info_source`)

) COMMENT '模板信息';

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

-- =====================================================================
-- [二] 增量并入的表结构（原 V6.4 ~ V6.10 建表部分，已并入本文件；
--       数据类语句移入 V8.0__initdata_all_account.sql）
-- =====================================================================

-- ---------------------------------------------------------------------
-- AI 渠道短信邮件消息模板（原 V6.4__add_channel_message_template.sql 建表）
-- 内置四条模板种子已移入 V8.0__initdata_all_account.sql。
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- 统一账号（原 V6.5__add_account.sql）
-- 账号管理的三张核心表，属于账号模块自己的数据，与 AI 的 sys_ai_* 表无关。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account` (
    `sys_account_id`             VARCHAR(64)  NOT NULL COMMENT '账号标识（UUID）',
    `sys_account_code`           VARCHAR(128) NOT NULL COMMENT '账号编码（业务唯一键，设备 / 模板按它引用）',
    `sys_account_name`           VARCHAR(128) NOT NULL COMMENT '账号名称（展示用）',
    `sys_account_kind`           VARCHAR(32)  NOT NULL COMMENT '账号种类（API_KEY / EMAIL / SMS）',
    `sys_account_vendor`         VARCHAR(64)  DEFAULT NULL COMMENT '厂商 / 服务商（如 aliyun / smtp / huawei）',
    `sys_account_channel_key`    VARCHAR(128) DEFAULT NULL COMMENT '所属渠道标识（可空）',
    `sys_account_username`       VARCHAR(255) DEFAULT NULL COMMENT '登录名 / 发件邮箱 / 手机号',
    `sys_account_secret`         TEXT         DEFAULT NULL COMMENT '敏感凭据（enc: 前缀密文）',
    `sys_account_endpoint`       VARCHAR(512) DEFAULT NULL COMMENT '服务地址（SMTP/POP 主机、API baseUrl）',
    `sys_account_config`         TEXT         DEFAULT NULL COMMENT '类型专属配置 JSON（端口/SSL/默认发件人等）',
    `sys_account_owner`          VARCHAR(64)  DEFAULT NULL COMMENT '归属用户（空表示部署级共享）',
    `sys_account_status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1 启用 / 0 停用）',
    `sys_account_default`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否同种类默认账号',
    `sys_account_verify_status`  VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '校验状态（UNKNOWN/VALID/INVALID）',
    `sys_account_verify_at`      BIGINT       DEFAULT NULL COMMENT '最近校验时间（毫秒）',
    `sys_account_verify_message` VARCHAR(512) DEFAULT NULL COMMENT '最近校验结果说明',
    `sys_account_deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 正常 / 1 已删除）',
    `sys_account_created_at`     BIGINT       DEFAULT NULL COMMENT '创建时间（毫秒）',
    `sys_account_updated_at`     BIGINT       DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_id`),
    UNIQUE KEY `uk_account_code` (`sys_account_code`),
    KEY `idx_account_kind` (`sys_account_kind`),
    KEY `idx_account_owner` (`sys_account_owner`),
    KEY `idx_account_status` (`sys_account_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一账号';

-- ---------------------------------------------------------------------
-- 外部访问密钥（原 V6.5__add_account.sql）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_app_key` (
    `sys_account_app_key_id`          VARCHAR(64)  NOT NULL COMMENT '密钥标识（UUID）',
    `sys_account_app_key_name`        VARCHAR(128) NOT NULL COMMENT '密钥名称',
    `sys_account_app_key_app_key`     VARCHAR(128) NOT NULL COMMENT '访问 Key（标识，可明文查询）',
    `sys_account_app_key_secret`      TEXT         DEFAULT NULL COMMENT '访问 Secret（enc: 前缀密文）',
    `sys_account_app_key_owner`       VARCHAR(64)  DEFAULT NULL COMMENT '归属用户（空表示部署级共享）',
    `sys_account_app_key_status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1 启用 / 0 停用）',
    `sys_account_app_key_expire_at`   BIGINT       DEFAULT NULL COMMENT '过期时间（毫秒，空表示永不过期）',
    `sys_account_app_key_ip_whitelist` VARCHAR(512) DEFAULT NULL COMMENT 'IP 白名单（逗号分隔）',
    `sys_account_app_key_remark`      VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `sys_account_app_key_last_used_at` BIGINT      DEFAULT NULL COMMENT '最近使用时间（毫秒）',
    `sys_account_app_key_deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 正常 / 1 已删除）',
    `sys_account_app_key_created_at`  BIGINT       DEFAULT NULL COMMENT '创建时间（毫秒）',
    `sys_account_app_key_updated_at`  BIGINT       DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_app_key_id`),
    UNIQUE KEY `uk_app_key` (`sys_account_app_key_app_key`),
    KEY `idx_app_key_owner` (`sys_account_app_key_owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号模块外部访问密钥';

-- ---------------------------------------------------------------------
-- Cookie 池（原 V6.5__add_account.sql）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_cookie` (
    `sys_account_cookie_id`         VARCHAR(64)  NOT NULL COMMENT 'Cookie 标识（UUID）',
    `sys_account_cookie_name`       VARCHAR(128) NOT NULL COMMENT '名称（展示用）',
    `sys_account_cookie_domain`     VARCHAR(255) DEFAULT NULL COMMENT '作用域域名',
    `sys_account_cookie_path`       VARCHAR(255) DEFAULT '/' COMMENT '作用路径',
    `sys_account_cookie_value`      TEXT         DEFAULT NULL COMMENT 'Cookie 内容（enc: 前缀密文）',
    `sys_account_cookie_owner`      VARCHAR(64)  DEFAULT NULL COMMENT '归属用户（空表示部署级共享）',
    `sys_account_cookie_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1 启用 / 0 停用）',
    `sys_account_cookie_expire_at`  BIGINT       DEFAULT NULL COMMENT '过期时间（毫秒，空表示会话级）',
    `sys_account_cookie_source`     VARCHAR(64)  DEFAULT NULL COMMENT '来源（如 trae / duckduckgo / manual）',
    `sys_account_cookie_remark`     VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `sys_account_cookie_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 正常 / 1 已删除）',
    `sys_account_cookie_created_at` BIGINT       DEFAULT NULL COMMENT '创建时间（毫秒）',
    `sys_account_cookie_updated_at` BIGINT       DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_cookie_id`),
    KEY `idx_cookie_owner` (`sys_account_cookie_owner`),
    KEY `idx_cookie_domain` (`sys_account_cookie_domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号模块 Cookie 池';

-- ---------------------------------------------------------------------
-- 密钥库（原 V6.6__add_account_secret.sql；V6.7 的类型列与 V6.8 的放宽
-- 已在列定义中内联为最终形态 VARCHAR(64)，索引一并内联）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_secret` (
    `sys_account_secret_id`         VARCHAR(64)  NOT NULL COMMENT '密钥标识（UUID）',
    `sys_account_secret_name`       VARCHAR(128) NOT NULL COMMENT '密钥名称（展示用）',
    `sys_account_secret_provider`   VARCHAR(64)  DEFAULT NULL COMMENT '密钥用途 / 厂商（aliyun / tencent / huawei / custom）',
    `sys_account_secret_type`       VARCHAR(64)  DEFAULT NULL COMMENT '密钥类型（多值逗号分隔：sms/mail/storage/payment/api/other）',
    `sys_account_secret_access_key` VARCHAR(255) DEFAULT NULL COMMENT 'AccessKeyId / 账号标识（可明文查询）',
    `sys_account_secret_secret`     TEXT         DEFAULT NULL COMMENT 'AccessKeySecret（enc: 前缀密文）',
    `sys_account_secret_region`     VARCHAR(64)  DEFAULT NULL COMMENT '区域（如 cn-hangzhou）',
    `sys_account_secret_endpoint`   VARCHAR(512) DEFAULT NULL COMMENT '服务地址（可空，按厂商默认）',
    `sys_account_secret_owner`      VARCHAR(64)  DEFAULT NULL COMMENT '归属用户（空表示部署级共享）',
    `sys_account_secret_status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1 启用 / 0 停用）',
    `sys_account_secret_remark`     VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `sys_account_secret_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 正常 / 1 已删除）',
    `sys_account_secret_created_at` BIGINT       DEFAULT NULL COMMENT '创建时间（毫秒）',
    `sys_account_secret_updated_at` BIGINT       DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_secret_id`),
    KEY `idx_account_secret_owner` (`sys_account_secret_owner`),
    KEY `idx_account_secret_provider` (`sys_account_secret_provider`),
    KEY `idx_account_secret_type` (`sys_account_secret_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号模块密钥库';

-- ---------------------------------------------------------------------
-- 上游余额查询配置（原 V6.9__add_credit_config.sql；V6.10 的 AK/SK 两列
-- 已在列定义中内联为最终形态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_credit_config` (
    `sys_account_credit_config_provider` VARCHAR(64)   NOT NULL COMMENT '上游 provider id（网关里的标识）',
    `sys_account_credit_config_kind`     VARCHAR(32)   DEFAULT NULL COMMENT '查询实现（expression / new-api / sub2api / openai / host，空表示按表达式回落）',
    `sys_account_credit_config_url`      VARCHAR(1024) DEFAULT NULL COMMENT '余额接口地址',
    `sys_account_credit_config_method`   VARCHAR(8)    DEFAULT NULL COMMENT 'HTTP 方法（GET / POST / PUT，空按 GET）',
    `sys_account_credit_config_header`   VARCHAR(64)   DEFAULT NULL COMMENT '令牌请求头名（空按 Authorization）',
    `sys_account_credit_config_token`    TEXT          DEFAULT NULL COMMENT '令牌（enc: 前缀密文）',
    `sys_account_credit_config_expr`     VARCHAR(512)  DEFAULT NULL COMMENT '字段表达式，多段用分号分隔',
    `sys_account_credit_config_billing`  VARCHAR(16)   DEFAULT NULL COMMENT '计费币种口径（usd / cny，空按表达式币种）',
    `sys_account_credit_config_status`   TINYINT       NOT NULL DEFAULT 1 COMMENT '状态（1 启用 / 0 停用）',
    `sys_account_credit_config_remark`   VARCHAR(512)  DEFAULT NULL COMMENT '备注',
    `sys_account_credit_config_deleted`  TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 正常 / 1 已删除）',
    `sys_account_credit_config_created_at` BIGINT      DEFAULT NULL COMMENT '创建时间（毫秒）',
    `sys_account_credit_config_updated_at` BIGINT      DEFAULT NULL COMMENT '更新时间（毫秒）',
    `sys_account_credit_config_ak`       VARCHAR(512)  DEFAULT NULL COMMENT 'Access Key（AK/SK 签名类余额接口用，enc: 前缀密文）',
    `sys_account_credit_config_sk`       VARCHAR(512)  DEFAULT NULL COMMENT 'Secret Key（AK/SK 签名类余额接口用，enc: 前缀密文）',
    PRIMARY KEY (`sys_account_credit_config_provider`),
    KEY `idx_account_credit_config_status` (`sys_account_credit_config_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上游余额查询配置';

-- =====================================================================
-- [三] 渠道管理五表（原 V7.2__migrate_channel_to_account.sql 的渠道 /
--       渠道模型 / 渠道连接 / 渠道健康四表，与 V7.5__add_channel_health_log.sql
--       的健康历史表，建表部分已并入本文件）
-- 迁移口径: V7.2 中「INSERT...SELECT 旧 sys_ai_provider* / sys_ai_channel_health
--       表」的存量数据复制不并入 —— 那是存量库的一次性搬迁（新库没有旧表），
--       随 V7.2 一并退役；V7.3__drop_legacy_channel_tables.sql 的旧表 DROP
--       亦不并入，旧表不再自动删除，需要清理的存量库请手工执行。
-- 约束: 脚本会被重复执行，一律 CREATE TABLE IF NOT EXISTS，
--       索引随建表语句一起创建，避免二次执行报错。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 渠道（开关 + 上游信息）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_channel` (
    `sys_account_channel_id`              VARCHAR(64)  NOT NULL COMMENT '渠道ID（UUIDv7）',
    `sys_account_channel_platform`        VARCHAR(64)  NOT NULL COMMENT '平台类型 / 渠道键（openai/anthropic/google 等）',
    `sys_account_channel_name`            VARCHAR(255) NOT NULL COMMENT '渠道展示名',
    `sys_account_channel_base_url`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'API 基础地址（免费渠道为空串）',
    `sys_account_channel_auth_scheme`     VARCHAR(32)  NOT NULL DEFAULT 'api_key' COMMENT '认证方式（api_key/bearer/oauth/none）',
    `sys_account_channel_credentials`     TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `sys_account_channel_enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '渠道是否启用',
    `sys_account_channel_bedrock_config`  TEXT         NULL COMMENT 'AWS Bedrock配置JSON',
    `sys_account_channel_extra`           TEXT         NULL COMMENT '扩展配置JSON（如自定义请求头）',
    `sys_account_channel_proxy`           VARCHAR(512) NULL COMMENT 'HTTP代理地址',
    `sys_account_channel_sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_account_channel_create_time`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建时间（毫秒）',
    `sys_account_channel_update_time`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新时间（毫秒）',
    `sys_account_channel_type`            VARCHAR(64)  NULL DEFAULT 'custom' COMMENT '渠道类型（openai/anthropic/gemini/qwen/custom）',
    `sys_account_channel_api_key`         VARCHAR(512) NULL COMMENT '渠道 API Key（旧列，保留兼容）',
    `sys_account_channel_logo`            VARCHAR(1024) NULL COMMENT 'Logo 图片地址',
    `sys_account_channel_models`          TEXT         NULL COMMENT '可用模型列表（JSON 字符串，旧列）',
    `sys_account_channel_deleted`         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `create_name` VARCHAR(64) NULL COMMENT '创建人姓名',
    `create_by`   INT         NULL COMMENT '创建人',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_name` VARCHAR(64) NULL COMMENT '更新人姓名',
    `update_by`   INT         NULL COMMENT '更新人',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`sys_account_channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道';

-- ---------------------------------------------------------------------
-- 渠道模型（模型级开关）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_channel_model` (
    `sys_account_channel_model_provider_id`        VARCHAR(64)  NOT NULL COMMENT '所属渠道ID',
    `sys_account_channel_model_model`              VARCHAR(255) NOT NULL COMMENT '模型名称',
    `sys_account_channel_model_alias`              VARCHAR(128) NULL COMMENT '模型别名',
    `sys_account_channel_model_enabled`            TINYINT      NOT NULL DEFAULT 0 COMMENT '模型是否启用（同步进来默认关）',
    `sys_account_channel_model_sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_account_channel_model_description`        TEXT         NULL COMMENT '模型描述',
    `sys_account_channel_model_capabilities`       TEXT         NULL COMMENT '任务能力JSON数组',
    `sys_account_channel_model_metrics`            TEXT         NULL COMMENT '模型指标JSON（延迟/智能/输出速度等）',
    `sys_account_channel_model_context_window`     INT          NULL COMMENT '上下文窗口',
    `sys_account_channel_model_max_tokens`         INT          NULL COMMENT '最大输出令牌',
    `sys_account_channel_model_temperature`        DOUBLE       NULL COMMENT '温度',
    `sys_account_channel_model_top_p`              DOUBLE       NULL COMMENT 'Top-P 采样',
    `sys_account_channel_model_frequency_penalty`  DOUBLE       NULL COMMENT '频率惩罚',
    `sys_account_channel_model_family`             VARCHAR(255) NULL COMMENT '模型系列（family）',
    `sys_account_channel_model_knowledge`          VARCHAR(255) NULL COMMENT '知识截止日期',
    `sys_account_channel_model_release_date`       VARCHAR(64)  NULL COMMENT '发布日期',
    `sys_account_channel_model_last_updated`       VARCHAR(64)  NULL COMMENT '最后更新时间',
    `sys_account_channel_model_modalities`         TEXT         NULL COMMENT '输入/输出模态JSON',
    `sys_account_channel_model_open_weights`       TINYINT      NULL COMMENT '是否开源权重',
    `sys_account_channel_model_attachment`         TINYINT      NULL COMMENT '是否支持附件',
    `sys_account_channel_model_reasoning`          TINYINT      NULL COMMENT '是否支持深度思考',
    `sys_account_channel_model_tool_call`          TINYINT      NULL COMMENT '是否支持工具调用',
    `sys_account_channel_model_temperature_supported` TINYINT   NULL COMMENT '是否支持温度调节',
    `sys_account_channel_model_create_time`        BIGINT       NOT NULL DEFAULT 0 COMMENT '创建时间（毫秒）',
    `sys_account_channel_model_update_time`        BIGINT       NOT NULL DEFAULT 0 COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_model_provider_id`, `sys_account_channel_model_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道模型';

-- ---------------------------------------------------------------------
-- 渠道连接（绑定凭据）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_channel_connection` (
    `sys_account_channel_connection_id`            VARCHAR(64)  NOT NULL COMMENT '连接ID（UUID）',
    `sys_account_channel_connection_provider_id`   VARCHAR(64)  NOT NULL COMMENT '所属渠道ID',
    `sys_account_channel_connection_role`          VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '连接角色（default/backup等）',
    `sys_account_channel_connection_account_type`  VARCHAR(16)  NOT NULL DEFAULT 'FIXED' COMMENT '账号类型（FIXED/TEMPORARY）',
    `sys_account_channel_connection_label`         VARCHAR(255) NULL COMMENT '连接标签',
    `sys_account_channel_connection_user_id`       VARCHAR(64)  NULL COMMENT '归属用户（账号隔离）',
    `sys_account_channel_connection_base_url`      VARCHAR(512) NULL COMMENT '覆盖的基础URL',
    `sys_account_channel_connection_auth_scheme`   VARCHAR(32)  NULL COMMENT '覆盖的认证方式',
    `sys_account_channel_connection_credentials`   TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `sys_account_channel_connection_extra`         TEXT         NULL COMMENT '扩展字段JSON',
    `sys_account_channel_connection_create_time`   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建时间（毫秒）',
    `sys_account_channel_connection_update_time`   BIGINT       NOT NULL DEFAULT 0 COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_connection_id`),
    KEY `idx_account_channel_conn_pid` (`sys_account_channel_connection_provider_id`),
    KEY `idx_account_channel_conn_user` (`sys_account_channel_connection_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道连接';

-- ---------------------------------------------------------------------
-- 渠道健康（探针状态机，一连接一行的最新态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_channel_health` (
    `sys_account_channel_health_id` VARCHAR(36) NOT NULL COMMENT '健康记录标识',
    `provider_id`   VARCHAR(64) NOT NULL COMMENT '渠道标识（UUID）',
    `connection_id` VARCHAR(36)  NOT NULL COMMENT '连接标识（UUID）',
    `channel`       VARCHAR(64)  NOT NULL COMMENT '渠道键（平台）',
    `label`         VARCHAR(128) DEFAULT NULL COMMENT '渠道展示名',
    `state`         VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态 (UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED)',
    `circuit_state` VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT '人工熔断状态（AUTO/OPEN/CLOSED）',
    `circuit_reason` VARCHAR(512) DEFAULT NULL COMMENT '人工熔断/放行的原因说明',
    `circuit_updated_at` BIGINT  DEFAULT NULL COMMENT '人工熔断状态变更时间（毫秒）',
    `failures`      BIGINT NOT NULL DEFAULT 0 COMMENT '累计失败次数',
    `successes`     BIGINT NOT NULL DEFAULT 0 COMMENT '累计成功次数',
    `consecutive_failures`  INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `consecutive_successes` INT NOT NULL DEFAULT 0 COMMENT '连续成功次数',
    `last_probe_at`      BIGINT DEFAULT NULL COMMENT '最近探针时间（毫秒）',
    `last_probe_status`  INT    DEFAULT NULL COMMENT '最近探针 HTTP 状态码',
    `last_probe_latency` INT    DEFAULT NULL COMMENT '最近探针延迟（毫秒）',
    `last_probe_error`   VARCHAR(512) DEFAULT NULL COMMENT '最近探针错误信息',
    `average_latency`    INT    DEFAULT NULL COMMENT '平均延迟（毫秒）',
    `total_failures`     BIGINT DEFAULT 0 COMMENT '计费/熔断口径累计失败次数',
    `interval_seconds`   INT NOT NULL DEFAULT 0 COMMENT '探针间隔秒（0=走全局设置）',
    `enabled`            TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用探针',
    `created_at`         BIGINT DEFAULT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_health_id`),
    UNIQUE KEY `uk_provider_connection` (`provider_id`, `connection_id`),
    KEY `idx_state` (`state`),
    KEY `idx_channel` (`channel`),
    KEY `idx_updated` (`updated_at`),
    KEY `idx_circuit_state` (`circuit_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道健康探针记录';

-- ---------------------------------------------------------------------
-- 渠道健康探针历史（追加式时序表，原 V7.5__add_channel_health_log.sql）
-- 每次探针落最新态时同事务追加一行，供渠道页「健康」页签画延迟趋势；
-- 默认保留 7 天，由 ChannelHealthLogCleaner 每日清理过期行。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_account_channel_health_log` (
    `sys_account_channel_health_log_id` VARCHAR(36) NOT NULL COMMENT '健康历史标识',
    `provider_id`   VARCHAR(64) NOT NULL COMMENT '渠道标识（UUID）',
    `connection_id` VARCHAR(36) NOT NULL COMMENT '连接标识（UUID）',
    `channel`       VARCHAR(64) DEFAULT NULL COMMENT '渠道键（平台）',
    `state`         VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '本次探针结论 (UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED)',
    `latency_ms`    INT         DEFAULT NULL COMMENT '本次探针延迟（毫秒）',
    `http_status`   INT         DEFAULT NULL COMMENT '本次探针 HTTP 状态码',
    `error`         VARCHAR(255) DEFAULT NULL COMMENT '本次探针错误摘要（截断）',
    `created_at`    BIGINT      NOT NULL COMMENT '记录时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_health_log_id`),
    KEY `idx_chlog_conn_time` (`connection_id`, `created_at`),
    KEY `idx_chlog_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道健康探针历史（保留7天）';

-- =====================================================================
-- [四] 统一发送日志表（原 V7.4__add_account_send_log.sql，已并入本文件）
-- 说明: MailboxService 统一发信门面的通道级完整日志（邮件 / 短信）。
--       发送成功与失败都落一条，异步写入，失败不影响发送主流程。
--       业务侧留痕（验证码 sys_sms_log / 消息推送 sys_message_log）仍在
--       各业务表，本表只记录「通道视角」的每一次真实发送。
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

