-- ============================================================================
-- V2__drop_nomifun_legacy.sql - 清理 nomifun 遗留表
--
-- 命名规范化：旧 sys_nomifun_* 表替换为 sys_ai_* 新表（见 V1__ai_manage_schema.sql）。
-- 由于项目处于起步阶段、不做兼容迁移，直接 DROP 旧表。
-- 注意：旧表名可能不存在于全新环境，DROP IF EXISTS 幂等处理。
-- ============================================================================

DROP TABLE IF EXISTS `sys_nomifun_provider_connection`;
DROP TABLE IF EXISTS `sys_nomifun_provider_model`;
DROP TABLE IF EXISTS `sys_nomifun_provider`;
DROP TABLE IF EXISTS `sys_nomifun_message`;
DROP TABLE IF EXISTS `sys_nomifun_conversation`;