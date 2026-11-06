-- =====================================================================
-- V6.5__add_account.sql
-- 通道: INIT 建表
-- 说明: 账号管理的三张核心表，属于账号模块自己的数据，与 AI 的 sys_ai_* 表无关。
--
--   1) sys_account         统一账号：密钥（设备等）、POP/SMTP 邮箱、短信
--   2) sys_account_app_key 外部系统访问密钥（与 AI 的应用密钥无关）
--   3) sys_account_cookie  Cookie 池（免登录渠道 / 站点会话）
--
-- 约定：
--   * 主键统一 VARCHAR(64) UUID；
--   * 敏感值（secret / cookie_value）落库前经 account.security.secret-key 加密，
--     密文带 enc: 前缀，历史明文仍可读；
--   * 时间统一 BIGINT 毫秒；
--   * 删除统一逻辑删除列 deleted（0 正常 / 1 已删除）。
-- =====================================================================

-- ---------- 1. 统一账号 ----------
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

-- ---------- 2. 外部访问密钥 ----------
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

-- ---------- 3. Cookie 池 ----------
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
