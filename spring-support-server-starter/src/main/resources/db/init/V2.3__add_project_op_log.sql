-- ---------------------------------------------------------------------
-- V2.3: 发布管理增强 - 项目操作日志
-- 所有项目操作（创建/修改/删除/启动/停止/重启/升级/备份/回滚/Git发布/
-- 挂载系统服务等）都落一条记录，供「日志管理」按项目/操作/状态检索。
-- 使用 IF NOT EXISTS，重复执行无副作用。
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ms_server_project_op_log
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
    PRIMARY KEY (id),
    KEY idx_op_log_project (project_id),
    KEY idx_op_log_release (release_id),
    KEY idx_op_log_task (task_id),
    KEY idx_op_log_time (start_time),
    KEY idx_op_log_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='发布管理-项目操作日志';
