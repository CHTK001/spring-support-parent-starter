-- ============================================================
-- 资产主表补列：Agent 安装标记
-- 模块: spring-support-server-starter
-- 背景: MsServerHost.serverHostAgentInstalled 与 Agent 自动部署流程
--       都读写 ms_server_host_agent_installed，但建表脚本从未包含该列，
--       导致主机列表查询（含定时在离线/采集调度）必然报
--       "Column ms_server_host_agent_installed not found"。
-- 幂等: ADD COLUMN IF NOT EXISTS，重复执行安全
-- ============================================================

ALTER TABLE `ms_server_host`
    ADD COLUMN IF NOT EXISTS `ms_server_host_agent_installed` TINYINT(1) DEFAULT 0
        COMMENT 'Agent 是否已安装: 0未安装 1已安装';
