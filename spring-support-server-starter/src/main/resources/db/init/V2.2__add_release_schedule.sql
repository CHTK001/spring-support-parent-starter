-- ---------------------------------------------------------------------
-- V2.2: 发布管理增强 - 定时发布 / Git 轮询 / 上传自动升级 - 存量库补列
-- 新库由 V2.0 全量建表覆盖，本文件仅对已有库增量补列。
-- flyway 版本表保证只执行一次。
-- ---------------------------------------------------------------------

ALTER TABLE ms_server_project
    ADD COLUMN upload_auto_upgrade TINYINT NULL DEFAULT 0          COMMENT '上传完成后是否自动升级(0=只上传,1=上传后升级)' AFTER deploy_type,
    ADD COLUMN schedule_enabled TINYINT    NULL DEFAULT 0          COMMENT '是否启用定时发布(一次性)' AFTER upload_auto_upgrade,
    ADD COLUMN schedule_time    DATETIME   NULL                    COMMENT '定时发布时间(一次性)' AFTER schedule_enabled,
    ADD COLUMN schedule_status  VARCHAR(16) NULL DEFAULT 'NONE'    COMMENT '定时发布状态: NONE/PENDING/DONE/FAILED/CANCELED' AFTER schedule_time,
    ADD COLUMN git_poll_enabled TINYINT    NULL DEFAULT 0          COMMENT '是否启用Git定时拉取自动发布' AFTER schedule_status,
    ADD COLUMN git_poll_cron    VARCHAR(64) NULL DEFAULT '0 */5 * * * ?' COMMENT 'Git轮询cron表达式' AFTER git_poll_enabled,
    ADD COLUMN git_last_commit  VARCHAR(64) NULL                   COMMENT '最近一次检测到的远端Git提交' AFTER git_poll_cron;
