-- =====================================================================
-- V6.8__widen_account_secret_type.sql
-- 通道: INIT 建表
-- 说明: 密钥类型支持多选。一条密钥可能同时服务多个业务（例如一把阿里云
--       AK 既发短信又读写对象存储），因此 secret_type 改为「逗号分隔的多值」
--       （如 sms,storage）。原 VARCHAR(32) 装不下全选六类的组合，这里放宽到
--       VARCHAR(64)。
--
-- 约定：
--   * 取值仍为 sms / mail / storage / payment / api / other 的任意组合，
--     以半角逗号分隔，全小写、无空格；
--   * 单值历史数据无需迁移，读取方按 token 匹配即可兼容；
--   * 空值表示未分类，前端按「未分类」展示。
-- =====================================================================

ALTER TABLE `sys_account_secret`
    MODIFY COLUMN `sys_account_secret_type` VARCHAR(64) DEFAULT NULL COMMENT '密钥类型（多值逗号分隔：sms/mail/storage/payment/api/other）';
