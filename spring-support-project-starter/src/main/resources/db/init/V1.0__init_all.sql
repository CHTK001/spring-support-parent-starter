-- ============================================================
-- 发布项目管理模块 DDL 初始化脚本 (spring-support-project-starter)
-- 适用数据库: MySQL 8.0+ / H2 (MODE=MySQL)
-- 说明: 全部建表/加列语句幂等 (IF NOT EXISTS)，可直接重复执行。
--       本文件为全量版：已合并历史增量 (V1.1 归档/日志/自动启动、V1.2 健康检查/自动回滚/重试/归档保留)。
--       表名前缀 ms_project_*，与 ms_server_host 资产台账通过主机 ID 关联。
-- @author CH
-- @since 4.0.0.42
-- ============================================================

-- 部署项目台账
CREATE TABLE IF NOT EXISTS ms_project (
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_project_name         VARCHAR(128) NOT NULL COMMENT '项目名称',
    ms_project_code         VARCHAR(128) NOT NULL COMMENT '项目编码',
    ms_project_type         VARCHAR(32)  NOT NULL COMMENT '项目类型: SPRINGBOOT_MONO/SPRINGBOOT_WAR/SPRINGBOOT_SPLIT/NODEJS/STATIC/CUSTOM',
    ms_project_host_ids     VARCHAR(1024) DEFAULT NULL COMMENT '目标服务器ID列表(JSON数组)',
    ms_project_host_os      VARCHAR(16)   DEFAULT NULL COMMENT '目标主机OS类型: LINUX/WINDOWS',
    ms_project_deploy_dir   VARCHAR(512) NOT NULL COMMENT '部署目录',
    ms_project_start_script TEXT COMMENT '启动脚本',
    ms_project_stop_script  TEXT COMMENT '停止脚本',
    ms_project_restart_script TEXT COMMENT '重启脚本',
    ms_project_log_dir      VARCHAR(512) DEFAULT NULL COMMENT '日志目录',
    ms_project_log_file     VARCHAR(256) DEFAULT NULL COMMENT '日志文件名(如 app.log)',
    ms_project_delete_before TINYINT      DEFAULT 0 COMMENT '升级前是否先删除旧内容: 0否 1是',
    ms_project_overwrite_mode VARCHAR(16) DEFAULT 'OVERWRITE' COMMENT '覆盖模式: OVERWRITE直接覆盖/REPLACE全量替换',
    ms_project_auto_extract TINYINT       DEFAULT 1 COMMENT '是否自动解压: 0否 1是',
    ms_project_auto_start   TINYINT       DEFAULT 0 COMMENT '部署成功后是否自动执行启动脚本: 0否 1是',
    ms_project_health_enabled TINYINT     DEFAULT 0 COMMENT '部署后健康检查: 0否 1是',
    ms_project_health_type  VARCHAR(16)   DEFAULT 'PORT' COMMENT '健康检查类型: PORT/HTTP/PROCESS',
    ms_project_health_target VARCHAR(512) DEFAULT NULL COMMENT '健康检查目标(端口/URL/进程)',
    ms_project_health_timeout INT         DEFAULT 30 COMMENT '健康检查超时(秒)',
    ms_project_auto_rollback TINYINT      DEFAULT 0 COMMENT '失败自动回滚: 0否 1是',
    ms_project_deploy_retry INT           DEFAULT 0 COMMENT '单台失败重试次数',
    ms_project_archive_keep INT           DEFAULT 0 COMMENT '归档保留数量(0不清理)',
    ms_project_current_version VARCHAR(256) DEFAULT NULL COMMENT '当前版本(最近一次部署包)',
    ms_project_runtime_status VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT '运行时状态: UNKNOWN/RUNNING/STOPPED/DEPLOYING/UPGRADING/RESTARTING/HEALTHY/UNHEALTHY',
    ms_project_enabled      TINYINT       DEFAULT 1 COMMENT '启用状态: 0停用 1启用',
    ms_project_sort         INT           DEFAULT 100 COMMENT '排序',
    ms_project_remark       VARCHAR(512)  DEFAULT NULL COMMENT '备注',
    create_by               INT           DEFAULT NULL COMMENT '创建人',
    create_name             VARCHAR(64)   DEFAULT NULL COMMENT '创建人姓名',
    create_time             DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name             VARCHAR(64)   DEFAULT NULL COMMENT '更新人姓名',
    update_by               INT           DEFAULT NULL COMMENT '更新人',
    update_time             DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (ms_project_code),
    KEY idx_project_name (ms_project_name),
    KEY idx_project_type (ms_project_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发布项目台账';

-- 部署日志
CREATE TABLE IF NOT EXISTS ms_project_deploy_log (
    id                              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_project_deploy_log_project_id BIGINT      NOT NULL COMMENT '关联项目ID',
    ms_project_deploy_log_project_name VARCHAR(128) DEFAULT NULL COMMENT '项目名称(冗余)',
    ms_project_deploy_log_host_id    BIGINT       DEFAULT NULL COMMENT '目标主机ID',
    ms_project_deploy_log_host_name  VARCHAR(128) DEFAULT NULL COMMENT '主机名称(冗余)',
    ms_project_deploy_log_action     VARCHAR(16)  NOT NULL COMMENT '操作类型: DEPLOY/UPGRADE/START/STOP/RESTART/ROLLBACK',
    ms_project_deploy_log_version    VARCHAR(256) DEFAULT NULL COMMENT '版本标识(部署包文件名)',
    ms_project_deploy_log_status     VARCHAR(16)  NOT NULL COMMENT '执行状态: SUCCESS/FAIL/RUNNING',
    ms_project_deploy_log_output     LONGTEXT     DEFAULT NULL COMMENT '执行输出',
    ms_project_deploy_log_error      TEXT         DEFAULT NULL COMMENT '错误信息',
    ms_project_deploy_log_start_time DATETIME     DEFAULT NULL COMMENT '开始时间',
    ms_project_deploy_log_end_time   DATETIME     DEFAULT NULL COMMENT '结束时间',
    ms_project_deploy_log_cost_ms    BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
    create_by                       INT           DEFAULT NULL COMMENT '创建人',
    create_name                     VARCHAR(64)   DEFAULT NULL COMMENT '创建人姓名',
    create_time                     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                     VARCHAR(64)   DEFAULT NULL COMMENT '更新人姓名',
    update_by                       INT           DEFAULT NULL COMMENT '更新人',
    update_time                     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_deploy_log_project (ms_project_deploy_log_project_id),
    KEY idx_deploy_log_host (ms_project_deploy_log_host_id),
    KEY idx_deploy_log_action (ms_project_deploy_log_action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署日志';

-- 部署包归档：打通「资产 → 部署包 → 部署目录」链路（版本追溯/回滚支撑）
CREATE TABLE IF NOT EXISTS ms_project_upload_archive (
    id                                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_project_upload_archive_project_id BIGINT      NOT NULL COMMENT '关联项目ID',
    ms_project_upload_archive_project_name VARCHAR(128) DEFAULT NULL COMMENT '项目名称(冗余)',
    ms_project_upload_archive_host_id    BIGINT       DEFAULT NULL COMMENT '目标主机ID(资产维度锚点)',
    ms_project_upload_archive_host_name  VARCHAR(128) DEFAULT NULL COMMENT '主机名称(冗余)',
    ms_project_upload_archive_action     VARCHAR(16)  NOT NULL COMMENT '操作类型: DEPLOY/UPGRADE/ROLLBACK',
    ms_project_upload_archive_version    VARCHAR(256) NOT NULL COMMENT '版本标识(原始包文件名)',
    ms_project_upload_archive_type       VARCHAR(32)  DEFAULT NULL COMMENT '包类型: SINGLE_FILE/COMPRESSED',
    ms_project_upload_archive_upload_path VARCHAR(512) DEFAULT NULL COMMENT '归档存储路径',
    ms_project_upload_archive_deploy_dir VARCHAR(512) DEFAULT NULL COMMENT '末端部署目录',
    ms_project_upload_archive_file_size  BIGINT       DEFAULT NULL COMMENT '包大小(字节)',
    ms_project_upload_archive_md5        VARCHAR(64)  DEFAULT NULL COMMENT '包MD5',
    ms_project_upload_archive_status     VARCHAR(16)  DEFAULT NULL COMMENT '部署结果: SUCCESS/FAIL',
    ms_project_upload_archive_deploy_time DATETIME     DEFAULT NULL COMMENT '部署时间',
    create_by                            INT          DEFAULT NULL COMMENT '创建人',
    create_name                          VARCHAR(64)  DEFAULT NULL COMMENT '创建人姓名',
    create_time                          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                          VARCHAR(64)  DEFAULT NULL COMMENT '更新人姓名',
    update_by                            INT          DEFAULT NULL COMMENT '更新人',
    update_time                          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_archive_project (ms_project_upload_archive_project_id),
    KEY idx_archive_host (ms_project_upload_archive_host_id),
    KEY idx_archive_version (ms_project_upload_archive_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署包归档';

-- ============================================================
-- 兼容历史库的增量加列（幂等，新库以上建表已含全部列）
-- ============================================================
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_log_dir VARCHAR(512) DEFAULT NULL COMMENT '日志目录' AFTER ms_project_restart_script;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_log_file VARCHAR(256) DEFAULT NULL COMMENT '日志文件名(如 app.log)' AFTER ms_project_log_dir;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_auto_start TINYINT DEFAULT 0 COMMENT '部署成功后是否自动执行启动脚本: 0否 1是' AFTER ms_project_auto_extract;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_enabled TINYINT DEFAULT 0 COMMENT '部署后健康检查: 0否 1是' AFTER ms_project_auto_start;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_type VARCHAR(16) DEFAULT 'PORT' COMMENT '健康检查类型: PORT/HTTP/PROCESS' AFTER ms_project_health_enabled;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_target VARCHAR(512) DEFAULT NULL COMMENT '健康检查目标(端口/URL/进程)' AFTER ms_project_health_type;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_timeout INT DEFAULT 30 COMMENT '健康检查超时(秒)' AFTER ms_project_health_target;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_auto_rollback TINYINT DEFAULT 0 COMMENT '失败自动回滚: 0否 1是' AFTER ms_project_health_timeout;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_deploy_retry INT DEFAULT 0 COMMENT '单台失败重试次数' AFTER ms_project_auto_rollback;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_archive_keep INT DEFAULT 0 COMMENT '归档保留数量(0不清理)' AFTER ms_project_deploy_retry;
-- 守护进程（持续存活检测 + 自动拉起）+ 自定义状态检测脚本
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_watchdog_enabled TINYINT DEFAULT 0 COMMENT '守护进程开关: 0关闭 1开启(持续检测+挂了自动拉起)' AFTER ms_project_archive_keep;

ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_watchdog_interval INT DEFAULT 30 COMMENT '守护检测间隔(秒,每项目独立设置,最小5秒)' AFTER ms_project_watchdog_enabled;

ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_watchdog_max_fail INT DEFAULT 1 COMMENT '守护连续失败阈值: 连续N次不健康才自动重启(0=仅告警)' AFTER ms_project_watchdog_interval;

ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_check_script TEXT DEFAULT NULL COMMENT '自定义状态检测脚本(exit 0=健康,为空时回退健康检查类型探测)' AFTER ms_project_watchdog_max_fail;


-- 部署前置门禁 + 项目级指标采集（SSH 轮询，零 Agent）
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_preflight_gate TINYINT DEFAULT 0 COMMENT '部署前置门禁: 部署前校验主机在线/延迟,不可达直接拦截' AFTER ms_project_check_script;

ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_metric_enabled TINYINT DEFAULT 0 COMMENT '指标采集开关: 0关闭 1开启(SSH轮询采集CPU/内存/磁盘)';

ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_metric_interval INT DEFAULT 60 COMMENT '指标采集间隔秒(最小10秒)';

ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_metric_host_ids VARCHAR(512) COMMENT '指标采集目标主机ID集合(JSON数组,空=项目绑定主机)';

