-- =====================================================================
-- V6.10__add_credit_config_aksk.sql
-- 通道: INIT 建表
-- 说明: 上游余额查询配置补充 AK/SK 两列。有 4 家云厂商（阿里云 BSS / 火山 billing /
--       百度财务 / 七牛）的官方余额接口要按 AK/SK 对请求做 HMAC 签名，只发 Bearer
--       查不到。签名方案由 datasearch 的 host 预设按 provider id 自动注入，这里只负责
--       把两把密钥安全地存下来、查询前解密后传进 CreditQuery。
--
-- 约定：
--   * 与 token 同口径：落库前经 account.security.secret-key 加密，密文带 enc: 前缀，
--     读接口只回 hasAccessKey / hasSecretKey；
--   * 两列可空，历史配置不受影响；只有命中签名类 host 预设才会用到；
--   * 与 V6.8 同约定：版本化 INIT 脚本按版本顺序执行一次，用裸 ALTER，不加 IF NOT EXISTS
--     （ADD COLUMN IF NOT EXISTS 在 MySQL 8.0 是语法错误，只有 MariaDB 支持）。
-- =====================================================================

ALTER TABLE `sys_account_credit_config`
    ADD COLUMN `sys_account_credit_config_ak` VARCHAR(512) DEFAULT NULL
        COMMENT 'Access Key（AK/SK 签名类余额接口用，enc: 前缀密文）',
    ADD COLUMN `sys_account_credit_config_sk` VARCHAR(512) DEFAULT NULL
        COMMENT 'Secret Key（AK/SK 签名类余额接口用，enc: 前缀密文）';
