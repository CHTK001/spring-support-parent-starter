-- =====================================================================
-- V6.6__add_account_secret.sql
-- 通道: INIT 建表
-- 说明: 密钥管理（密钥库）。把「厂商访问密钥」从账号里拆出来单独管理，
--       账号只引用密钥标识，后续 server 等底层模块也能按同一份密钥复用。
--
-- 约定：
--   * 主键 VARCHAR(64) UUID；
--   * access_key 是可检索标识，secret 落库前经 account.security.secret-key 加密，
--     密文带 enc: 前缀；读接口只回 hasSecret；
--   * owner 为空表示部署级共享，非空只对本人可见可改；
--   * 时间统一 BIGINT 毫秒，删除统一逻辑删除。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_account_secret` (
    `sys_account_secret_id`         VARCHAR(64)  NOT NULL COMMENT '密钥标识（UUID）',
    `sys_account_secret_name`       VARCHAR(128) NOT NULL COMMENT '密钥名称（展示用）',
    `sys_account_secret_provider`   VARCHAR(64)  DEFAULT NULL COMMENT '密钥用途 / 厂商（aliyun / tencent / huawei / custom）',
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
    KEY `idx_account_secret_provider` (`sys_account_secret_provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号模块密钥库';
