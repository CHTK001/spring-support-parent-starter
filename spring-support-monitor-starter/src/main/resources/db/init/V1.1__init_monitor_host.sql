-- V1.1__init_monitor_host.sql
-- 监控模块：主机画像新增字段
-- 兼容 H2(MySQL模式) / MySQL 8 / PostgreSQL 12
-- 说明: MySQL 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS，
--       此处用 information_schema 条件判断 + PREPARE/EXECUTE 动态执行，
--       H2/PostgreSQL 执行到 PREPARE 段失败时由 continue-on-error 跳过。

-- 公网IP
SET @h1 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_public_ip');
SET @sql = IF(@h1 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_public_ip VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 内网IP
SET @h2 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_private_ip');
SET @sql = IF(@h2 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_private_ip VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- MAC地址
SET @h3 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_mac');
SET @sql = IF(@h3 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_mac VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 系统版本
SET @h4 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_os_version');
SET @sql = IF(@h4 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_os_version VARCHAR(256)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 包管理器
SET @h5 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_pkg_manager');
SET @sql = IF(@h5 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_pkg_manager VARCHAR(64)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 远控协议 (host/vnc/ssh/rdp)
SET @h6 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_remote_protocol');
SET @sql = IF(@h6 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_remote_protocol VARCHAR(32)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 是否开启SIP
SET @h7 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_sip_enabled');
SET @sql = IF(@h7 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_sip_enabled INTEGER DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- SIP服务器
SET @h8 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_sip_host');
SET @sql = IF(@h8 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_sip_host VARCHAR(128)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- SIP端口
SET @h9 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_sip_port');
SET @sql = IF(@h9 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_sip_port INTEGER', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- SIP密钥
SET @h10 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_sip_secret');
SET @sql = IF(@h10 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_sip_secret VARCHAR(256)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- SIP本地端口映射
SET @h11 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ms_server_host' AND column_name = 'server_host_sip_local_port');
SET @sql = IF(@h11 = 0, 'ALTER TABLE ms_server_host ADD COLUMN server_host_sip_local_port INTEGER', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
