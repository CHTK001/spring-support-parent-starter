-- V1.6: Agent 相关列补齐（列名必须与实体 @TableField 一致，统一带 ms_ 前缀）
-- 历史版本写的是 server_host_agent_installed / server_host_agent_port（无前缀），
-- 与 ms_server_host 建表脚本和 MsServerHost 实体的 ms_server_host_agent_* 不是一套，
-- 结果 ALTER 加了一列没人读、真正要查的列始终不存在，主机列表直接 500。
-- 全新库以 db/init/V1.0__init_all_server.sql 的建表语句为准；本脚本只服务存量库补列。

ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_agent_installed INT NULL DEFAULT 0 COMMENT 'Agent是否已安装(0否 1是)';
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_agent_port INT NULL DEFAULT 19876 COMMENT 'Agent HTTP端口(画像与生命周期共用)';
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_agent_version VARCHAR(32) NULL COMMENT 'Agent版本号(/health回读)';
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_agent_time DATETIME NULL COMMENT '最近一次Agent采集成功时间';
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_agent_error VARCHAR(512) NULL COMMENT '最近一次Agent失败原因';
