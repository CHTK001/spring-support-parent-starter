-- ---------------------------------------------------------------------
-- V2.6: 发布级 SkyWalking 注入列 - 存量库补列
-- 新库由 V2.0 全量建表覆盖（ms_server_release 里已含这 4 列），本文件仅对已有库补列。
--
-- 背景：这 4 列是先有表、后加字段，直接补进了 V2.0 的 CREATE TABLE，
-- 而存量库不会重建表，db/init 里也没有对应的增量脚本（V2.1~V2.5 都不涉及
-- ms_server_release）。表现就是打开「发布管理」时：
--   GET /ms/server/release/list → 500
--   Unknown column 'sw_enabled' in 'field list'
--
-- 写法约束：FlywayLike 用 FlywayScripts.splitStatements 按语句外分号逐条切分，
-- 不认 DELIMITER / BEGIN...END（CREATE PROCEDURE 会被切碎，整段静默失败，
-- 且脚本仍被记成「已执行」，之后再也不会重跑）。所以这里只写单条 DDL，
-- 不用存储过程做判列。
--
-- 幂等性：MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，靠 sys_database_version
-- 的「已成功执行」记录保证只跑一次；若某列已被手工加过，该条失败一次
-- 由 continue-on-error 记录后跳过，不影响启动。
-- ---------------------------------------------------------------------

ALTER TABLE ms_server_release
    ADD COLUMN sw_enabled TINYINT NULL COMMENT '是否启用 SkyWalking（0/1）';

ALTER TABLE ms_server_release
    ADD COLUMN sw_jar_path VARCHAR(512) NULL COMMENT 'agent jar 路径（目标主机）';

ALTER TABLE ms_server_release
    ADD COLUMN sw_service_name_prefix VARCHAR(128) NULL COMMENT 'service_name 前缀';

ALTER TABLE ms_server_release
    ADD COLUMN sw_backend_service VARCHAR(128) NULL COMMENT 'OAP 地址（collector.backend_service）';
