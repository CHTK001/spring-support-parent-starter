-- =====================================================================
-- V7.0__init_all_account.sql - spring-support-account-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。
--
-- 合并方式:
--   1. 原来靠 ALTER 补的列，一律折进建表语句 —— 新库一次建全，
--      不再依赖「先建表再补列」的两段式；
--   2. 存量库补列的 ALTER 仍原样保留在文件末尾（information_schema 判存 + 动态 DDL），
--      否则老库缺列；新建库上这些语句会因列已存在而跳过。
--
-- 版本递进: V2.0 -> V7.0。原 V2.1__add_template_source.sql /
--   V2.4__add_template_source_code.sql / V6.4__add_channel_message_template.sql /
--   V6.5__add_account.sql / V6.6__add_account_secret.sql /
--   V6.7__add_account_secret_type.sql / V6.8__widen_account_secret_type.sql /
--   V6.9__add_credit_config.sql / V6.10__add_credit_config_aksk.sql
--   均在本次收敛中并入本文件并删除。数据类语句（V6.4 内置模板 INSERT、
--   V2.4 存量回填 UPDATE）移入 V7.0__initdata_all_account.sql。
--   FlywayLike 按「脚本名 + 版本」记账，命中成功记录即跳过（checksum 变化
--   只做校验、不会触发重跑），故合并增量必须递进版本号，否则存量库不会
--   重跑、并入的补列与建表不会生效。
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
--       数据类语句移入 V7.0__initdata_all_account.sql）
-- =====================================================================

-- ---------------------------------------------------------------------
-- AI 渠道短信邮件消息模板（原 V6.4__add_channel_message_template.sql 建表）
-- 内置四条模板种子已移入 V7.0__initdata_all_account.sql。
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
-- [三] 存量库兼容 ALTER（原 V2.1 / V2.4 / V6.7 / V6.8 / V6.10 增量已并入
--       并删除；全部改写为 information_schema 判存 + 动态 DDL 的幂等形式）
--
-- 背景：V2.0 是「唯一建表入口」，但历史演进中 template_info 的补列 /
--   索引变更与账号模块新表曾以独立增量脚本下发；已经被标记执行过 V2.0 的
--   库存不到这些新增内容（表现：模板列表页 / 账号页 500「Unknown column」）。
--
-- 约束：全部语句幂等 —— MySQL 8.4 / MariaDB 11.8 都拒绝 ADD/MODIFY COLUMN
--   IF NOT EXISTS 与 DROP INDEX IF EXISTS，一律走 information_schema 判存 +
--   PREPARE 动态 DDL，新库与老库都可反复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- [V2.1] template_info: 来源扩展三列 + 索引 + 唯一约束迁移
-- （数据回填见 V7.0__initdata_all_account.sql —— 本脚本无数据类语句）
-- ---------------------------------------------------------------------
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND COLUMN_NAME = 'template_info_source'), 'SELECT 1', 'ALTER TABLE template_info ADD COLUMN template_info_source VARCHAR(32) NOT NULL DEFAULT ''hiprint'' COMMENT ''来源：hiprint(绘制)/message(消息)/paper(组卷)''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND COLUMN_NAME = 'template_info_source_id'), 'SELECT 1', 'ALTER TABLE template_info ADD COLUMN template_info_source_id VARCHAR(64) COMMENT ''来源行 ID（如短信模板的 sys_message_template.id，与 template_info_id 对应）''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND COLUMN_NAME = 'template_info_provider'), 'SELECT 1', 'ALTER TABLE template_info ADD COLUMN template_info_provider VARCHAR(64) COMMENT ''来源提供方（消息提供商 ID / 组卷归属用户 ID，与 template_info_id 对应）''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND INDEX_NAME = 'idx_template_info_source'), 'SELECT 1', 'CREATE INDEX idx_template_info_source ON template_info(template_info_source)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND INDEX_NAME = 'idx_template_info_source_id'), 'SELECT 1', 'CREATE INDEX idx_template_info_source_id ON template_info(template_info_source_id)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND INDEX_NAME = 'idx_template_info_provider'), 'SELECT 1', 'CREATE INDEX idx_template_info_provider ON template_info(template_info_provider)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 原唯一名称约束改为（名称 + 来源）唯一，避免不同来源模板同名
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND INDEX_NAME = 'uk_template_info_name'), 'SELECT 1', 'ALTER TABLE template_info DROP INDEX uk_template_info_name');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND INDEX_NAME = 'uk_template_info_name_source'), 'SELECT 1', 'CREATE UNIQUE INDEX uk_template_info_name_source ON template_info (template_info_name, template_info_source)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- [V2.4] template_info: 三方模板 ID 列 + 索引
-- （存量回填 UPDATE 已移入 V7.0__initdata_all_account.sql）
-- ---------------------------------------------------------------------
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND COLUMN_NAME = 'template_info_source_code'), 'SELECT 1', 'ALTER TABLE template_info ADD COLUMN template_info_source_code VARCHAR(128) COMMENT ''三方模板 ID（来源侧模板编码，如短信模板 sys_message_template_code；hiprint 自建模板为空）''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'template_info' AND INDEX_NAME = 'idx_template_info_source_code'), 'SELECT 1', 'CREATE INDEX idx_template_info_source_code ON template_info(template_info_source_code)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- [V6.7] sys_account_secret: 密钥类型列 + 索引
-- ---------------------------------------------------------------------
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_secret' AND COLUMN_NAME = 'sys_account_secret_type'), 'SELECT 1', 'ALTER TABLE sys_account_secret ADD COLUMN sys_account_secret_type VARCHAR(32) DEFAULT NULL COMMENT ''密钥类型（sms/storage/payment/mail/api/other）'' AFTER sys_account_secret_provider');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_secret' AND INDEX_NAME = 'idx_account_secret_type'), 'SELECT 1', 'ALTER TABLE sys_account_secret ADD KEY idx_account_secret_type (sys_account_secret_type)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- [V6.8] sys_account_secret: 密钥类型放宽为多值（VARCHAR(64)）
-- ---------------------------------------------------------------------
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_secret' AND COLUMN_NAME = 'sys_account_secret_type' AND CHARACTER_MAXIMUM_LENGTH >= 64), 'ALTER TABLE sys_account_secret MODIFY COLUMN sys_account_secret_type VARCHAR(64) DEFAULT NULL COMMENT ''密钥类型（多值逗号分隔：sms/mail/storage/payment/api/other）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- [V6.10] sys_account_credit_config: AK/SK 两列（HMAC 签名类余额接口用）
-- ---------------------------------------------------------------------
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_credit_config' AND COLUMN_NAME = 'sys_account_credit_config_ak'), 'SELECT 1', 'ALTER TABLE sys_account_credit_config ADD COLUMN sys_account_credit_config_ak VARCHAR(512) DEFAULT NULL COMMENT ''Access Key（AK/SK 签名类余额接口用，enc: 前缀密文）''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_credit_config' AND COLUMN_NAME = 'sys_account_credit_config_sk'), 'SELECT 1', 'ALTER TABLE sys_account_credit_config ADD COLUMN sys_account_credit_config_sk VARCHAR(512) DEFAULT NULL COMMENT ''Secret Key（AK/SK 签名类余额接口用，enc: 前缀密文）''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

