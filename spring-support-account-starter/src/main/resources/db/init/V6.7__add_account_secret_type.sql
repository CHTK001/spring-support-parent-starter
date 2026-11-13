-- =====================================================================
-- V6.7__add_account_secret_type.sql
-- 通道: INIT 建表
-- 说明: 给密钥库补「类型」维度。原先只有 provider（厂商），无法表达
--       「这条密钥拿来干什么」；账号引用密钥时按类型挑选（如短信密钥、
--       对象存储密钥、支付密钥），类型不同字段含义也不同。
--
-- 约定：
--   * secret_type 取值：sms（短信）/ storage（对象存储）/ payment（支付）
--     / mail（邮件）/ api（通用 API）/ other（其他）；新增列可空，
--     历史数据回填 sms 之外的按 provider 推断不了时留空，前端按「未分类」展示；
--   * owner 为空表示部署级共享，非空只对本人可见可改；
--   * 时间统一 BIGINT 毫秒。
-- =====================================================================

ALTER TABLE `sys_account_secret`
    ADD COLUMN `sys_account_secret_type` VARCHAR(32) DEFAULT NULL COMMENT '密钥类型（sms/storage/payment/mail/api/other）' AFTER `sys_account_secret_provider`;

ALTER TABLE `sys_account_secret`
    ADD KEY `idx_account_secret_type` (`sys_account_secret_type`);
