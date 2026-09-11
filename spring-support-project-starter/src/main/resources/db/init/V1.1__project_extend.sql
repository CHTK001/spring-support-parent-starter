-- ============================================================
-- 发布项目管理模块 DDL 增量迁移 (spring-support-project-starter)
-- 适用数据库: MySQL 8.0+ / H2 (MODE=MySQL)
-- 内容: 部署包归档表(资产维度版本追溯/回滚支撑) + 项目扩展列(自动启动/日志目录)
-- @author CH
-- @since 4.0.0.42
-- ============================================================

-- 部署包归档表：打通「资产 → 部署包 → 部署目录」链路
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

-- 项目扩展列：部署后自动启动 / 实时日志目录与文件名
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_auto_start TINYINT DEFAULT 0 COMMENT '部署成功后是否自动执行启动脚本: 0否 1是' AFTER ms_project_auto_extract;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_log_dir VARCHAR(512) DEFAULT NULL COMMENT '日志目录' AFTER ms_project_restart_script;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_log_file VARCHAR(256) DEFAULT NULL COMMENT '日志文件名(如 app.log)' AFTER ms_project_log_dir;