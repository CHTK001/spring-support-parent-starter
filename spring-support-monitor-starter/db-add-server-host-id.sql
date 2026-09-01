-- Add ms_server_host_id column to ms_monitor table (if not exists)
ALTER TABLE ms_monitor ADD COLUMN ms_server_host_id BIGINT DEFAULT NULL COMMENT '关联服务器主机ID(ms_server_host.id)' AFTER ms_monitor_agent_key;
