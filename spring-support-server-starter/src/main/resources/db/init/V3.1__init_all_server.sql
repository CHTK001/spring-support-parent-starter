-- =====================================================================
-- V3.1__init_all_server.sql - spring-support-server-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 版本递进原因: FlywayLike 按「脚本名 + 版本」记账，命中成功记录即跳过，
--   故本次「ms_ 前缀统一为 server_ 前缀」的表/列重命名必须递进版本号，
--   否则存量库不会重跑、新命名的表不会创建。
-- 迁移口径: RENAME 合并成 CREATE——新表直接以 server_* 命名建出，
--   不做 RENAME TABLE、不做数据搬迁；本文件只用通用 SQL
--   （CREATE TABLE IF NOT EXISTS + 内联列定义），
--   不再使用 information_schema 判存 + PREPARE 的动态 DDL 段
--   （原 V3.0 尾部兼容段中的补列已全部内联进 CREATE TABLE，
--   已废弃的 upload_auto_upgrade 列不再创建）。
-- 命名口径: 表名统一 server_ 前缀；列名去掉 ms_ 前缀、保留「表名前缀」风格
--   （如 ms_server_group_name -> server_group_name）；
--   原本就是裸列名的表（server_proxy / server_project /
--   server_project_op_log / server_release）保持裸列名不变。
-- 审计口径: 全部 15 表补齐 6 审计列
--   （create_by / create_name / create_time / update_name / update_by / update_time），
--   与 mybatis-starter 的 SysBase 审计基类字段一一对应。
-- 幂等: 建表 IF NOT EXISTS，可反复执行。
-- @author CH
-- @since 4.0.0.51
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 服务器分组(树形)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_group (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_group_parent_id    BIGINT       NOT NULL DEFAULT 0      COMMENT '父分组ID, 0为根',
    server_group_name         VARCHAR(64)  NOT NULL                COMMENT '分组名称',
    server_group_sort         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    server_group_remark       VARCHAR(255) NULL                    COMMENT '备注',
    server_group_color        VARCHAR(32)  NULL                    COMMENT '分组颜色(十六进制,如 #6d5dfc)',
    create_by                 INT          NULL                    COMMENT '创建人',
    create_name               VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time               DATETIME     NULL                    COMMENT '创建时间',
    update_name               VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                 INT          NULL                    COMMENT '更新人',
    update_time               DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_server_group_parent (server_group_parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器分组';

-- ---------------------------------------------------------------------
-- 2. SSH 密钥库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_key (
    id                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_key_name            VARCHAR(64)  NOT NULL                COMMENT '密钥名称',
    server_key_type            VARCHAR(16)  NULL                    COMMENT '密钥类型: RSA/ED25519/ECDSA',
    server_key_public_key      TEXT         NULL                    COMMENT '公钥内容(明文)',
    server_key_private_key     TEXT         NULL                    COMMENT '私钥内容(AES加密)',
    server_key_password        VARCHAR(512) NULL                    COMMENT '私钥口令(AES加密)',
    server_key_remark          VARCHAR(255) NULL                    COMMENT '备注',
    create_by                  INT          NULL                    COMMENT '创建人',
    create_name                VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                DATETIME     NULL                    COMMENT '创建时间',
    update_name                VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                  INT          NULL                    COMMENT '更新人',
    update_time                DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SSH密钥库';

-- ---------------------------------------------------------------------
-- 3. 登录身份
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_identity (
    id                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_identity_name        VARCHAR(64)  NOT NULL                COMMENT '身份名称',
    server_identity_auth_type   VARCHAR(16)  NOT NULL DEFAULT 'PASSWORD' COMMENT '认证类型: PASSWORD/PUBLIC_KEY/MAILBOX',
    server_identity_username    VARCHAR(64)  NOT NULL                COMMENT '登录用户名',
    server_identity_password    VARCHAR(512) NULL                    COMMENT '登录密码(AES加密)',
    server_identity_key_id      BIGINT       NULL                    COMMENT '引用密钥ID(PUBLIC_KEY类型)',
    server_identity_email_source VARCHAR(16) NULL DEFAULT 'IMAP'      COMMENT '邮箱来源(MAILBOX类型): IMAP(真实邮箱)/TEMP(临时邮箱API)',
    server_identity_provider    VARCHAR(32)  NULL                    COMMENT '临时邮箱服务商(TEMP来源): fce/luckmail/mailnest/mailtm',
    server_identity_imap_host   VARCHAR(128) NULL                    COMMENT '邮箱IMAP服务器(MAILBOX类型)',
    server_identity_imap_port   INT          NULL                    COMMENT '邮箱IMAP端口(MAILBOX类型,默认993)',
    server_identity_remark      VARCHAR(255) NULL                    COMMENT '备注',
    create_by                   INT          NULL                    COMMENT '创建人',
    create_name                 VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                 DATETIME     NULL                    COMMENT '创建时间',
    update_name                 VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                   INT          NULL                    COMMENT '更新人',
    update_time                 DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_server_identity_key (server_identity_key_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录身份';

-- ---------------------------------------------------------------------
-- 4. 服务器主机台账
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_host (
    id                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_host_name            VARCHAR(64)  NOT NULL                COMMENT '主机名称',
    server_host_code            VARCHAR(64)  NULL                    COMMENT '主机编码',
    server_host_address         VARCHAR(128) NOT NULL                COMMENT '主机地址',
    server_host_port            INT          NOT NULL DEFAULT 22     COMMENT 'SSH端口',
    server_host_os_type         VARCHAR(16)  NOT NULL DEFAULT 'LINUX' COMMENT '系统类型: LINUX/WINDOWS/MACOS',
    server_host_os_arch         VARCHAR(32)  NULL                    COMMENT '系统架构',
    server_host_ssh_enabled     TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否开启SSH 1是 0否(Linux默认1,Windows默认0)',
    server_group_id             BIGINT       NULL                    COMMENT '所属分组ID',
    server_host_tags            VARCHAR(512) NULL                    COMMENT '标签(JSON数组)',
    server_host_status          TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    server_host_online          TINYINT      NOT NULL DEFAULT -1     COMMENT '在线状态 1在线 0离线 -1未知',
    server_host_online_time     DATETIME     NULL                    COMMENT '最近在线时间',
    server_host_agent_key       CHAR(32)     NULL                    COMMENT '探针标识',
    server_host_description     VARCHAR(255) NULL                    COMMENT '描述',
    server_host_cpu_cores       INT          NULL                    COMMENT 'CPU核心数',
    server_host_memory_mb       BIGINT       NULL                    COMMENT '内存容量(MB)',
    server_host_disk_gb         BIGINT       NULL                    COMMENT '磁盘容量(GB)',
    server_host_os_version      VARCHAR(128) NULL                    COMMENT '系统版本',
    server_host_public_ip       VARCHAR(64)  NULL                    COMMENT '公网IP',
    server_host_private_ip      VARCHAR(64)  NULL                    COMMENT '内网IP',
    server_host_online_enabled    INT NULL DEFAULT 1                  COMMENT '在离线检测开关(主机级)',
    server_host_online_interval   INT NULL DEFAULT 60                 COMMENT '在离线检测间隔秒(主机级)',
    server_host_collect_enabled   INT NULL DEFAULT 0                  COMMENT '硬件采集开关(主机级)',
    server_host_collect_mode      VARCHAR(16) NULL DEFAULT 'SCHEDULED' COMMENT '采集方式(主机级)',
    server_host_collect_interval  INT NULL DEFAULT 3600               COMMENT '采集间隔秒(主机级)',
    server_host_last_collect_time DATETIME NULL                       COMMENT '最近采集时间(主机级)',
    server_host_agent_enabled     INT NULL DEFAULT 0                  COMMENT 'Agent上报开关(主机级)',
    server_host_remote_enabled    INT NULL DEFAULT 1                  COMMENT '远控开关(主机级)',
    server_host_delay_enabled     INT NULL DEFAULT 0                  COMMENT '延迟检测开关(主机级)',
    server_host_delay_interval    INT NULL DEFAULT 60                 COMMENT '延迟检测间隔秒(主机级)',
    server_host_delay_ms          BIGINT NULL                         COMMENT '最近延迟毫秒(检测结果)',
    server_host_last_delay_time   DATETIME NULL                       COMMENT '最近延迟检测时间(主机级)',
    server_host_ports             TEXT NULL                           COMMENT '端口扫描结果(JSON数组)',
    server_host_last_port_scan_time DATETIME NULL                     COMMENT '最近端口扫描时间',
    server_host_agent_installed INT NULL DEFAULT 0                     COMMENT 'Agent 是否已安装(0否 1是)',
    server_host_agent_port      INT NULL DEFAULT 19876                 COMMENT 'Agent HTTP端口(画像与生命周期共用)',
    server_host_agent_version   VARCHAR(32) NULL                       COMMENT 'Agent版本号(/health回读)',
    server_host_agent_time      DATETIME NULL                          COMMENT '最近一次Agent采集成功时间',
    server_host_agent_error     VARCHAR(512) NULL                      COMMENT '最近一次Agent失败原因',
    server_host_gpu_count       INT NULL                               COMMENT 'GPU卡数(探测结果)',
    server_host_gpu_model       VARCHAR(256) NULL                      COMMENT 'GPU型号(探测结果)',
    server_host_networks        TEXT NULL                              COMMENT '网卡信息(JSON数组)',
    server_host_cpu_usage       DECIMAL(5,2) NULL                      COMMENT 'CPU使用率%(探测结果)',
    server_host_mem_usage       DECIMAL(5,2) NULL                      COMMENT '内存使用率%(探测结果)',
    server_host_disk_usage      DECIMAL(5,2) NULL                      COMMENT '磁盘使用率%(探测结果)',
    server_host_disk_read_kbs   DECIMAL(10,2) NULL                     COMMENT '磁盘读取速率 KB/s(探测结果)',
    server_host_disk_write_kbs  DECIMAL(10,2) NULL                     COMMENT '磁盘写入速率 KB/s(探测结果)',
    server_host_net_rx_kbs      DECIMAL(12,2) NULL                     COMMENT '网络接收速率 KB/s(探测结果)',
    server_host_net_tx_kbs      DECIMAL(12,2) NULL                     COMMENT '网络发送速率 KB/s(探测结果)',
    server_host_software        TEXT NULL                              COMMENT '软件实例(JSON数组,进程识别结果)',
    server_host_remote_protocol VARCHAR(16) NULL DEFAULT NULL          COMMENT '远控协议: SSH/RDP/VNC, 空则按操作系统类型推断',
    server_host_remote_port     INT          NULL DEFAULT NULL         COMMENT '远控端口, 空则取协议默认端口(SSH 22/RDP 3389/VNC 5900)',
    server_host_remote_mode     VARCHAR(16)  NULL DEFAULT 'GUACD'      COMMENT '远控模式: GUACD 经 guacd 中继 / DIRECT 平台原生客户端直连',
    server_host_vnc_password    VARCHAR(512) NULL DEFAULT NULL         COMMENT 'VNC 口令(AES 密文)',
    server_host_package_manager VARCHAR(32)  NULL DEFAULT NULL         COMMENT '包管理器(探测结果): apt/dnf/yum/brew/winget',
    server_host_mac             VARCHAR(32) NULL                       COMMENT 'MAC地址',
    server_host_os_hostname     VARCHAR(128) NULL                      COMMENT '系统主机名',
    server_host_java_version    VARCHAR(64) NULL                       COMMENT 'Java版本',
    server_host_uptime_sec      BIGINT NULL                            COMMENT '运行时间秒',
    create_by                   INT          NULL                      COMMENT '创建人',
    create_name                 VARCHAR(64)  NULL                      COMMENT '创建人名称',
    create_time                 DATETIME     NULL                      COMMENT '创建时间',
    update_name                 VARCHAR(64)  NULL                      COMMENT '更新人名称',
    update_by                   INT          NULL                      COMMENT '更新人',
    update_time                 DATETIME     NULL                      COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_host_agent (server_host_agent_key),
    KEY idx_server_host_group (server_group_id),
    KEY idx_server_host_address (server_host_address)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器主机';

-- ---------------------------------------------------------------------
-- 5. 主机-身份关联
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_host_identity (
    id                                  INT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_host_identity_host_id        BIGINT NOT NULL              COMMENT '主机ID',
    server_host_identity_identity_id    BIGINT NOT NULL              COMMENT '身份ID',
    server_host_identity_sort           INT  NOT NULL DEFAULT 1      COMMENT '优先级, 越小越优先',
    create_by                           INT  NULL                    COMMENT '创建人',
    create_name                         VARCHAR(64)  NULL            COMMENT '创建人名称',
    create_time                         DATETIME     NULL            COMMENT '创建时间',
    update_name                         VARCHAR(64)  NULL            COMMENT '更新人名称',
    update_by                           INT  NULL                    COMMENT '更新人',
    update_time                         DATETIME     NULL            COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_host_identity_host (server_host_identity_host_id),
    KEY idx_host_identity_identity (server_host_identity_identity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '主机身份关联';

-- ---------------------------------------------------------------------
-- 6. 远程终端会话审计
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_terminal_session (
    id                               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_terminal_session_host_id      BIGINT      NULL                COMMENT '主机ID',
    server_terminal_session_host_name    VARCHAR(64) NULL                COMMENT '主机名称',
    server_terminal_session_protocol     VARCHAR(8)  NOT NULL            COMMENT '协议: SSH/RDP/VNC/SFTP',
    server_terminal_session_user         VARCHAR(64) NULL                COMMENT '发起用户',
    server_terminal_session_client_ip    VARCHAR(64) NULL                COMMENT '客户端IP',
    server_terminal_session_connect_time DATETIME    NULL                COMMENT '连接时间',
    server_terminal_session_close_time   DATETIME    NULL                COMMENT '关闭时间',
    server_terminal_session_status       VARCHAR(8)  NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
    server_terminal_session_close_reason VARCHAR(128) NULL               COMMENT '关闭原因',
    create_by                        INT         NULL                    COMMENT '创建人',
    create_name                      VARCHAR(64) NULL                    COMMENT '创建人名称',
    create_time                      DATETIME    NULL                    COMMENT '创建时间',
    update_name                      VARCHAR(64) NULL                    COMMENT '更新人名称',
    update_by                        INT         NULL                    COMMENT '更新人',
    update_time                      DATETIME    NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_terminal_session_host (server_terminal_session_host_id),
    KEY idx_terminal_session_status (server_terminal_session_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端会话审计';

-- ---------------------------------------------------------------------
-- 7. 终端命令片段
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_terminal_snippet (
    id                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_terminal_snippet_name    VARCHAR(64)  NOT NULL            COMMENT '片段名称',
    server_terminal_snippet_content TEXT         NULL                COMMENT '片段内容',
    server_terminal_snippet_remark  VARCHAR(255) NULL                COMMENT '备注',
    create_by                   INT          NULL                    COMMENT '创建人',
    create_name                 VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time                 DATETIME     NULL                    COMMENT '创建时间',
    update_name                 VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                   INT          NULL                    COMMENT '更新人',
    update_time                 DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端命令片段';

-- ---------------------------------------------------------------------
-- 8. 终端路径书签
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_terminal_bookmark (
    id                           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_terminal_bookmark_host_id BIGINT  NOT NULL                COMMENT '主机ID',
    server_terminal_bookmark_name    VARCHAR(64) NULL                COMMENT '书签名称',
    server_terminal_bookmark_path    VARCHAR(255)NOT NULL            COMMENT '书签路径',
    server_terminal_bookmark_sort    INT         NOT NULL DEFAULT 0  COMMENT '排序号',
    create_by                    INT         NULL                    COMMENT '创建人',
    create_name                  VARCHAR(64) NULL                    COMMENT '创建人名称',
    create_time                  DATETIME    NULL                    COMMENT '创建时间',
    update_name                  VARCHAR(64) NULL                    COMMENT '更新人名称',
    update_by                    INT         NULL                    COMMENT '更新人',
    update_time                  DATETIME    NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_terminal_bookmark_host (server_terminal_bookmark_host_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端路径书签';

-- ---------------------------------------------------------------------
-- 9. 批量计划任务
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_exec_job (
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_exec_job_name         VARCHAR(64)  NOT NULL            COMMENT '任务名称',
    server_exec_job_type         VARCHAR(16)  NOT NULL DEFAULT 'CRON' COMMENT '任务类型 CRON定时/NONE脚本',
    server_exec_job_cron         VARCHAR(32)  NOT NULL            COMMENT 'cron表达式(6域)',
    server_exec_job_command      TEXT         NOT NULL            COMMENT '执行命令',
    server_exec_job_host_ids     VARCHAR(512) NOT NULL            COMMENT '目标主机ID集合(JSON数组)',
    server_exec_job_timeout      INT          NOT NULL DEFAULT 60 COMMENT '单机超时(秒)',
    server_exec_job_enabled      TINYINT      NOT NULL DEFAULT 1  COMMENT '启用状态 1启用 0停用',
    server_exec_job_last_run_time DATETIME    NULL                COMMENT '上次运行时间',
    server_exec_job_next_run_time DATETIME    NULL                COMMENT '下次运行时间',
    server_exec_job_remark       VARCHAR(255) NULL                COMMENT '备注',
    create_by                INT          NULL                    COMMENT '创建人',
    create_name              VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time              DATETIME     NULL                    COMMENT '创建时间',
    update_name              VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by                INT          NULL                    COMMENT '更新人',
    update_time              DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '批量计划任务';

-- ---------------------------------------------------------------------
-- 10. 批量执行日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_exec_log (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_exec_log_type      VARCHAR(8)   NOT NULL            COMMENT '类型: COMMAND/FILE/JOB',
    server_exec_log_host_id   BIGINT       NULL                COMMENT '主机ID',
    server_exec_log_host_name VARCHAR(64)  NULL                COMMENT '主机名称',
    server_exec_log_job_id    BIGINT       NULL                COMMENT '关联任务ID',
    server_exec_log_command   VARCHAR(512) NULL                COMMENT '命令或文件路径',
    server_exec_log_output    LONGTEXT     NULL                COMMENT '执行输出(截断)',
    server_exec_log_exit_code INT          NULL                COMMENT '退出码',
    server_exec_log_status    VARCHAR(8)   NOT NULL            COMMENT '状态: SUCCESS/FAIL',
    server_exec_log_error     VARCHAR(1024) NULL               COMMENT '错误信息',
    server_exec_log_start_time DATETIME    NULL                COMMENT '开始时间',
    server_exec_log_end_time  DATETIME     NULL                COMMENT '结束时间',
    server_exec_log_cost_ms   BIGINT       NULL                COMMENT '耗时(毫秒)',
    create_by             INT          NULL                    COMMENT '创建人',
    create_name           VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time           DATETIME     NULL                    COMMENT '创建时间',
    update_name           VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by             INT          NULL                    COMMENT '更新人',
    update_time           DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_exec_log_host (server_exec_log_host_id),
    KEY idx_exec_log_job (server_exec_log_job_id),
    KEY idx_exec_log_type (server_exec_log_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '批量执行日志';

-- ---------------------------------------------------------------------
-- 11. 服务器系统配置 (key-value)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_config (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_config_key      VARCHAR(128) NOT NULL                COMMENT '配置键',
    server_config_value    VARCHAR(2048) NULL                   COMMENT '配置值(JSON字符串)',
    server_config_desc     VARCHAR(255) NULL                    COMMENT '配置说明',
    create_by              INT          NULL                    COMMENT '创建人',
    create_name            VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time            DATETIME     NULL                    COMMENT '创建时间',
    update_name            VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by              INT          NULL                    COMMENT '更新人',
    update_time            DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_config_key (server_config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器系统配置';

-- ---------------------------------------------------------------------
-- 12. 免费代理（每次获取免费代理时清空重录——页面从本表读取）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_proxy (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    host             VARCHAR(64)  NOT NULL COMMENT '代理主机',
    port             INT          NOT NULL COMMENT '代理端口',
    protocol         VARCHAR(16)  NULL COMMENT '协议（Http/Https/Socks4/Socks5）',
    source           VARCHAR(64)  NULL COMMENT '来源',
    delay            BIGINT       NULL COMMENT '延迟（毫秒），探测结果',
    reachable        INT          NULL COMMENT '是否可达（NULL 未检测 0 不可达 1 可达）',
    check_time       BIGINT       NULL COMMENT '最近一次探测时间（毫秒时间戳）',
    create_by        INT          NULL COMMENT '创建人',
    create_name      VARCHAR(64)  NULL COMMENT '创建人名称',
    create_time      DATETIME     NULL COMMENT '创建时间',
    update_name      VARCHAR(64)  NULL COMMENT '更新人名称',
    update_by        INT          NULL COMMENT '更新人',
    update_time      DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_proxy_host_port (host, port)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '免费代理';

-- ---------------------------------------------------------------------
-- 13. 项目管理（原 project 模块实体并入 server；原 V2.1/V2.2/V2.4 增量列已内联）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_project (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    release_id     BIGINT       NULL                    COMMENT '发布ID',
    project_name   VARCHAR(128) NOT NULL                COMMENT '项目名称',
    project_type   VARCHAR(32)  NULL                    COMMENT '项目类型: springboot/fatjar/static/nginx等',
    main_entry     VARCHAR(255) NULL                    COMMENT '主入口名称: 如 jar 文件名/二进制名/静态目录',
    start_script   TEXT         NULL                    COMMENT '启动脚本',
    stop_script    TEXT         NULL                    COMMENT '停止脚本',
    restart_script TEXT         NULL                    COMMENT '重启脚本',
    status         VARCHAR(16)  NULL DEFAULT 'unknown'   COMMENT '项目状态: running/stopped/unknown',
    project_path   VARCHAR(512) NULL                    COMMENT '项目地址',
    log_path       VARCHAR(512) NULL                    COMMENT '日志地址',
    log_mode       VARCHAR(16)  NULL DEFAULT 'append'   COMMENT '重启日志: append追加/truncate清空',
    description    VARCHAR(255) NULL                    COMMENT '描述',
    icon           VARCHAR(64)  NULL                    COMMENT '项目图标: 如 ep:cpu/ri:java-line',
    env_vars       TEXT         NULL                    COMMENT '环境变量JSON: [{"key":"K","value":"V"}]',
    cmd_args       TEXT         NULL                    COMMENT '命令参数JSON: ["--profile=prod"]',
    jvm_args       TEXT         NULL                    COMMENT 'JVM参数JSON: ["-Xmx512m"]',
    status_script  TEXT         NULL                    COMMENT '状态检测脚本',
    service_name   VARCHAR(128) NULL                    COMMENT '系统服务名(非空=已挂载)',
    autostart      TINYINT      NULL DEFAULT 0          COMMENT '开机自启',
    guardian       TINYINT      NULL DEFAULT 0          COMMENT '守护进程',
    enabled        TINYINT      NULL DEFAULT 1          COMMENT '是否启用(0=禁用)',
    pid            VARCHAR(32)  NULL                    COMMENT '运行时PID',
    app_config     TEXT         NULL                    COMMENT 'SpringBoot专属配置JSON',
    config_dir     VARCHAR(512) NULL                    COMMENT '分离式配置目录',
    lib_dir        VARCHAR(512) NULL                    COMMENT '分离式依赖(lib)目录',
    nginx_dir      VARCHAR(512) NULL                    COMMENT 'Nginx安装/配置目录',
    git_url        VARCHAR(512) NULL                    COMMENT 'Git仓库地址',
    git_branch     VARCHAR(128) NULL DEFAULT 'main'     COMMENT 'Git分支',
    git_auth_type  VARCHAR(16)  NULL DEFAULT 'none'     COMMENT 'Git认证: none/token',
    git_token      VARCHAR(512) NULL                    COMMENT 'Git访问令牌',
    git_build_goals VARCHAR(255) NULL DEFAULT 'clean,package' COMMENT 'Maven构建目标',
    git_skip_tests TINYINT      NULL DEFAULT 1          COMMENT '是否跳过测试',
    maven_home     VARCHAR(512) NULL                    COMMENT 'Maven安装路径(留空则自动检测/下载)',
    maven_version  VARCHAR(32)  NULL DEFAULT '3.9.9'    COMMENT 'Maven版本(自动下载时使用)',
    jdk_home       VARCHAR(512) NULL                    COMMENT 'JDK安装路径(留空则用PATH上的java)',
    upload_extract TINYINT      NULL DEFAULT 1          COMMENT '上传时是否自动解压(jar不解压;zip/tar解压;模型上传设0保留原文件)',
    upload_target_dir VARCHAR(255) NULL                 COMMENT '上传目标目录(相对项目地址,如models/onnx;jar类留空则投放到主入口)',
    deploy_type    VARCHAR(16)  NULL DEFAULT 'main'     COMMENT '分离式部署类型: main=仅主包; lib=仅依赖; both=全部',
    schedule_enabled TINYINT    NULL DEFAULT 0          COMMENT '是否启用定时发布(一次性)',
    schedule_time  DATETIME     NULL                    COMMENT '定时发布时间(一次性)',
    schedule_status VARCHAR(16) NULL DEFAULT 'NONE'     COMMENT '定时发布状态: NONE/PENDING/DONE/FAILED/CANCELED',
    git_poll_enabled TINYINT    NULL DEFAULT 0          COMMENT '是否启用Git定时拉取自动发布',
    git_poll_cron  VARCHAR(64)  NULL DEFAULT '0 */5 * * * ?' COMMENT 'Git轮询cron表达式',
    git_last_commit VARCHAR(64) NULL                    COMMENT '最近一次检测到的远端Git提交',
    running_since  DATETIME     NULL                    COMMENT '本次启动时间(运行时长基准)',
    create_by      INT          NULL                    COMMENT '创建人',
    create_name    VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time    DATETIME     NULL                    COMMENT '创建时间',
    update_name    VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by      INT          NULL                    COMMENT '更新人',
    update_time    DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_server_project_release (release_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目管理';

-- ---------------------------------------------------------------------
-- 14. 项目操作日志（原 V2.3__add_project_op_log.sql，已并入本文件）
-- 所有项目操作（创建/修改/删除/启动/停止/重启/升级/备份/回滚/Git发布/
-- 挂载系统服务等）都落一条记录，供「日志管理」按项目/操作/状态检索。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_project_op_log
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id      BIGINT       NULL                    COMMENT '项目ID',
    project_name    VARCHAR(128) NULL                    COMMENT '项目名称(冗余,项目删除后仍可读)',
    release_id      BIGINT       NULL                    COMMENT '发布ID',
    operation       VARCHAR(64)  NOT NULL                COMMENT '操作类型: create/update/delete/start/stop/restart/upgrade/backup/rollback/deploy/install-service 等',
    operation_label VARCHAR(64)  NULL                    COMMENT '操作中文名(展示用)',
    status          VARCHAR(16)  NOT NULL                COMMENT '状态: RUNNING/SUCCESS/FAILED',
    message         VARCHAR(2000) NULL                   COMMENT '结果说明或失败原因',
    task_id         VARCHAR(64)  NULL                    COMMENT '异步任务标识(同步操作为空)',
    operator_name   VARCHAR(64)  NULL                    COMMENT '触发人',
    start_time      DATETIME     NULL                    COMMENT '开始时间',
    end_time        DATETIME     NULL                    COMMENT '结束时间(RUNNING时为空)',
    duration_ms     BIGINT       NULL                    COMMENT '耗时(毫秒)',
    create_by       INT          NULL                    COMMENT '创建人',
    create_name     VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time     DATETIME     NULL                    COMMENT '创建时间',
    update_name     VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by       INT          NULL                    COMMENT '更新人',
    update_time     DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_op_log_project (project_id),
    KEY idx_op_log_release (release_id),
    KEY idx_op_log_task (task_id),
    KEY idx_op_log_time (start_time),
    KEY idx_op_log_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='发布管理-项目操作日志';

-- ---------------------------------------------------------------------
-- 15. 发布管理（原 V2.6 的 SkyWalking 列已内联）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_release (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    release_name VARCHAR(128) NOT NULL                COMMENT '发布名称',
    host_id      BIGINT       NULL                    COMMENT '主机ID',
    host_ip      VARCHAR(64)  NULL                    COMMENT '主机IP',
    description  VARCHAR(255) NULL                    COMMENT '描述',
    background_image VARCHAR(1000) NULL               COMMENT '背景图URL',
    sw_enabled   TINYINT      NULL                    COMMENT '是否启用 SkyWalking（0/1）',
    sw_jar_path   VARCHAR(512) NULL                   COMMENT 'agent jar 路径（目标主机）',
    sw_service_name_prefix VARCHAR(128) NULL           COMMENT 'service_name 前缀',
    sw_backend_service VARCHAR(128) NULL               COMMENT 'OAP 地址（collector.backend_service）',
    create_by    INT          NULL                    COMMENT '创建人',
    create_name  VARCHAR(64)  NULL                    COMMENT '创建人名称',
    create_time  DATETIME     NULL                    COMMENT '创建时间',
    update_name  VARCHAR(64)  NULL                    COMMENT '更新人名称',
    update_by    INT          NULL                    COMMENT '更新人',
    update_time  DATETIME     NULL                    COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_server_release_host (host_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发布管理';
