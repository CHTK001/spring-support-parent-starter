-- =====================================================================
-- V1.0__init_all.sql - spring-support-job-starter 全量 DDL
-- 通道: INIT 同步建表（sys_job 任务主表 / sys_job_log 日志表 / sys_job_cluster 集群表）。
-- 原脚本位于 db/migration/V1.0__init_job.sql，按"一个文件原则"收敛至 db/init。
-- =====================================================================

-- =====================================================
-- sys_job 任务主表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_job (
    sys_job_id BIGINT PRIMARY KEY COMMENT '任务标识 (主键)',
    sys_job_no VARCHAR(64) COMMENT '业务编号 (非主键, 对外唯一)',
    sys_job_name VARCHAR(255) COMMENT '任务名',
    sys_job_author VARCHAR(255) COMMENT '任务负责人',
    sys_job_alarm_email VARCHAR(512) COMMENT '告警邮箱 (多个用逗号分隔)',
    sys_job_group VARCHAR(128) COMMENT '命名空间 (隔离多项目)',
    sys_job_schedule_type VARCHAR(32) COMMENT '调度类型 (CRON / FIXED / FIXED_MS / 延迟 / AT / 无)',
    sys_job_schedule_time VARCHAR(255) COMMENT '调度配置 (cron 表达式或毫秒间隔)',
    sys_job_trigger_status INT DEFAULT 0 COMMENT '启用状态 0=停止 1=运行',
    sys_job_desc VARCHAR(512) COMMENT '任务描述',
    sys_job_glue_updatetime TIMESTAMP NULL COMMENT '胶水最后更新时间 (留扩展)',
    sys_job_glue_source TEXT COMMENT '胶水源码 (留扩展)',
    sys_job_glue_type VARCHAR(64) COMMENT '胶水类型 (Bean / 胶水_xxx)',
    sys_job_dispatch_mode VARCHAR(32) COMMENT '分发模式 (本地 / 远程)',
    sys_job_remote_executor_address VARCHAR(512) COMMENT '远程执行器地址覆盖',
    sys_job_storage_mode VARCHAR(32) COMMENT '日志存储模式 (DATABASE / 文件)',
    sys_job_fail_retry INT DEFAULT 0 COMMENT '失败重试次数',
    sys_job_retry_interval INT DEFAULT 0 COMMENT '重试间隔 (秒)',
    sys_job_execute_timeout INT DEFAULT 0 COMMENT '执行超时 (秒)',
    sys_job_execute_bean VARCHAR(255) COMMENT '执行器处理器名称',
    sys_job_execute_param TEXT COMMENT '执行参数',
    sys_job_exception_callback_bean VARCHAR(255) COMMENT '异常回调处理器',
    sys_job_retry_callback_bean VARCHAR(255) COMMENT '重试前回调处理器',
    sys_job_execute_misfire_strategy VARCHAR(64) COMMENT '错失触发策略',
    sys_job_trigger_last_time BIGINT COMMENT '上次触发时间 (毫秒)',
    sys_job_trigger_next_time BIGINT COMMENT '下次触发时间 (毫秒)',
    sys_job_is_deleted INT DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
    sys_job_version BIGINT DEFAULT 0 COMMENT '乐观锁版本',
    create_by INT COMMENT '创建人ID',
    create_name VARCHAR(64) COMMENT '创建人名称',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by INT COMMENT '更新人ID',
    update_name VARCHAR(64) COMMENT '更新人名称',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='任务主表';

-- =====================================================
-- sys_job_log 任务日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_job_log (
    sys_job_log_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    sys_job_id BIGINT COMMENT '任务ID',
    sys_job_no VARCHAR(64) COMMENT '任务编号',
    sys_job_name VARCHAR(255) COMMENT '任务名称',
    sys_job_group VARCHAR(128) COMMENT '任务分组',
    sys_job_trigger_time TIMESTAMP NULL COMMENT '触发时间',
    sys_job_handle_time TIMESTAMP NULL COMMENT '处理时间',
    sys_job_status INT DEFAULT 0 COMMENT '执行状态 0=失败 1=成功',
    sys_job_code VARCHAR(64) COMMENT '执行结果码',
    sys_job_msg TEXT COMMENT '执行结果消息',
    sys_job_trigger_msg TEXT COMMENT '触发参数',
    sys_job_handle_msg TEXT COMMENT '执行日志',
    sys_job_alarm_status INT DEFAULT 0 COMMENT '告警状态 0=不需要 1=已告警',
    create_by INT COMMENT '创建人ID',
    create_name VARCHAR(64) COMMENT '创建人名称',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='任务日志表';

-- =====================================================
-- sys_job_cluster 任务集群表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_job_cluster (
    sys_job_cluster_id VARCHAR(64) PRIMARY KEY COMMENT '集群ID',
    sys_job_cluster_leader_node VARCHAR(255) COMMENT 'Leader节点',
    sys_job_cluster_lease_until_ms BIGINT COMMENT '租约到期时间 (毫秒)',
    sys_job_cluster_version INT DEFAULT 0 COMMENT '版本号',
    create_by INT COMMENT '创建人ID',
    create_name VARCHAR(64) COMMENT '创建人名称',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by INT COMMENT '更新人ID',
    update_name VARCHAR(64) COMMENT '更新人名称',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='任务集群表';
