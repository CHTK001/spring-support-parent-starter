-- =====================================================================
-- V6.9__add_credit_config.sql
-- 通道: INIT 建表
-- 说明: 上游余额查询配置。网关（magpie）读不到余额的上游，按这里配置的
--       地址 / 字段表达式再查一次；查询实现由
--       spring-support-datasearch-starter 的 CreditQueryProvider SPI 提供。
--
-- 约定：
--   * 主键就是 provider id（网关里的上游标识，如 moxuna / zzz-jierui-dpdns），
--     一个上游最多一条配置；
--   * token 落库前经 account.security.secret-key 加密，密文带 enc: 前缀，
--     接口只回 hasToken；
--   * expression 是 datasearch 的字段表达式，如 $data.total_available / 500000；
--   * status=1 才参与查询，0 表示停用（配置留着但不读）；
--   * 时间统一 BIGINT 毫秒，删除统一逻辑删除。
-- =====================================================================

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
    PRIMARY KEY (`sys_account_credit_config_provider`),
    KEY `idx_account_credit_config_status` (`sys_account_credit_config_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上游余额查询配置';
