-- =====================================================================
-- 服务器资产扩展字段 (spring-support-server-starter)
-- 硬件规格 + 账号密码 + 公网/内网IP + 系统版本
-- 兼容 MySQL 8.0+ / H2 MySQL 模式
-- 说明: 依赖 plugin.datasource.script.continue-on-error=true 容忍
--       已存在列的 "Duplicate column" 错误(幂等升级)
-- @since 4.0.0.42
-- =====================================================================

ALTER TABLE ms_server_host ADD COLUMN ms_server_host_cpu_cores   INT          NULL COMMENT 'CPU核心数';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_memory_mb    BIGINT       NULL COMMENT '内存容量(MB)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_disk_gb      BIGINT       NULL COMMENT '磁盘容量(GB)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_os_version   VARCHAR(128) NULL COMMENT '系统版本';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_public_ip    VARCHAR(64)  NULL COMMENT '公网IP';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_private_ip   VARCHAR(64)  NULL COMMENT '内网IP';
