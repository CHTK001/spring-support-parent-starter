-- =====================================================================
-- 服务器资产模块增量 DDL：ms_proxy 补齐 SysBase 审计列
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明: 历史库的 ms_proxy 表缺少 create_by 列（V1.0 旧 DDL 遗漏），
--       而 MsProxy 继承 SysBase，MyBatis-Plus 自动 SELECT 包含 create_by，
--       缺列会导致 /proxy/list、/proxy/current 报「数据操作失败」。
--       本增量脚本幂等补列；新库由 V1.0 全量 DDL 直接包含，执行后为空操作。
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

ALTER TABLE ms_proxy ADD COLUMN IF NOT EXISTS create_by INT NULL COMMENT '创建人';
