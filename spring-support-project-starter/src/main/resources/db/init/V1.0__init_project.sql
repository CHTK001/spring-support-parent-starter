-- ============================================================
-- 发布项目管理模块 DDL 初始化脚本 (spring-support-project-starter)
-- 适用数据库: MySQL 8.0+ / H2 (MODE=MySQL)
-- 说明: 全部建表语句幂等 (IF NOT EXISTS)，可直接重复执行。
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
    ms_project_delete_before TINYINT      DEFAULT 0 COMMENT '升级前是否先删除旧内容: 0否 1是',
    ms_project_overwrite_mode VARCHAR(16) DEFAULT 'OVERWRITE' COMMENT '覆盖模式: OVERWRITE直接覆盖/REPLACE全量替换',
    ms_project_auto_extract TINYINT       DEFAULT 1 COMMENT '是否自动解压: 0否 1是',
    ms_project_current_version VARCHAR(256) DEFAULT NULL COMMENT '当前版本(最近一次部署包)',
    ms_project_runtime_status VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT '运行时状态: UNKNOWN/RUNNING/STOPPED/DEPLOYING/UPGRADING/RESTARTING',
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
    ms_project_deploy_log_action     VARCHAR(16)  NOT NULL COMMENT '操作类型: DEPLOY/UPGRADE/START/STOP/RESTART',
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
