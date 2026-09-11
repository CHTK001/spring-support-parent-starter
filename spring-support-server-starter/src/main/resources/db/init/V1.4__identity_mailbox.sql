-- =====================================================================
-- 服务器资产模块增量 DDL：邮箱账号(MAILBOX 身份) 支持
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明:
--   新增「邮箱账号」登录身份类型(ms_server_identity_auth_type='MAILBOX')。
--   邮箱身份用 ms_server_identity_username 存邮箱地址、password 存授权码，
--   并新增两列记录 IMAP 服务器地址/端口(默认 imap.<邮箱域名>:993)。
--   本文件为增量脚本，仅对已由 V1.0 建表的存量库执行(新库由 V1.0 直接含这两列)。
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

ALTER TABLE ms_server_identity
    ADD COLUMN ms_server_identity_imap_host VARCHAR(128) NULL COMMENT '邮箱IMAP服务器(MAILBOX类型)',
    ADD COLUMN ms_server_identity_imap_port INT NULL COMMENT '邮箱IMAP端口(MAILBOX类型,默认993)';