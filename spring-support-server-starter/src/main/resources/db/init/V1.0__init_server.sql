-- =====================================================================
-- 服务器资产模块 DDL 初始化脚本 (spring-support-server-starter)
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明: 表名与列名均以 ms_server_* 前缀组织, 与监控模块(ms_monitor_*)风格一致
-- 本文件为唯一 DDL: 全部建表(含主机扩展字段与主机级配置字段)与系统配置表
-- 初始化数据见 V1.0__initdata_server.sql
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 服务器分组(树形)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_group (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_group_parent_id BIGINT       NOT NULL DEFAULT 0      COMMENT '父分组ID, 0为根',
    ms_server_group_name      VARCHAR(64)  NOT NULL                COMMENT '分组名称',
    ms_server_group_sort      INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    ms_server_group_remark    VARCHAR(255) NULL                    COMMENT '备注',
    create_by                 INT          NULL                    COMMENT '创建人',
    create_name               VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time               DATETIME     NULL                    COMMENT '创建时间',
    update_name               VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                 INT          NULL                    COMMENT '更新人',
    update_time               DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_server_group_parent (ms_server_group_parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器分组';

-- ---------------------------------------------------------------------
-- 2. SSH 密钥库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_key (
    id                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_key_name         VARCHAR(64)  NOT NULL                COMMENT '密钥名称',
    ms_server_key_type         VARCHAR(16)  NULL                    COMMENT '密钥类型: RSA/ED25519/ECDSA',
    ms_server_key_public_key   TEXT         NULL                    COMMENT '公钥内容(明文)',
    ms_server_key_private_key  TEXT         NULL                    COMMENT '私钥内容(AES加密)',
    ms_server_key_password     VARCHAR(512) NULL                    COMMENT '私钥口令(AES加密)',
    ms_server_key_remark       VARCHAR(255) NULL                    COMMENT '备注',
    create_by                  INT          NULL                    COMMENT '创建人',
    create_name                VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                DATETIME     NULL                    COMMENT '创建时间',
     update_name             VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                  INT          NULL                    COMMENT '更新人',
    update_time                DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SSH密钥库';

-- ---------------------------------------------------------------------
-- 3. 登录身份
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_identity (
    id                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_identity_name     VARCHAR(64)  NOT NULL                COMMENT '身份名称',
    ms_server_identity_auth_type VARCHAR(16) NOT NULL DEFAULT 'PASSWORD' COMMENT '认证类型: PASSWORD/PUBLIC_KEY',
    ms_server_identity_username VARCHAR(64)  NOT NULL                COMMENT '登录用户名',
    ms_server_identity_password VARCHAR(512) NULL                    COMMENT '登录密码(AES加密)',
    ms_server_identity_key_id   BIGINT       NULL                    COMMENT '引用密钥ID(PUBLIC_KEY类型)',
    ms_server_identity_remark   VARCHAR(255) NULL                    COMMENT '备注',
    create_by                   INT          NULL                    COMMENT '创建人',
    create_name                 VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                 DATETIME     NULL                    COMMENT '创建时间',
     update_name             VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                   INT          NULL                    COMMENT '更新人',
    update_time                 DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_server_identity_key (ms_server_identity_key_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录身份';

-- ---------------------------------------------------------------------
-- 4. 服务器主机台账
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_host (
    id                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_host_name         VARCHAR(64)  NOT NULL                COMMENT '主机名称',
    ms_server_host_code         VARCHAR(64)  NULL                    COMMENT '主机编码',
    ms_server_host_address      VARCHAR(128) NOT NULL                COMMENT '主机地址',
    ms_server_host_port         INT          NOT NULL DEFAULT 22     COMMENT 'SSH端口',
    ms_server_host_os_type      VARCHAR(16)  NOT NULL DEFAULT 'LINUX' COMMENT '系统类型: LINUX/WINDOWS/MACOS',
    ms_server_host_os_arch      VARCHAR(32)  NULL                    COMMENT '系统架构',
    ms_server_group_id          BIGINT       NULL                    COMMENT '所属分组ID',
    ms_server_host_tags         VARCHAR(512) NULL                    COMMENT '标签(JSON数组)',
    ms_server_host_status       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    ms_server_host_online       TINYINT      NOT NULL DEFAULT -1     COMMENT '在线状态 1在线 0离线 -1未知',
    ms_server_host_online_time  DATETIME     NULL                    COMMENT '最近在线时间',
    ms_server_host_agent_key    CHAR(32)     NULL                    COMMENT '探针标识',
    ms_server_host_description  VARCHAR(255) NULL                    COMMENT '描述',
    ms_server_host_cpu_cores    INT          NULL                    COMMENT 'CPU核心数',
    ms_server_host_memory_mb    BIGINT       NULL                    COMMENT '内存容量(MB)',
    ms_server_host_disk_gb      BIGINT       NULL                    COMMENT '磁盘容量(GB)',
    ms_server_host_os_version   VARCHAR(128) NULL                    COMMENT '系统版本',
    ms_server_host_public_ip    VARCHAR(64)  NULL                    COMMENT '公网IP',
    ms_server_host_private_ip   VARCHAR(64)  NULL                    COMMENT '内网IP',
    ms_server_host_online_enabled    INT NULL DEFAULT 1      COMMENT '在离线检测开关(主机级)',
    ms_server_host_online_interval   INT NULL DEFAULT 60     COMMENT '在离线检测间隔秒(主机级)',
    ms_server_host_collect_enabled   INT NULL DEFAULT 0      COMMENT '硬件采集开关(主机级)',
    ms_server_host_collect_mode      VARCHAR(16) NULL DEFAULT 'SCHEDULED' COMMENT '采集方式(主机级)',
    ms_server_host_collect_interval  INT NULL DEFAULT 3600   COMMENT '采集间隔秒(主机级)',
    ms_server_host_last_collect_time DATETIME NULL            COMMENT '最近采集时间(主机级)',
    ms_server_host_agent_enabled     INT NULL DEFAULT 0      COMMENT 'Agent上报开关(主机级)',
    ms_server_host_remote_enabled    INT NULL DEFAULT 1      COMMENT '远控开关(主机级)',
    create_by                   INT          NULL                    COMMENT '创建人',
    create_name                 VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                 DATETIME     NULL                    COMMENT '创建时间',
     update_name             VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                   INT          NULL                    COMMENT '更新人',
    update_time                 DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_host_agent (ms_server_host_agent_key),
    KEY idx_server_host_group (ms_server_group_id),
    KEY idx_server_host_address (ms_server_host_address)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器主机';

-- ---------------------------------------------------------------------
-- 5. 主机-身份关联
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_host_identity (
    id                                  INT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_host_identity_host_id     BIGINT NOT NULL              COMMENT '主机ID',
    ms_server_host_identity_identity_id BIGINT NOT NULL              COMMENT '身份ID',
    ms_server_host_identity_sort        INT  NOT NULL DEFAULT 1      COMMENT '优先级, 越小越优先',
    PRIMARY KEY (id),
    KEY idx_host_identity_host (ms_server_host_identity_host_id),
    KEY idx_host_identity_identity (ms_server_host_identity_identity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '主机身份关联';

-- ---------------------------------------------------------------------
-- 6. 远程终端会话审计
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_terminal_session (
    id                               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_terminal_session_host_id      BIGINT      NULL                    COMMENT '主机ID',
    ms_terminal_session_host_name    VARCHAR(64) NULL                    COMMENT '主机名称',
    ms_terminal_session_protocol     VARCHAR(8)  NOT NULL                COMMENT '协议: SSH/RDP/VNC/SFTP',
    ms_terminal_session_user         VARCHAR(64) NULL                    COMMENT '发起用户',
    ms_terminal_session_client_ip    VARCHAR(64) NULL                    COMMENT '客户端IP',
    ms_terminal_session_connect_time DATETIME    NULL                    COMMENT '连接时间',
    ms_terminal_session_close_time   DATETIME    NULL                    COMMENT '关闭时间',
    ms_terminal_session_status       VARCHAR(8)  NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
    ms_terminal_session_close_reason VARCHAR(128) NULL                   COMMENT '关闭原因',
    PRIMARY KEY (id),
    KEY idx_terminal_session_host (ms_terminal_session_host_id),
    KEY idx_terminal_session_status (ms_terminal_session_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端会话审计';

-- ---------------------------------------------------------------------
-- 7. 终端命令片段
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_terminal_snippet (
    id                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_terminal_snippet_name    VARCHAR(64)  NOT NULL                COMMENT '片段名称',
    ms_terminal_snippet_content TEXT         NULL                    COMMENT '片段内容',
    ms_terminal_snippet_remark  VARCHAR(255) NULL                    COMMENT '备注',
    create_by                   INT          NULL                    COMMENT '创建人',
    create_name                 VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                 DATETIME     NULL                    COMMENT '创建时间',
     update_name             VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                   INT          NULL                    COMMENT '更新人',
    update_time                 DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端命令片段';

-- ---------------------------------------------------------------------
-- 8. 终端路径书签
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_terminal_bookmark (
    id                           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_terminal_bookmark_host_id BIGINT      NOT NULL                COMMENT '主机ID',
    ms_terminal_bookmark_name    VARCHAR(64) NULL                    COMMENT '书签名称',
    ms_terminal_bookmark_path    VARCHAR(255)NOT NULL                COMMENT '书签路径',
    ms_terminal_bookmark_sort    INT         NOT NULL DEFAULT 0      COMMENT '排序号',
    PRIMARY KEY (id),
    KEY idx_terminal_bookmark_host (ms_terminal_bookmark_host_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端路径书签';

-- ---------------------------------------------------------------------
-- 9. 批量计划任务
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_exec_job (
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_exec_job_name         VARCHAR(64)  NOT NULL                COMMENT '任务名称',
    ms_exec_job_cron         VARCHAR(32)  NOT NULL                COMMENT 'cron表达式(6域)',
    ms_exec_job_command      TEXT         NOT NULL                COMMENT '执行命令',
    ms_exec_job_host_ids     VARCHAR(512) NOT NULL                COMMENT '目标主机ID集合(JSON数组)',
    ms_exec_job_timeout      INT          NOT NULL DEFAULT 60     COMMENT '单机超时(秒)',
    ms_exec_job_enabled      TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    ms_exec_job_last_run_time DATETIME    NULL                    COMMENT '上次运行时间',
    ms_exec_job_next_run_time DATETIME    NULL                    COMMENT '下次运行时间',
    ms_exec_job_remark       VARCHAR(255) NULL                    COMMENT '备注',
    create_by                INT          NULL                    COMMENT '创建人',
    create_name              VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time              DATETIME     NULL                    COMMENT '创建时间',
     update_name             VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                INT          NULL                    COMMENT '更新人',
    update_time              DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '批量计划任务';

-- ---------------------------------------------------------------------
-- 10. 批量执行日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_exec_log (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_exec_log_type      VARCHAR(8)   NOT NULL                COMMENT '类型: COMMAND/FILE/JOB',
    ms_exec_log_host_id   BIGINT       NULL                    COMMENT '主机ID',
    ms_exec_log_host_name VARCHAR(64)  NULL                    COMMENT '主机名称',
    ms_exec_log_job_id    BIGINT       NULL                    COMMENT '关联任务ID',
    ms_exec_log_command   VARCHAR(512) NULL                    COMMENT '命令或文件路径',
    ms_exec_log_output    LONGTEXT     NULL                    COMMENT '执行输出(截断)',
    ms_exec_log_exit_code INT          NULL                    COMMENT '退出码',
    ms_exec_log_status    VARCHAR(8)   NOT NULL                COMMENT '状态: SUCCESS/FAIL',
    ms_exec_log_error     VARCHAR(1024) NULL                   COMMENT '错误信息',
    ms_exec_log_start_time DATETIME    NULL                    COMMENT '开始时间',
    ms_exec_log_end_time  DATETIME     NULL                    COMMENT '结束时间',
    ms_exec_log_cost_ms   BIGINT       NULL                    COMMENT '耗时(毫秒)',
    PRIMARY KEY (id),
    KEY idx_exec_log_host (ms_exec_log_host_id),
    KEY idx_exec_log_job (ms_exec_log_job_id),
    KEY idx_exec_log_type (ms_exec_log_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '批量执行日志';

-- ---------------------------------------------------------------------
-- 11. 服务器系统配置 (key-value)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_config (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_config_key   VARCHAR(128) NOT NULL                COMMENT '配置键',
    ms_server_config_value VARCHAR(2048) NULL                   COMMENT '配置值(JSON字符串)',
    ms_server_config_desc  VARCHAR(255) NULL                    COMMENT '配置说明',
    create_by              INT          NULL                    COMMENT '创建人',
    create_name            VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time            DATETIME     NULL                    COMMENT '创建时间',
    update_name            VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by              INT          NULL                    COMMENT '更新人',
    update_time            DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_config_key (ms_server_config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器系统配置';
