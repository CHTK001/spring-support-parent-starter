-- =====================================================================
-- V1.0__init_all.sql - datasource-starter 全量 DDL（合并自全部 init_* 文件）
-- 通道: INIT 同步建表；老库缺列/缺表由 information_schema + PREPARE 段幂等补齐；
-- 种子数据见 V1.0__initdata_all.sql（虚拟线程异步，不阻塞启动）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 来源: V1.0.0__init_datasync_mapping.sql
-- ---------------------------------------------------------------------
-- DataSync 数据库模式：datasync_mapping 映射主表
CREATE TABLE IF NOT EXISTS datasync_mapping (
 datasync_mapping_id VARCHAR(255) PRIMARY KEY,
 datasync_mapping_input_id VARCHAR(255),
 datasync_mapping_source_id VARCHAR(255),
 datasync_mapping_output_id VARCHAR(255),
 datasync_mapping_sink_id VARCHAR(255),
 datasync_mapping_batch INT DEFAULT 100,
 datasync_mapping_cron_type VARCHAR(255),
 datasync_mapping_cron VARCHAR(255),
 datasync_mapping_params TEXT,
 datasync_mapping_enabled BOOLEAN DEFAULT TRUE,
 datasync_mapping_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 datasync_mapping_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

















-- DataSync 数据库模式：datasync_field_mapping 字段映射表
CREATE TABLE IF NOT EXISTS datasync_field_mapping (
 datasync_field_mapping_id VARCHAR(255) PRIMARY KEY,
 datasync_field_mapping_mapping_id VARCHAR(255),
 datasync_field_mapping_source_field VARCHAR(255),
 datasync_field_mapping_target_field VARCHAR(255),
 datasync_field_mapping_converter VARCHAR(255),
 datasync_field_mapping_sort INT,
 FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES datasync_mapping(datasync_mapping_id)
);


















-- ---------------------------------------------------------------------
-- 来源: V1.0.0__init_spider.sql
-- ---------------------------------------------------------------------
-- =====================================================
-- 爬虫中心建表脚本（V1.0.0）
-- 由 spring-support-datasource-starter 自动扫描执行
-- 命名规范：V{版本}__init_{描述}.sql
-- @author CH
-- =====================================================

-- -----------------------------------------------------
-- 1. 爬虫定义表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_definition` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
 `code` VARCHAR(64) NOT NULL COMMENT '爬虫唯一编码',
 `name` VARCHAR(128) NOT NULL COMMENT '爬虫名称',
 `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
 `flow_id` BIGINT DEFAULT NULL COMMENT '关联流程 ID（spider_flow.id）',
 `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用（1 启用 / 0 停用）',
 `cron_expression` VARCHAR(64) DEFAULT NULL COMMENT '定时 cron 表达式，为空则不自动调度',
 `executor_type` VARCHAR(16) DEFAULT NULL COMMENT '执行类型（ONCE 一次 / SCHEDULE 定时）',
 `last_run_at` DATETIME DEFAULT NULL COMMENT '上次执行时间',
 `last_status` VARCHAR(16) DEFAULT NULL COMMENT '上次执行状态（SUCCESS/FAIL/RUNNING）',
 `last_message` VARCHAR(512) DEFAULT NULL COMMENT '上次执行消息',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` BIGINT DEFAULT NULL COMMENT '创建人 ID',
 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` BIGINT DEFAULT NULL COMMENT '更新人 ID',
 `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `sys_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
 PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT '爬虫定义表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_code ON spider_definition (`code`);
CREATE INDEX IF NOT EXISTS idx_enabled ON spider_definition (`enabled`);
CREATE INDEX IF NOT EXISTS idx_flow_id ON spider_definition (`flow_id`);











-- -----------------------------------------------------
-- 2. 流程编排表（前端 ReFlow 导出的 JSON）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_flow` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
 `definition_id` BIGINT NOT NULL COMMENT '关联爬虫定义 ID',
 `name` VARCHAR(128) DEFAULT NULL COMMENT '流程名称',
 `nodes_json` TEXT COMMENT '节点 JSON（ReFlow 导出）',
 `edges_json` TEXT COMMENT '连线 JSON（ReFlow 导出）',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` BIGINT DEFAULT NULL COMMENT '创建人 ID',
 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` BIGINT DEFAULT NULL COMMENT '更新人 ID',
 `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `sys_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
 PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT '流程编排表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_definition_id ON spider_flow (`definition_id`);
CREATE INDEX IF NOT EXISTS idx_name ON spider_flow (`name`);











-- -----------------------------------------------------
-- 3. 采集数据记录表（output 节点写入）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_data_record` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
 `execution_no` VARCHAR(64) NOT NULL COMMENT '执行号（traceId）',
 `definition_code` VARCHAR(64) NOT NULL COMMENT '爬虫编码',
 `title` VARCHAR(256) DEFAULT NULL COMMENT '标题',
 `content` MEDIUMTEXT DEFAULT NULL COMMENT '内容',
 `source_url` VARCHAR(512) DEFAULT NULL COMMENT '来源 URL',
 `raw_data` JSON NOT NULL COMMENT '原始采集数据（JSON）',
 `status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态（SUCCESS/FAIL）',
 `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败信息',
 `record_time` DATETIME DEFAULT NULL COMMENT '记录时间',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` BIGINT DEFAULT NULL COMMENT '创建人 ID',
 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` BIGINT DEFAULT NULL COMMENT '更新人 ID',
 `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `sys_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
 PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT '采集数据记录表';

CREATE INDEX IF NOT EXISTS idx_execution_no ON spider_data_record (`execution_no`);
CREATE INDEX IF NOT EXISTS idx_definition_code ON spider_data_record (`definition_code`);
CREATE INDEX IF NOT EXISTS idx_create_time ON spider_data_record (`create_time`);











-- -----------------------------------------------------
-- 4. 执行日志表（节点级，串联 traceId）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_execution_log` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
 `execution_no` VARCHAR(64) NOT NULL COMMENT '执行号（traceId）',
 `definition_code` VARCHAR(64) NOT NULL COMMENT '爬虫编码',
 `flow_id` BIGINT DEFAULT NULL COMMENT '流程 ID',
 `node_id` VARCHAR(64) DEFAULT NULL COMMENT '节点 ID',
 `node_name` VARCHAR(128) DEFAULT NULL COMMENT '节点名称',
 `node_type` VARCHAR(64) DEFAULT NULL COMMENT '节点类型',
 `status` VARCHAR(16) NOT NULL COMMENT '状态（RUNNING/SUCCESS/FAIL）',
 `input_data` TEXT DEFAULT NULL COMMENT '节点输入数据（JSON）',
 `output_data` TEXT DEFAULT NULL COMMENT '节点输出数据（JSON）',
 `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败信息',
 `start_at` DATETIME DEFAULT NULL COMMENT '节点开始时间',
 `end_at` DATETIME DEFAULT NULL COMMENT '节点结束时间',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` BIGINT DEFAULT NULL COMMENT '创建人 ID',
 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` BIGINT DEFAULT NULL COMMENT '更新人 ID',
 `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `sys_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
 PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT '执行日志表';

CREATE INDEX IF NOT EXISTS idx_execution_no ON spider_execution_log (`execution_no`);
CREATE INDEX IF NOT EXISTS idx_definition_code ON spider_execution_log (`definition_code`);
CREATE INDEX IF NOT EXISTS idx_status ON spider_execution_log (`status`);











-- -----------------------------------------------------
-- 5. 环境变量表（{{env.KEY}} 插值）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_env` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
 `env_key` VARCHAR(128) NOT NULL COMMENT '变量名（如 COOKIE）',
 `env_value` VARCHAR(2048) DEFAULT NULL COMMENT '变量值',
 `description` VARCHAR(256) DEFAULT NULL COMMENT '描述',
 `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（1 启用 / 0 停用）',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` BIGINT DEFAULT NULL COMMENT '创建人 ID',
 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` BIGINT DEFAULT NULL COMMENT '更新人 ID',
 `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `sys_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
 PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT '环境变量表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_env_key ON spider_env (`env_key`);












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_device.sql
-- ---------------------------------------------------------------------
-- ============================================
-- 设备管理模块数据库初始化脚本
-- 模块名：spring-support-device-starter
-- 作者：CH
-- 说明：包含设备组织、信息、通道、预置位、事件记录、门禁六张表
-- 表名规则：所有表均以 device_ 为前缀
-- 字段规则：所有字段均以 表名_ 为前缀，避免冲突
-- ============================================

-- 设备组织表（树形结构，按厂商隔离）
CREATE TABLE IF NOT EXISTS device_organization (
 device_organization_id VARCHAR(32) PRIMARY KEY COMMENT '组织主键 ID',
 device_organization_parent_id VARCHAR(32) COMMENT '父级组织 ID',
 device_organization_name VARCHAR(100) NOT NULL COMMENT '组织名称',
 device_organization_path VARCHAR(500) COMMENT '路径，例 1/2/3',
 device_organization_vendor VARCHAR(20) COMMENT '所属厂商，例如 hikvision、dahua'
) COMMENT '设备组织树';

















-- 设备信息表
CREATE TABLE IF NOT EXISTS device_info (
 device_info_id VARCHAR(32) PRIMARY KEY COMMENT '设备主键 ID',
 device_info_org_id VARCHAR(32) COMMENT '所属组织 ID',
 device_info_name VARCHAR(100) COMMENT '设备名称',
 device_info_vendor VARCHAR(20) COMMENT '厂商标识，例如 hikvision、dahua',
 device_info_model VARCHAR(50) COMMENT '设备型号',
 device_info_category VARCHAR(30) COMMENT '设备大类：camera、accessControl、alarm、intercom',
 device_info_sub_category VARCHAR(30) COMMENT '设备小类：bullet、dome、ptz、cardReader、fingerprint、panicButton',
 device_info_ip VARCHAR(15) COMMENT 'IP 地址',
 device_info_port INT COMMENT '端口',
 device_info_username VARCHAR(50) COMMENT '用户名',
 device_info_password VARCHAR(100) COMMENT '密码（加密存储）',
 device_info_online TINYINT DEFAULT 0 COMMENT '是否在线：0 离线、1 在线',
 -- 地图位置与扩展属性（并入自 V1.1__add_channel_and_location.sql）
 device_info_latitude DECIMAL(10, 6) COMMENT '纬度（地图位置）',
 device_info_longitude DECIMAL(10, 6) COMMENT '经度（地图位置）',
 device_info_address VARCHAR(255) COMMENT '详细地址',
 device_info_serial VARCHAR(64) COMMENT '设备序列号',
 device_info_firmware VARCHAR(32) COMMENT '固件版本',
 device_info_channel_count INT DEFAULT 0 COMMENT '通道数量',
 device_info_offline_threshold INT DEFAULT 60 COMMENT '离线检测阈值（秒）',
 device_info_updated_at DATETIME COMMENT '更新时间',
 device_info_created_at DATETIME COMMENT '创建时间'
) COMMENT '设备基本信息';

















-- 设备通道表
CREATE TABLE IF NOT EXISTS device_channel (
 device_channel_id VARCHAR(32) PRIMARY KEY COMMENT '通道主键 ID',
 device_channel_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
 device_channel_no INT NOT NULL COMMENT '通道号',
 device_channel_name VARCHAR(100) COMMENT '通道名称',
 device_channel_type VARCHAR(20) COMMENT '通道类型：video 视频、door 门禁',
 -- 通道状态与排序（并入自 V1.1__add_channel_and_location.sql）
 device_channel_status VARCHAR(20) DEFAULT 'enabled' COMMENT '通道状态：enabled、disabled',
 device_channel_sort INT DEFAULT 0 COMMENT '排序'
) COMMENT '设备通道';

















-- 设备预置位表
CREATE TABLE IF NOT EXISTS device_preset (
 device_preset_id VARCHAR(32) PRIMARY KEY COMMENT '预置位主键 ID',
 device_preset_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
 device_preset_no INT NOT NULL COMMENT '预置位编号',
 device_preset_name VARCHAR(100) COMMENT '预置位名称'
) COMMENT '设备云台预置位';

















-- 设备事件记录表
CREATE TABLE IF NOT EXISTS device_event_record (
 device_event_record_id VARCHAR(32) PRIMARY KEY COMMENT '事件记录主键 ID',
 device_event_record_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
 device_event_record_channel_no INT COMMENT '通道号',
 device_event_record_event_type VARCHAR(30) COMMENT '事件类型：MOTION、IO_ALARM、ACCESS、TEMP、NETWORK、OTHER',
 device_event_record_detail TEXT COMMENT '事件详情',
 device_event_record_occurred_at DATETIME COMMENT '发生时间'
) COMMENT '设备事件记录';

















-- 设备门禁表
CREATE TABLE IF NOT EXISTS device_door (
 device_door_id VARCHAR(32) PRIMARY KEY COMMENT '门禁主键 ID',
 device_door_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
 device_door_no INT NOT NULL COMMENT '门编号',
 device_door_name VARCHAR(100) COMMENT '门名称',
 device_door_status VARCHAR(20) COMMENT '门状态：locked 已锁、unlocked 已开'
) COMMENT '设备门禁';

















-- 设备后台定时任务状态表（并入自 V1.1__initdata_device.sql 的前置建表依赖）
CREATE TABLE IF NOT EXISTS device_task_state (
 device_task_state_id VARCHAR(32) PRIMARY KEY COMMENT '任务状态主键 ID',
 device_task_state_name VARCHAR(50) NOT NULL COMMENT '任务名称（唯一标识）',
 device_task_state_running TINYINT DEFAULT 0 COMMENT '是否正在运行：0 停止、1 运行中',
 device_task_state_interval INT DEFAULT 30 COMMENT '轮询间隔（秒）',
 device_task_state_description VARCHAR(200) COMMENT '任务说明',
 device_task_state_updated_at DATETIME COMMENT '最后更新时间'
) COMMENT '设备后台定时任务状态';

CREATE INDEX IF NOT EXISTS idx_device_info_org ON device_info(device_info_org_id);
CREATE INDEX IF NOT EXISTS idx_device_info_category ON device_info(device_info_category);
CREATE INDEX IF NOT EXISTS idx_device_channel_device ON device_channel(device_channel_device_id);
CREATE INDEX IF NOT EXISTS idx_device_event_device ON device_event_record(device_event_record_device_id);
CREATE INDEX IF NOT EXISTS idx_device_event_time ON device_event_record(device_event_record_occurred_at);
CREATE INDEX IF NOT EXISTS idx_device_door_device ON device_door(device_door_device_id);
CREATE INDEX IF NOT EXISTS idx_device_org_parent ON device_organization(device_organization_parent_id);
CREATE INDEX IF NOT EXISTS idx_device_info_location ON device_info(device_info_latitude, device_info_longitude);
CREATE INDEX IF NOT EXISTS idx_device_task_state_name ON device_task_state(device_task_state_name);












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_job.sql
-- ---------------------------------------------------------------------
/*
  V1.0__init_job.sql

  任务调度模块数据库初始化脚本 (单一脚本)

  设计原则:
    1. 字段全部以表名为前缀
    2. 仅使用三库通用语法 (MySQL 8 / PostgreSQL 12 / Oracle 12c), 不使用方言专属关键字:
       - 列/表注释统一使用 "--" 行注释, 不使用 MySQL 内联 COMMENT 或 PG/Oracle 的 COMMENT ON
       - 64 位整型统一使用 NUMERIC(19, 0), 兼容 Oracle NUMBER
       - 时间类型统一使用 TIMESTAMP / DATE, 不使用 MySQL 专属 DATETIME
       - 大文本统一使用 VARCHAR(4000), 兼容 Oracle VARCHAR2 上限
    3. 主键由 ORM 层 (MyBatis-Plus) 生成, 不使用 AUTO_INCREMENT / 序列
    4. 数据库方言差异 (分页/类型) 由 spring-support-mybatis-starter 内部自动适配,
       本脚本仅需保证三库自兼容即可

  包含表:
    - sys_job:          任务主表
    - sys_job_log:      任务执行日志表
    - sys_job_cluster:  集群选主表

  切库注意事项:
    - 脚本由 spring-support-datasource-starter 的 FlywayLikePopulator 执行,
      采用 Spring ScriptUtils 原样执行, 不区分方言, 故本文件必须三库自兼容
    - Oracle 中 DEFAULT 子句不支持函数表达式, 故时间字段不设置数据库默认值,
      由 ORM 审计字段自动填充
*/

-- =====================================================
-- 1. 任务主表 (sys_job)
-- 用途: 保存任务定义、调度配置与触发时间状态
-- =====================================================
CREATE TABLE sys_job (
    -- 主键: 任务 ID
    sys_job_id                       NUMERIC(19, 0) NOT NULL,
    -- 业务编号: 非主键, 对外唯一
    sys_job_no                       VARCHAR(64)    NOT NULL,
    -- 任务名
    sys_job_name                     VARCHAR(255),
    -- 任务负责人: 留扩展, 便于告警通知
    sys_job_author                   VARCHAR(64),
    -- 告警邮箱: 多个用逗号分隔
    sys_job_alarm_email              VARCHAR(255),
    -- 命名空间: 隔离多项目
    sys_job_group                    VARCHAR(64)    DEFAULT 'default',
    -- 调度类型: CRON / FIXED / FIXED_MS / DELAY / AT / NONE
    sys_job_schedule_type            VARCHAR(50)    DEFAULT 'CRON',
    -- 调度配置: cron 表达式或毫秒间隔
    sys_job_schedule_time            VARCHAR(255),
    -- 调度状态: 0-停止 1-运行
    sys_job_trigger_status           INT            DEFAULT 0,
    -- 任务描述
    sys_job_desc                     VARCHAR(500),
    -- GLUE 更新时间: 留扩展
    sys_job_glue_updatetime          TIMESTAMP,
    -- GLUE 源码: 留扩展, 统一 VARCHAR(4000) 以兼容 Oracle
    sys_job_glue_source              VARCHAR(4000),
    -- GLUE 类型: BEAN / GLUE_xxx
    sys_job_glue_type                VARCHAR(50)    DEFAULT 'BEAN',
    -- 分发模式: LOCAL / REMOTE
    sys_job_dispatch_mode            VARCHAR(20)    DEFAULT 'LOCAL',
    -- 远程执行器地址覆盖
    sys_job_remote_executor_address  VARCHAR(500),
    -- 日志存储模式: DATABASE / FILE
    sys_job_storage_mode             VARCHAR(20)    DEFAULT 'DATABASE',
    -- 失败重试次数
    sys_job_fail_retry               INT            DEFAULT 3,
    -- 重试间隔: 秒
    sys_job_retry_interval           INT            DEFAULT 0,
    -- 执行超时: 秒
    sys_job_execute_timeout          INT            DEFAULT 0,
    -- 执行器 Handler 名称
    sys_job_execute_bean             VARCHAR(255),
    -- 执行参数
    sys_job_execute_param            VARCHAR(512),
    -- 异常回调 Handler
    sys_job_exception_callback_bean  VARCHAR(255),
    -- 重试前回调 Handler
    sys_job_retry_callback_bean      VARCHAR(255),
    -- 错失触发策略
    sys_job_execute_misfire_strategy VARCHAR(50)    DEFAULT 'DO_NOTHING',
    -- 上次触发时间: 毫秒时间戳
    sys_job_trigger_last_time        NUMERIC(19, 0) DEFAULT 0,
    -- 下次触发时间: 毫秒时间戳
    sys_job_trigger_next_time        NUMERIC(19, 0) DEFAULT 0,
    -- 逻辑删除: 0-未删 1-已删
    sys_job_is_deleted               INT            DEFAULT 0,
    -- 乐观锁版本
    sys_job_version                  NUMERIC(19, 0) DEFAULT 0,

    -- 通用审计字段 (由 ORM 审计填充, 参考 SysBase)
    create_name                      VARCHAR(64),
    create_by                        INT,
    create_time                      TIMESTAMP,
    update_name                      VARCHAR(64),
    update_by                        INT,
    update_time                      TIMESTAMP,

    PRIMARY KEY (sys_job_id),
    CONSTRAINT uk_sys_job_no UNIQUE (sys_job_no)
);
CREATE INDEX IF NOT EXISTS idx_sys_job_trigger_status ON sys_job (sys_job_trigger_status);
CREATE INDEX IF NOT EXISTS idx_sys_job_trigger_next_time ON sys_job (sys_job_trigger_next_time);











-- =====================================================
-- 2. 任务执行日志表 (sys_job_log)
-- 用途: 记录任务触发与执行结果, 供运维排查与统计
-- =====================================================
CREATE TABLE sys_job_log (
 -- 主键: 日志 ID
 sys_job_log_id NUMERIC(19, 0) NOT NULL,
 -- 日志编号: 非主键, 对外唯一
 sys_job_log_no VARCHAR(64) NOT NULL,
 -- 任务 ID
 sys_job_id NUMERIC(19, 0),
 -- 任务编号
 sys_job_no VARCHAR(64),
 -- 触发应用
 sys_job_log_app VARCHAR(255),
 -- Handler 名称
 sys_job_log_trigger_bean VARCHAR(255),
 -- 触发类型
 sys_job_log_trigger_type VARCHAR(50),
 -- profile
 sys_job_log_profile VARCHAR(50),
 -- 触发时间
 sys_job_log_trigger_time TIMESTAMP,
 -- 触发日期
 sys_job_log_trigger_date DATE,
 -- 触发状态码
 sys_job_log_trigger_code VARCHAR(20),
 -- 执行状态码
 sys_job_log_execute_code VARCHAR(20) DEFAULT 'PADDING',
 -- 耗时: 毫秒
 sys_job_log_cost DECIMAL(10, 2),
 -- 消息: 统一 VARCHAR(4000) 以兼容 Oracle
 sys_job_log_trigger_msg VARCHAR(4000),
 -- 参数
 sys_job_log_trigger_param VARCHAR(512),
 -- 地址
 sys_job_log_trigger_address VARCHAR(255),
 -- 节点 ID
 sys_job_log_node_id VARCHAR(64),
 -- 日志文件
 sys_job_log_file_path VARCHAR(500),
 -- 逻辑删除
 sys_job_log_is_deleted INT DEFAULT 0,

 -- 通用审计字段 (由 ORM 审计填充, 参考 SysBase)
 create_name VARCHAR(64),
 create_by INT,
 create_time TIMESTAMP,
 update_name VARCHAR(64),
 update_by INT,
 update_time TIMESTAMP,

 PRIMARY KEY (sys_job_log_id),
 CONSTRAINT uk_sys_job_log_no UNIQUE (sys_job_log_no)
);

CREATE INDEX IF NOT EXISTS idx_sys_job_log_job_id ON sys_job_log (sys_job_id);
CREATE INDEX IF NOT EXISTS idx_sys_job_log_node_id ON sys_job_log (sys_job_log_node_id);
CREATE INDEX IF NOT EXISTS idx_sys_job_log_trigger_date ON sys_job_log (sys_job_log_trigger_date);











-- =====================================================
-- 3. 集群选主表 (sys_job_cluster)
-- 用途: 记录各命名空间的 Leader 节点与租约状态
-- 注意: 本表仅有结构, 无初始数据; 首次抢占前会 INSERT 命名空间行,
-- 该兜底逻辑在 MysqlLeaderElectionResolver 中实现
-- =====================================================
CREATE TABLE sys_job_cluster (
 -- 主键: 集群命名空间 ID
 sys_job_cluster_id VARCHAR(64) NOT NULL,
 -- 当前 Leader 节点 ID
 sys_job_cluster_leader_node VARCHAR(64),
 -- 租约到期时间戳 (毫秒)
 sys_job_cluster_lease_until_ms NUMERIC(19, 0) DEFAULT 0,
 -- 乐观锁版本
 sys_job_cluster_version NUMERIC(19, 0) DEFAULT 0,

 -- 通用审计字段 (由 ORM 审计填充, 参考 SysBase)
 create_name VARCHAR(64),
 create_by INT,
 create_time TIMESTAMP,
 update_name VARCHAR(64),
 update_by INT,
 update_time TIMESTAMP,

 PRIMARY KEY (sys_job_cluster_id)
);


















-- ---------------------------------------------------------------------
-- 来源: V1.0__init_message.sql
-- ---------------------------------------------------------------------
/*
 * 消息推送模块 - 数据库初始化脚本
 *
 * 包含表:
 *   - sys_message_provider: 消息提供商表
 *   - sys_message_template: 消息模板表
 *   - sys_message_log: 消息推送日志表
 *
 * 用于管理消息服务商配置与消息模板，提供统一的消息推送能力。
 *
 * @author CH
 * @version 1.0
 * @since 2026-08-03
 */

SET NAMES utf8mb4;











SET FOREIGN_KEY_CHECKS = 0;







-- [通用化] 全量脚本禁止 DROP TABLE（数据丢失风险），老库重建走 DBA 流程；原语句：
-- DROP TABLE IF EXISTS `sys_message_provider`;;




CREATE TABLE `sys_message_provider` (
 `sys_message_provider_id` int NOT NULL AUTO_INCREMENT COMMENT '提供商ID - 主键自增',
 `sys_message_provider_code` varchar(255) NULL DEFAULT NULL COMMENT '提供商编码 - 唯一标识，如 aliyun/tencent/smtp',
 `sys_message_provider_name` varchar(255) NULL DEFAULT NULL COMMENT '提供商名称 - 显示名称',
 `sys_message_provider_type` varchar(64) NULL DEFAULT NULL COMMENT '提供商类型 - EMAIL/SMS/WECHAT/DINGDING/FEISHU等',
 `sys_message_provider_vender` varchar(128) NULL DEFAULT NULL COMMENT '厂家编码 - 适配的扩展厂家标识',
 `sys_message_provider_app_id` varchar(255) NULL DEFAULT NULL COMMENT '应用ID - 用于API认证',
 `sys_message_provider_app_secret` varchar(255) NULL DEFAULT NULL COMMENT '应用密钥 - 用于API认证',
 `sys_message_provider_sign` varchar(255) NULL DEFAULT NULL COMMENT '签名 - 短信/接口签名',
 `sys_message_provider_endpoint` varchar(255) NULL DEFAULT NULL COMMENT '端点地址 - API服务端点URL',
 `sys_message_provider_api_address` varchar(255) NULL DEFAULT NULL COMMENT 'API地址 - 接口服务基础地址',
 `sys_message_provider_smtp_host` varchar(255) NULL DEFAULT NULL COMMENT 'SMTP主机 - 邮件服务器主机地址',
 `sys_message_provider_smtp_port` int NULL DEFAULT NULL COMMENT 'SMTP端口 - 邮件服务器端口号',
 `sys_message_provider_smtp_password` varchar(255) NULL DEFAULT NULL COMMENT 'SMTP密码 - 邮件服务器授权码',
 `sys_message_provider_smtp_from` varchar(255) NULL DEFAULT NULL COMMENT 'SMTP发件人 - 发件人邮箱地址',
 `sys_message_provider_webhook_url` varchar(512) NULL DEFAULT NULL COMMENT 'Webhook地址 - 用于钉钉、飞书等机器人',
 `sys_message_provider_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
 `sys_message_provider_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
 `sys_message_provider_interval` int NULL DEFAULT 1 COMMENT '重复消息间隔 - 分钟，间隔内同一接收人相同模板/内容不重复发送，默认1分钟，0表示不限制',
 `sys_message_provider_sort` int NULL DEFAULT 0 COMMENT '排序号 - 列表显示顺序',
 `sys_message_provider_remark` varchar(255) NULL DEFAULT NULL COMMENT '描述 - 提供商用途说明',
 `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
 `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
 `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
 `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
 `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
 PRIMARY KEY (`sys_message_provider_id`) ,
 UNIQUE INDEX `uk_provider_code` (`sys_message_provider_code`) COMMENT '提供商编码唯一索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 
COMMENT = '消息提供商表 - 管理短信、邮件、微信、钉钉等消息服务商配置' ;











-- [通用化] 行内索引拆出为独立语句（H2/MySQL 双兼容幂等）
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_provider_type` (`sys_message_provider_type`) COMMENT '提供商类型索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_provider_status` (`sys_message_provider_status`) COMMENT '状态索引'


-- [通用化] 全量脚本禁止 DROP TABLE（数据丢失风险），老库重建走 DBA 流程；原语句：
-- DROP TABLE IF EXISTS `sys_message_template`;;




CREATE TABLE `sys_message_template` (
 `sys_message_template_id` int NOT NULL AUTO_INCREMENT COMMENT '模板ID - 主键自增',
 `sys_message_provider_id` int NULL DEFAULT NULL COMMENT '提供商ID - 关联sys_message_provider表',
 `sys_message_template_code` varchar(255) NULL DEFAULT NULL COMMENT '模板编码 - 模板唯一标识',
 `sys_message_template_name` varchar(255) NULL DEFAULT NULL COMMENT '模板名称',
 `sys_message_template_type` varchar(64) NULL DEFAULT NULL COMMENT '模板类型 - EMAIL/SMS/WECHAT等',
 `sys_message_template_category` int NULL DEFAULT NULL COMMENT '子类型 - 短信通知/验证码等',
 `sys_message_template_content` text NULL COMMENT '模板内容 - 模板内容，变量用${var}表示',
 `sys_message_template_callback_url` varchar(512) NULL DEFAULT NULL COMMENT '回调地址 - 邮件状态回调URL',
 `sys_message_template_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
 `sys_message_template_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
 `sys_message_template_sort` int NULL DEFAULT 0 COMMENT '模板优先级',
 `sys_message_template_default` int NULL DEFAULT 0 COMMENT '是否默认 - 0:否, 1:是',
 `sys_message_template_remark` varchar(512) NULL DEFAULT NULL COMMENT '备注说明',
 `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
 `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
 `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
 `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
 `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
 PRIMARY KEY (`sys_message_template_id`) 
) ENGINE = InnoDB AUTO_INCREMENT = 1 
COMMENT = '消息模板表 - 管理各渠道消息模板' ;











-- [通用化] 行内索引拆出为独立语句（H2/MySQL 双兼容幂等）
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_template_provider` (`sys_message_provider_id`) COMMENT '提供商ID索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_template_code` (`sys_message_template_code`) COMMENT '模板编码索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_template_status` (`sys_message_template_status`) COMMENT '状态索引'


-- [通用化] 全量脚本禁止 DROP TABLE（数据丢失风险），老库重建走 DBA 流程；原语句：
-- DROP TABLE IF EXISTS `sys_message_log`;;




CREATE TABLE `sys_message_log` (
 `sys_message_log_id` int NOT NULL AUTO_INCREMENT COMMENT '日志ID - 主键自增',
 `sys_message_log_target` varchar(255) NULL DEFAULT NULL COMMENT '接收人 - 手机号/邮箱/用户ID等',
 `sys_message_template_id` int NULL DEFAULT NULL COMMENT '模板ID - 使用的模板',
 `sys_message_provider_id` int NULL DEFAULT NULL COMMENT '提供商ID - 使用的消息提供商',
 `sys_message_log_channel` varchar(64) NULL DEFAULT NULL COMMENT '渠道标识 - 实际发送渠道',
 `sys_message_log_content` longtext NULL COMMENT '发送内容 - 最终发送的消息内容',
 `sys_message_log_param` longtext NULL COMMENT '模板参数 - JSON格式的模板变量',
 `sys_message_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '耗时 - 发送耗时(毫秒)',
 `sys_message_code` varchar(64) NULL DEFAULT NULL COMMENT '返回码 - SUCCESS/FAILURE',
 `sys_message_message` text NULL COMMENT '返回消息 - 错误信息',
 `sys_message_log_subject` varchar(512) NULL DEFAULT NULL COMMENT '消息主题 - 邮件标题等',
 `sys_message_log_message_id` varchar(255) NULL DEFAULT NULL COMMENT '消息ID - 服务商返回的消息标识',
 `sys_message_batch_id` varchar(64) NULL DEFAULT NULL COMMENT '批次号 - 同一次发送请求的接收人共享',
 `sys_message_track_token` varchar(64) NULL DEFAULT NULL COMMENT '追踪标识 - 邮件阅读追踪token，用于回写已读状态',
 `sys_message_read_status` int NULL DEFAULT 0 COMMENT '已读状态 - 0:未读, 1:已读',
 `sys_message_read_time` datetime NULL DEFAULT NULL COMMENT '已读时间 - 收件人打开邮件触发追踪的时间',
 `sys_message_log_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
 `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
 `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
 `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
 `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
 `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
 PRIMARY KEY (`sys_message_log_id`) 
) ENGINE = InnoDB AUTO_INCREMENT = 1 
COMMENT = '消息推送日志表 - 记录消息推送历史' ;











-- [通用化] 行内索引拆出为独立语句（H2/MySQL 双兼容幂等）
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_log_target` (`sys_message_log_target`) USING BTREE COMMENT '接收人索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_log_template` (`sys_message_template_id`) USING BTREE COMMENT '模板ID索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_log_provider` (`sys_message_provider_id`) USING BTREE COMMENT '提供商ID索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_log_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_log_track_token` (`sys_message_track_token`) USING BTREE COMMENT '追踪标识索引'
-- [通用化] 无法解析的行内索引定义（保留供 DBA）：INDEX `idx_log_batch` (`sys_message_batch_id`) USING BTREE COMMENT '批次号索引'






SET FOREIGN_KEY_CHECKS = 1;












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_payment.sql
-- ---------------------------------------------------------------------
-- 支付系统数据库表结构
-- 更新时间: 2026-05-08
-- 说明: 商户、支付方式、订单、状态日志、交易流水、钱包、退款、回调、调度、分表
-- 注意: 所有列使用表名前缀命名策略以匹配实体注解

CREATE TABLE IF NOT EXISTS `pay_merchant` (
 `pay_merchant_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_merchant_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '商户号',
 `pay_merchant_name` VARCHAR(100) NOT NULL COMMENT '商户名称',
 `pay_merchant_contact_name` VARCHAR(50) DEFAULT NULL COMMENT '联系人',
 `pay_merchant_contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
 `pay_merchant_contact_email` VARCHAR(100) DEFAULT NULL COMMENT '联系邮箱',
 `pay_merchant_business_license` VARCHAR(200) DEFAULT NULL COMMENT '营业执照',
 `pay_merchant_legal_person` VARCHAR(50) DEFAULT NULL COMMENT '法人',
 `pay_merchant_default_notify_url` VARCHAR(255) DEFAULT NULL COMMENT '默认支付回调地址',
 `pay_merchant_default_return_url` VARCHAR(255) DEFAULT NULL COMMENT '默认支付返回地址',
 `pay_merchant_payment_wallet_enabled` TINYINT DEFAULT 0 COMMENT '钱包能力开关',
 `pay_merchant_payment_auto_close_enabled` TINYINT DEFAULT 1 COMMENT '自动关单开关',
 `pay_merchant_payment_auto_close_minutes` INT DEFAULT 30 COMMENT '自动关单分钟数',
 `pay_merchant_payment_split_table_enabled` TINYINT DEFAULT 0 COMMENT '自动分表能力开关',
 `pay_merchant_payment_profit_sharing_enabled` TINYINT DEFAULT 0 COMMENT '自动分账能力开关',
 `pay_merchant_payment_coupon_enabled` TINYINT DEFAULT 0 COMMENT '优惠能力开关',
 `pay_merchant_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_merchant_status` TINYINT DEFAULT 0 COMMENT '状态:0待审核 1已激活 2已停用 3已注销',
 `pay_merchant_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_merchant_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付商户表';

CREATE INDEX IF NOT EXISTS idx_pay_merchant_no ON pay_merchant (`pay_merchant_no`);
CREATE INDEX IF NOT EXISTS idx_pay_merchant_status ON pay_merchant (`pay_merchant_status`);











CREATE TABLE IF NOT EXISTS `pay_merchant_channel` (
 `pay_merchant_channel_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_merchant_channel_channel_type` VARCHAR(32) NOT NULL COMMENT '渠道类型',
 `pay_merchant_channel_channel_sub_type` VARCHAR(32) NOT NULL COMMENT '渠道子类型',
 `pay_merchant_channel_channel_name` VARCHAR(100) NOT NULL COMMENT '渠道名称',
 `pay_merchant_channel_app_id` VARCHAR(120) DEFAULT NULL COMMENT '应用ID',
 `pay_merchant_channel_merchant_no` VARCHAR(120) DEFAULT NULL COMMENT '商户号/PID/路由标识',
 `pay_merchant_channel_api_key` TEXT DEFAULT NULL COMMENT 'API Key(加密)',
 `pay_merchant_channel_private_key` TEXT DEFAULT NULL COMMENT '私钥(加密)',
 `pay_merchant_channel_public_key` TEXT DEFAULT NULL COMMENT '公钥',
 `pay_merchant_channel_cert_path` VARCHAR(255) DEFAULT NULL COMMENT '证书路径',
 `pay_merchant_channel_sandbox_mode` TINYINT DEFAULT 0 COMMENT '沙箱模式:0正式 1沙箱',
 `pay_merchant_channel_notify_url` VARCHAR(255) DEFAULT NULL COMMENT '渠道专属回调地址',
 `pay_merchant_channel_return_url` VARCHAR(255) DEFAULT NULL COMMENT '渠道专属返回地址',
 `pay_merchant_channel_status` TINYINT DEFAULT 0 COMMENT '状态:0禁用 1启用',
 `pay_merchant_channel_ext_config` TEXT DEFAULT NULL COMMENT '扩展配置JSON',
 `pay_merchant_channel_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_merchant_channel_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户支付方式配置表';

CREATE INDEX IF NOT EXISTS idx_pay_merchant_channel_merchant ON pay_merchant_channel (`pay_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_merchant_channel_type ON pay_merchant_channel (`pay_merchant_channel_channel_type`);
CREATE INDEX IF NOT EXISTS idx_pay_merchant_channel_status ON pay_merchant_channel (`pay_merchant_channel_status`);











CREATE TABLE IF NOT EXISTS `pay_order` (
 `pay_order_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_order_order_no` VARCHAR(40) NOT NULL UNIQUE COMMENT '平台订单号',
 `pay_order_business_order_no` VARCHAR(64) DEFAULT NULL COMMENT '业务订单号',
 `pay_order_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_order_channel_id` BIGINT DEFAULT NULL COMMENT '支付方式ID',
 `pay_order_user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
 `pay_order_channel_type` VARCHAR(32) NOT NULL COMMENT '支付渠道类型',
 `pay_order_channel_sub_type` VARCHAR(32) DEFAULT NULL COMMENT '支付渠道子类型',
 `pay_order_amount` DECIMAL(18,2) NOT NULL COMMENT '订单金额',
 `pay_order_paid_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '实付金额',
 `pay_order_refund_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '已退金额',
 `pay_order_discount_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '优惠金额',
 `pay_order_currency` VARCHAR(16) DEFAULT 'CNY' COMMENT '币种',
 `pay_order_status` VARCHAR(32) DEFAULT 'PENDING' COMMENT '订单状态',
 `pay_order_subject` VARCHAR(200) DEFAULT NULL COMMENT '订单标题',
 `pay_order_body` VARCHAR(500) DEFAULT NULL COMMENT '订单描述',
 `pay_order_notify_url` VARCHAR(255) DEFAULT NULL COMMENT '支付回调地址',
 `pay_order_return_url` VARCHAR(255) DEFAULT NULL COMMENT '返回地址',
 `pay_order_expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
 `pay_order_pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
 `pay_order_complete_time` DATETIME DEFAULT NULL COMMENT '完成时间',
 `pay_order_refund_time` DATETIME DEFAULT NULL COMMENT '退款时间',
 `pay_order_third_party_order_no` VARCHAR(100) DEFAULT NULL COMMENT '第三方订单号',
 `pay_order_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_order_deleted` TINYINT DEFAULT 0 COMMENT '删除标记:0未删 1已删',
 `pay_order_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_order_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

CREATE INDEX IF NOT EXISTS idx_pay_order_no ON pay_order (`pay_order_order_no`);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_order_merchant_business_no ON pay_order (`pay_order_merchant_id`, `pay_order_business_order_no`);
CREATE INDEX IF NOT EXISTS idx_pay_order_merchant_id ON pay_order (`pay_order_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_order_status ON pay_order (`pay_order_status`);
CREATE INDEX IF NOT EXISTS idx_pay_order_deleted ON pay_order (`pay_order_deleted`);
CREATE INDEX IF NOT EXISTS idx_pay_order_timeout_scan ON pay_order (`pay_order_status`, `pay_order_deleted`, `pay_order_expire_time`);











CREATE TABLE IF NOT EXISTS `pay_order_state_log` (
 `pay_order_state_log_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_order_state_log_order_id` BIGINT NOT NULL COMMENT '订单ID',
 `pay_order_state_log_from_state` VARCHAR(32) DEFAULT NULL COMMENT '原状态',
 `pay_order_state_log_to_state` VARCHAR(32) NOT NULL COMMENT '目标状态',
 `pay_order_state_log_event` VARCHAR(50) NOT NULL COMMENT '触发事件',
 `pay_order_state_log_operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
 `pay_order_state_log_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_order_state_log_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志表';

CREATE INDEX IF NOT EXISTS idx_pay_order_state_log_order_id ON pay_order_state_log (`pay_order_state_log_order_id`);











CREATE TABLE IF NOT EXISTS `pay_transaction_record` (
 `pay_transaction_record_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_transaction_record_transaction_no` VARCHAR(40) NOT NULL UNIQUE COMMENT '流水号',
 `pay_transaction_record_order_id` BIGINT NOT NULL COMMENT '订单ID',
 `pay_transaction_record_order_no` VARCHAR(40) NOT NULL COMMENT '订单号',
 `pay_transaction_record_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_transaction_record_channel_id` BIGINT DEFAULT NULL COMMENT '支付方式ID',
 `pay_transaction_record_transaction_type` VARCHAR(32) NOT NULL COMMENT '交易类型',
 `pay_transaction_record_amount` DECIMAL(18,2) NOT NULL COMMENT '交易金额',
 `pay_transaction_record_channel_type` VARCHAR(32) NOT NULL COMMENT '渠道类型',
 `pay_transaction_record_third_party_transaction_no` VARCHAR(100) DEFAULT NULL COMMENT '第三方流水号',
 `pay_transaction_record_status` TINYINT DEFAULT 2 COMMENT '状态:0失败 1成功 2处理中',
 `pay_transaction_record_request_payload` TEXT DEFAULT NULL COMMENT '请求快照',
 `pay_transaction_record_response_payload` TEXT DEFAULT NULL COMMENT '响应快照',
 `pay_transaction_record_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_transaction_record_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水表';

CREATE INDEX IF NOT EXISTS idx_pay_transaction_no ON pay_transaction_record (`pay_transaction_record_transaction_no`);
CREATE INDEX IF NOT EXISTS idx_pay_transaction_order_no ON pay_transaction_record (`pay_transaction_record_order_no`);
CREATE INDEX IF NOT EXISTS idx_pay_transaction_merchant_id ON pay_transaction_record (`pay_transaction_record_merchant_id`);











CREATE TABLE IF NOT EXISTS `pay_wallet_account` (
 `pay_wallet_account_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_wallet_account_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_wallet_account_user_id` BIGINT NOT NULL COMMENT '用户ID',
 `pay_wallet_account_available_balance` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '可用余额',
 `pay_wallet_account_frozen_balance` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冻结余额',
 `pay_wallet_account_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0禁用',
 `pay_wallet_account_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_wallet_account_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_wallet_account_merchant_user ON pay_wallet_account (`pay_wallet_account_merchant_id`, `pay_wallet_account_user_id`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_account_merchant ON pay_wallet_account (`pay_wallet_account_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_account_user ON pay_wallet_account (`pay_wallet_account_user_id`);











CREATE TABLE IF NOT EXISTS `pay_wallet_account_log` (
 `pay_wallet_account_log_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_wallet_account_log_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_wallet_account_log_user_id` BIGINT NOT NULL COMMENT '用户ID',
 `pay_wallet_account_log_biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型:RECHARGE/PAY/REFUND',
 `pay_wallet_account_log_biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
 `pay_wallet_account_log_change_type` VARCHAR(16) NOT NULL COMMENT '变动方向:IN/OUT',
 `pay_wallet_account_log_change_amount` DECIMAL(18,2) NOT NULL COMMENT '变动金额',
 `pay_wallet_account_log_balance_before` DECIMAL(18,2) NOT NULL COMMENT '变动前余额',
 `pay_wallet_account_log_balance_after` DECIMAL(18,2) NOT NULL COMMENT '变动后余额',
 `pay_wallet_account_log_operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
 `pay_wallet_account_log_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_wallet_account_log_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户流水表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_wallet_log_merchant_user_biz ON pay_wallet_account_log (`pay_wallet_account_log_merchant_id`, `pay_wallet_account_log_user_id`, `pay_wallet_account_log_biz_type`, `pay_wallet_account_log_biz_no`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_log_merchant_user ON pay_wallet_account_log (`pay_wallet_account_log_merchant_id`, `pay_wallet_account_log_user_id`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_log_biz ON pay_wallet_account_log (`pay_wallet_account_log_biz_type`, `pay_wallet_account_log_biz_no`);











CREATE TABLE IF NOT EXISTS `pay_merchant_payment_config` (
 `pay_merchant_payment_config_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_merchant_payment_config_tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
 `pay_merchant_payment_config_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_merchant_payment_config_order_reusable` TINYINT DEFAULT 1 COMMENT '订单是否可重复支付',
 `pay_merchant_payment_config_order_timeout_minutes` INT DEFAULT NULL COMMENT '订单超时时间(分钟)',
 `pay_merchant_payment_config_pending_order_limit` INT DEFAULT NULL COMMENT '待支付订单数量限制',
 `pay_merchant_payment_config_auto_cancel_timeout_order` TINYINT DEFAULT 1 COMMENT '是否自动取消超时订单',
 `pay_merchant_payment_config_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_merchant_payment_config_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `pay_merchant_payment_config_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
 `pay_merchant_payment_config_update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
 `pay_merchant_payment_config_create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `pay_merchant_payment_config_update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户支付配置表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_merchant_payment_config_merchant ON pay_merchant_payment_config (`pay_merchant_payment_config_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_merchant_payment_config_tenant_merchant ON pay_merchant_payment_config (`pay_merchant_payment_config_tenant_id`, `pay_merchant_payment_config_merchant_id`);











CREATE TABLE IF NOT EXISTS `pay_global_config` (
 `pay_global_config_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_global_config_config_key` VARCHAR(32) NOT NULL COMMENT '配置标识',
 `pay_global_config_notify_base_url` VARCHAR(255) DEFAULT NULL COMMENT '默认回调基础地址',
 `pay_global_config_return_url` VARCHAR(255) DEFAULT NULL COMMENT '默认返回地址',
 `pay_global_config_callback_path_template` VARCHAR(128) DEFAULT '/{orderNo}/{merchantId}' COMMENT '回调路径模板',
 `pay_global_config_auto_refresh_seconds` INT DEFAULT 60 COMMENT '默认自动刷新秒数',
 `pay_global_config_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_global_config_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_global_config_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付全局配置表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_global_config_key ON pay_global_config (`pay_global_config_config_key`);











CREATE TABLE IF NOT EXISTS `pay_refund_order` (
 `pay_refund_order_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_refund_order_refund_no` VARCHAR(40) NOT NULL COMMENT '退款单号',
 `pay_refund_order_order_id` BIGINT NOT NULL COMMENT '支付订单ID',
 `pay_refund_order_order_no` VARCHAR(40) NOT NULL COMMENT '支付订单号',
 `pay_refund_order_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_refund_order_channel_id` BIGINT DEFAULT NULL COMMENT '支付方式ID',
 `pay_refund_order_source_order_status` VARCHAR(32) DEFAULT NULL COMMENT '发起退款前订单状态',
 `pay_refund_order_third_party_refund_no` VARCHAR(100) DEFAULT NULL COMMENT '第三方退款单号',
 `pay_refund_order_refund_amount` DECIMAL(18,2) NOT NULL COMMENT '退款金额',
 `pay_refund_order_status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '退款状态',
 `pay_refund_order_reason` VARCHAR(500) DEFAULT NULL COMMENT '退款原因',
 `pay_refund_order_notify_status` TINYINT NOT NULL DEFAULT 0 COMMENT '回调状态:0未收到 1已收到',
 `pay_refund_order_request_payload` TEXT DEFAULT NULL COMMENT '退款请求快照',
 `pay_refund_order_response_payload` TEXT DEFAULT NULL COMMENT '退款响应快照',
 `pay_refund_order_operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
 `pay_refund_order_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_refund_order_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_refund_order_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付退款单表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_refund_order_refund_no ON pay_refund_order (`pay_refund_order_refund_no`);
CREATE INDEX IF NOT EXISTS idx_pay_refund_order_order ON pay_refund_order (`pay_refund_order_order_id`, `pay_refund_order_created_at`);
CREATE INDEX IF NOT EXISTS idx_pay_refund_order_status ON pay_refund_order (`pay_refund_order_status`, `pay_refund_order_updated_at`);











CREATE TABLE IF NOT EXISTS `pay_notify_log` (
 `pay_notify_log_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_notify_log_notify_type` VARCHAR(32) NOT NULL COMMENT '通知类型',
 `pay_notify_log_tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
 `pay_notify_log_merchant_id` BIGINT DEFAULT NULL COMMENT '商户ID',
 `pay_notify_log_channel_id` BIGINT DEFAULT NULL COMMENT '支付方式ID',
 `pay_notify_log_channel_type` VARCHAR(32) DEFAULT NULL COMMENT '渠道类型',
 `pay_notify_log_channel_sub_type` VARCHAR(32) DEFAULT NULL COMMENT '渠道子类型',
 `pay_notify_log_order_id` BIGINT DEFAULT NULL COMMENT '订单ID',
 `pay_notify_log_order_no` VARCHAR(64) DEFAULT NULL COMMENT '订单号',
 `pay_notify_log_refund_id` BIGINT DEFAULT NULL COMMENT '退款单ID',
 `pay_notify_log_refund_no` VARCHAR(64) DEFAULT NULL COMMENT '退款单号',
 `pay_notify_log_third_party_trade_no` VARCHAR(128) DEFAULT NULL COMMENT '第三方交易号',
 `pay_notify_log_request_headers` TEXT DEFAULT NULL COMMENT '请求头',
 `pay_notify_log_request_body` LONGTEXT DEFAULT NULL COMMENT '请求体',
 `pay_notify_log_request_params` TEXT DEFAULT NULL COMMENT '请求参数',
 `pay_notify_log_sign_verified` TINYINT DEFAULT 0 COMMENT '验签结果:0失败 1成功',
 `pay_notify_log_process_status` VARCHAR(32) DEFAULT 'PROCESSING' COMMENT '处理状态',
 `pay_notify_log_process_result` TEXT DEFAULT NULL COMMENT '处理结果',
 `pay_notify_log_error_message` VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
 `pay_notify_log_retry_count` INT DEFAULT 0 COMMENT '重试次数',
 `pay_notify_log_received_at` DATETIME DEFAULT NULL COMMENT '接收时间',
 `pay_notify_log_processed_at` DATETIME DEFAULT NULL COMMENT '处理时间',
 `pay_notify_log_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_notify_log_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `pay_notify_log_related_order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID',
 `pay_notify_log_related_refund_id` BIGINT DEFAULT NULL COMMENT '关联退款单ID',
 `pay_notify_log_received_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '兼容旧字段-接收时间',
 `pay_notify_log_processed_time` DATETIME DEFAULT NULL COMMENT '兼容旧字段-处理时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';

CREATE INDEX IF NOT EXISTS idx_pay_notify_log_tenant_merchant ON pay_notify_log (`pay_notify_log_tenant_id`, `pay_notify_log_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_log_order ON pay_notify_log (`pay_notify_log_order_no`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_log_refund ON pay_notify_log (`pay_notify_log_refund_no`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_log_status ON pay_notify_log (`pay_notify_log_process_status`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_log_received ON pay_notify_log (`pay_notify_log_received_time`);











CREATE TABLE IF NOT EXISTS `pay_notify_error` (
 `pay_notify_error_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_notify_error_notify_log_id` BIGINT NOT NULL COMMENT '回调日志ID',
 `pay_notify_error_notify_type` VARCHAR(32) NOT NULL COMMENT '通知类型',
 `pay_notify_error_tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
 `pay_notify_error_merchant_id` BIGINT DEFAULT NULL COMMENT '商户ID',
 `pay_notify_error_order_no` VARCHAR(64) DEFAULT NULL COMMENT '订单号',
 `pay_notify_error_refund_no` VARCHAR(64) DEFAULT NULL COMMENT '退款单号',
 `pay_notify_error_error_type` VARCHAR(64) NOT NULL COMMENT '错误类型',
 `pay_notify_error_error_message` TEXT DEFAULT NULL COMMENT '错误信息',
 `pay_notify_error_error_stack` LONGTEXT DEFAULT NULL COMMENT '错误堆栈',
 `pay_notify_error_request_data` LONGTEXT DEFAULT NULL COMMENT '请求数据',
 `pay_notify_error_retry_count` INT DEFAULT 0 COMMENT '重试次数',
 `pay_notify_error_max_retry_count` INT DEFAULT 5 COMMENT '最大重试次数',
 `pay_notify_error_next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间',
 `pay_notify_error_status` VARCHAR(32) NOT NULL COMMENT '状态',
 `pay_notify_error_resolved_at` DATETIME DEFAULT NULL COMMENT '解决时间',
 `pay_notify_error_resolved_by` VARCHAR(64) DEFAULT NULL COMMENT '解决人',
 `pay_notify_error_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
 `pay_notify_error_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_notify_error_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调异常表';

CREATE INDEX IF NOT EXISTS idx_pay_notify_error_log ON pay_notify_error (`pay_notify_error_notify_log_id`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_error_status ON pay_notify_error (`pay_notify_error_status`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_error_retry ON pay_notify_error (`pay_notify_error_next_retry_time`);
CREATE INDEX IF NOT EXISTS idx_pay_notify_error_tenant_merchant ON pay_notify_error (`pay_notify_error_tenant_id`, `pay_notify_error_merchant_id`);











CREATE TABLE IF NOT EXISTS `pay_wallet_order` (
 `pay_wallet_order_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_wallet_order_order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
 `pay_wallet_order_order_type` VARCHAR(32) NOT NULL COMMENT '订单类型:RECHARGE-充值 TRANSFER-转账 WITHDRAW-提现',
 `pay_wallet_order_tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
 `pay_wallet_order_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_wallet_order_user_id` BIGINT NOT NULL COMMENT '用户ID',
 `pay_wallet_order_related_user_id` BIGINT DEFAULT NULL COMMENT '关联用户ID(转账时使用)',
 `pay_wallet_order_amount` DECIMAL(15,2) NOT NULL COMMENT '金额',
 `pay_wallet_order_status` VARCHAR(32) NOT NULL COMMENT '状态:PENDING-待处理 PROCESSING-处理中 SUCCESS-成功 FAILED-失败',
 `pay_wallet_order_third_party_order_no` VARCHAR(128) DEFAULT NULL COMMENT '第三方订单号',
 `pay_wallet_order_bank_account` VARCHAR(64) DEFAULT NULL COMMENT '银行账号',
 `pay_wallet_order_bank_name` VARCHAR(128) DEFAULT NULL COMMENT '银行名称',
 `pay_wallet_order_account_name` VARCHAR(64) DEFAULT NULL COMMENT '账户名',
 `pay_wallet_order_request_payload` TEXT DEFAULT NULL COMMENT '请求报文',
 `pay_wallet_order_response_payload` TEXT DEFAULT NULL COMMENT '响应报文',
 `pay_wallet_order_operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
 `pay_wallet_order_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
 `pay_wallet_order_completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
 `pay_wallet_order_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_wallet_order_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `pay_wallet_order_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
 `pay_wallet_order_update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
 `pay_wallet_order_create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `pay_wallet_order_update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包订单表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_wallet_order_no ON pay_wallet_order (`pay_wallet_order_order_no`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_order_tenant_merchant ON pay_wallet_order (`pay_wallet_order_tenant_id`, `pay_wallet_order_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_order_user ON pay_wallet_order (`pay_wallet_order_user_id`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_order_type ON pay_wallet_order (`pay_wallet_order_order_type`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_order_status ON pay_wallet_order (`pay_wallet_order_status`);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_order_create_time ON pay_wallet_order (`pay_wallet_order_create_time`);











CREATE TABLE IF NOT EXISTS `pay_merchant_wallet_limit` (
 `pay_merchant_wallet_limit_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_merchant_wallet_limit_tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
 `pay_merchant_wallet_limit_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_merchant_wallet_limit_single_recharge_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '单笔充值限额',
 `pay_merchant_wallet_limit_daily_recharge_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '日充值限额',
 `pay_merchant_wallet_limit_single_withdraw_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '单笔提现限额',
 `pay_merchant_wallet_limit_daily_withdraw_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '日提现限额',
 `pay_merchant_wallet_limit_single_transfer_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '单笔转账限额',
 `pay_merchant_wallet_limit_daily_transfer_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '日转账限额',
 `pay_merchant_wallet_limit_balance_limit` DECIMAL(15,2) DEFAULT NULL COMMENT '余额上限',
 `pay_merchant_wallet_limit_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_merchant_wallet_limit_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `pay_merchant_wallet_limit_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
 `pay_merchant_wallet_limit_update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
 `pay_merchant_wallet_limit_create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `pay_merchant_wallet_limit_update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户钱包限额配置表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_merchant_wallet_limit_merchant ON pay_merchant_wallet_limit (`pay_merchant_wallet_limit_merchant_id`);
CREATE INDEX IF NOT EXISTS idx_pay_merchant_wallet_limit_tenant_merchant ON pay_merchant_wallet_limit (`pay_merchant_wallet_limit_tenant_id`, `pay_merchant_wallet_limit_merchant_id`);











CREATE TABLE IF NOT EXISTS `pay_wechat_pay_score_order` (
 `pay_wechat_pay_score_order_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_wechat_pay_score_order_merchant_id` BIGINT NOT NULL COMMENT '商户ID',
 `pay_wechat_pay_score_order_channel_id` BIGINT NOT NULL COMMENT '渠道ID',
 `pay_wechat_pay_score_order_user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
 `pay_wechat_pay_score_order_out_order_no` VARCHAR(64) NOT NULL COMMENT '商户服务订单号',
 `pay_wechat_pay_score_order_service_order_no` VARCHAR(64) DEFAULT NULL COMMENT '微信支付分服务订单号',
 `pay_wechat_pay_score_order_app_id` VARCHAR(128) DEFAULT NULL COMMENT '应用ID',
 `pay_wechat_pay_score_order_service_id` VARCHAR(128) NOT NULL COMMENT '支付分服务ID',
 `pay_wechat_pay_score_order_open_id` VARCHAR(128) NOT NULL COMMENT '用户openId',
 `pay_wechat_pay_score_order_state` VARCHAR(32) NOT NULL COMMENT '订单状态',
 `pay_wechat_pay_score_order_total_amount` DECIMAL(15,2) DEFAULT NULL COMMENT '金额',
 `pay_wechat_pay_score_order_notify_url` VARCHAR(255) DEFAULT NULL COMMENT '回调地址',
 `pay_wechat_pay_score_order_service_introduction` VARCHAR(255) DEFAULT NULL COMMENT '服务介绍',
 `pay_wechat_pay_score_order_start_time` VARCHAR(64) DEFAULT NULL COMMENT '服务开始时间',
 `pay_wechat_pay_score_order_end_time` VARCHAR(64) DEFAULT NULL COMMENT '服务结束时间',
 `pay_wechat_pay_score_order_finish_type` VARCHAR(32) DEFAULT NULL COMMENT '完结类型',
 `pay_wechat_pay_score_order_reason` VARCHAR(255) DEFAULT NULL COMMENT '原因',
 `pay_wechat_pay_score_order_finish_reason` VARCHAR(255) DEFAULT NULL COMMENT '完成原因',
 `pay_wechat_pay_score_order_package_info` VARCHAR(512) DEFAULT NULL COMMENT '拉起参数',
 `pay_wechat_pay_score_order_attach` VARCHAR(255) DEFAULT NULL COMMENT '附加数据',
 `pay_wechat_pay_score_order_request_payload` LONGTEXT DEFAULT NULL COMMENT '请求报文',
 `pay_wechat_pay_score_order_response_payload` LONGTEXT DEFAULT NULL COMMENT '响应报文',
 `pay_wechat_pay_score_order_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
 `pay_wechat_pay_score_order_completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
 `pay_wechat_pay_score_order_cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
 `pay_wechat_pay_score_order_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_wechat_pay_score_order_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信支付分订单表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_wechat_pay_score_order_no ON pay_wechat_pay_score_order (`pay_wechat_pay_score_order_out_order_no`);
CREATE INDEX IF NOT EXISTS idx_pay_wechat_pay_score_merchant ON pay_wechat_pay_score_order (`pay_wechat_pay_score_order_merchant_id`, `pay_wechat_pay_score_order_create_time`);
CREATE INDEX IF NOT EXISTS idx_pay_wechat_pay_score_channel ON pay_wechat_pay_score_order (`pay_wechat_pay_score_order_channel_id`, `pay_wechat_pay_score_order_create_time`);
CREATE INDEX IF NOT EXISTS idx_pay_wechat_pay_score_open ON pay_wechat_pay_score_order (`pay_wechat_pay_score_order_open_id`);
CREATE INDEX IF NOT EXISTS idx_pay_wechat_pay_score_state ON pay_wechat_pay_score_order (`pay_wechat_pay_score_order_state`, `pay_wechat_pay_score_order_update_time`);











CREATE TABLE IF NOT EXISTS `pay_scheduler_config` (
 `pay_scheduler_config_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_scheduler_config_task_key` VARCHAR(64) NOT NULL COMMENT '任务编码',
 `pay_scheduler_config_task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
 `pay_scheduler_config_cron_expression` VARCHAR(64) NOT NULL COMMENT 'Cron表达式',
 `pay_scheduler_config_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
 `pay_scheduler_config_description` VARCHAR(512) DEFAULT NULL COMMENT '任务说明',
 `pay_scheduler_config_last_started_at` DATETIME DEFAULT NULL COMMENT '最近开始执行时间',
 `pay_scheduler_config_last_finished_at` DATETIME DEFAULT NULL COMMENT '最近完成时间',
 `pay_scheduler_config_last_run_status` VARCHAR(32) DEFAULT NULL COMMENT '最近运行状态',
 `pay_scheduler_config_last_run_message` VARCHAR(512) DEFAULT NULL COMMENT '最近运行结果',
 `pay_scheduler_config_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_scheduler_config_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付调度任务配置表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_scheduler_config_task_key ON pay_scheduler_config (`pay_scheduler_config_task_key`);
CREATE INDEX IF NOT EXISTS idx_pay_scheduler_config_enabled ON pay_scheduler_config (`pay_scheduler_config_enabled`);











CREATE TABLE IF NOT EXISTS `pay_partition_config` (
 `pay_partition_config_id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
 `pay_partition_config_business_type` VARCHAR(32) NOT NULL COMMENT '业务类型: ORDER/TRANSACTION',
 `pay_partition_config_source_table` VARCHAR(64) NOT NULL COMMENT '源表名',
 `pay_partition_config_partition_prefix` VARCHAR(64) NOT NULL COMMENT '分表前缀',
 `pay_partition_config_partition_granularity` VARCHAR(16) NOT NULL DEFAULT 'MONTH' COMMENT '分表粒度: MONTH/DAY',
 `pay_partition_config_retention_days` INT NOT NULL DEFAULT 365 COMMENT '保留天数',
 `pay_partition_config_create_ahead_days` INT NOT NULL DEFAULT 7 COMMENT '提前建表天数',
 `pay_partition_config_migrate_before_days` INT NOT NULL DEFAULT 90 COMMENT '迁移阈值天数',
 `pay_partition_config_auto_create_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否自动建表',
 `pay_partition_config_auto_migrate_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否自动迁移',
 `pay_partition_config_keep_source_data` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '迁移后是否保留主表数据',
 `pay_partition_config_migration_dry_run` TINYINT(1) DEFAULT 1 COMMENT 'dry-run 模式: 1-仅复制不删除源数据',
 `pay_partition_config_time_column` VARCHAR(64) DEFAULT NULL COMMENT '时间列名(如 pay_order_created_at)',
 `pay_partition_config_id_column` VARCHAR(64) DEFAULT NULL COMMENT '主键列名(如 pay_order_id)',
 `pay_partition_config_create_task_key` VARCHAR(80) DEFAULT NULL COMMENT '自动建表任务Key',
 `pay_partition_config_migrate_task_key` VARCHAR(80) DEFAULT NULL COMMENT '迁移任务Key',
 `pay_partition_config_last_partition_table` VARCHAR(80) DEFAULT NULL COMMENT '最近分表名',
 `pay_partition_config_last_partition_at` DATETIME DEFAULT NULL COMMENT '最近建表时间',
 `pay_partition_config_last_migrate_at` DATETIME DEFAULT NULL COMMENT '最近迁移时间',
 `pay_partition_config_remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
 `pay_partition_config_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `pay_partition_config_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单与流水分表配置表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_partition_config_business_type ON pay_partition_config (`pay_partition_config_business_type`);












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_project.sql
-- ---------------------------------------------------------------------
-- ============================================================
-- 发布项目管理模块 DDL 初始化脚本 (spring-support-project-starter)
-- 适用数据库: MySQL 8.0+ / H2 (MODE=MySQL)
-- 说明: 全部建表/加列语句幂等 (IF NOT EXISTS)，可直接重复执行。
-- 本文件为全量版：已合并历史增量 (V1.1 归档/日志/自动启动、V1.2 健康检查/自动回滚/重试/归档保留)。
-- 表名前缀 ms_project_*，与 ms_server_host 资产台账通过主机 ID 关联。
-- @author CH
-- @since 4.0.0.42
-- ============================================================

-- 部署项目台账
CREATE TABLE IF NOT EXISTS ms_project (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
 ms_project_code VARCHAR(128) NOT NULL COMMENT '项目编码',
 ms_project_type VARCHAR(32) NOT NULL COMMENT '项目类型: SPRINGBOOT_MONO/SPRINGBOOT_WAR/SPRINGBOOT_SPLIT/NODEJS/STATIC/CUSTOM',
 ms_project_host_ids VARCHAR(1024) DEFAULT NULL COMMENT '目标服务器ID列表(JSON数组)',
 ms_project_host_os VARCHAR(16) DEFAULT NULL COMMENT '目标主机OS类型: LINUX/WINDOWS',
 ms_project_deploy_dir VARCHAR(512) NOT NULL COMMENT '部署目录',
 ms_project_start_script TEXT COMMENT '启动脚本',
 ms_project_stop_script TEXT COMMENT '停止脚本',
 ms_project_restart_script TEXT COMMENT '重启脚本',
 ms_project_log_dir VARCHAR(512) DEFAULT NULL COMMENT '日志目录',
 ms_project_log_file VARCHAR(256) DEFAULT NULL COMMENT '日志文件名(如 app.log)',
 ms_project_delete_before TINYINT DEFAULT 0 COMMENT '升级前是否先删除旧内容: 0否 1是',
 ms_project_overwrite_mode VARCHAR(16) DEFAULT 'OVERWRITE' COMMENT '覆盖模式: OVERWRITE直接覆盖/REPLACE全量替换',
 ms_project_auto_extract TINYINT DEFAULT 1 COMMENT '是否自动解压: 0否 1是',
 ms_project_auto_start TINYINT DEFAULT 0 COMMENT '部署成功后是否自动执行启动脚本: 0否 1是',
 ms_project_health_enabled TINYINT DEFAULT 0 COMMENT '部署后健康检查: 0否 1是',
 ms_project_health_type VARCHAR(16) DEFAULT 'PORT' COMMENT '健康检查类型: PORT/HTTP/PROCESS',
 ms_project_health_target VARCHAR(512) DEFAULT NULL COMMENT '健康检查目标(端口/URL/进程)',
 ms_project_health_timeout INT DEFAULT 30 COMMENT '健康检查超时(秒)',
 ms_project_auto_rollback TINYINT DEFAULT 0 COMMENT '失败自动回滚: 0否 1是',
 ms_project_deploy_retry INT DEFAULT 0 COMMENT '单台失败重试次数',
 ms_project_archive_keep INT DEFAULT 0 COMMENT '归档保留数量(0不清理)',
 ms_project_current_version VARCHAR(256) DEFAULT NULL COMMENT '当前版本(最近一次部署包)',
 ms_project_runtime_status VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT '运行时状态: UNKNOWN/RUNNING/STOPPED/DEPLOYING/UPGRADING/RESTARTING/HEALTHY/UNHEALTHY',
 ms_project_enabled TINYINT DEFAULT 1 COMMENT '启用状态: 0停用 1启用',
 ms_project_sort INT DEFAULT 100 COMMENT '排序',
 ms_project_remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
 create_by INT DEFAULT NULL COMMENT '创建人',
 create_name VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_name VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 update_by INT DEFAULT NULL COMMENT '更新人',
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发布项目台账';

CREATE UNIQUE INDEX IF NOT EXISTS uk_project_code ON ms_project (ms_project_code);
CREATE INDEX IF NOT EXISTS idx_project_name ON ms_project (ms_project_name);
CREATE INDEX IF NOT EXISTS idx_project_type ON ms_project (ms_project_type);











-- 部署日志
CREATE TABLE IF NOT EXISTS ms_project_deploy_log (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_project_deploy_log_project_id BIGINT NOT NULL COMMENT '关联项目ID',
 ms_project_deploy_log_project_name VARCHAR(128) DEFAULT NULL COMMENT '项目名称(冗余)',
 ms_project_deploy_log_host_id BIGINT DEFAULT NULL COMMENT '目标主机ID',
 ms_project_deploy_log_host_name VARCHAR(128) DEFAULT NULL COMMENT '主机名称(冗余)',
 ms_project_deploy_log_action VARCHAR(32) NOT NULL COMMENT '操作类型: DEPLOY/UPGRADE/START/STOP/RESTART/ROLLBACK/WATCHDOG/WATCHDOG-CHECK/WATCHDOG-ALERT/METRIC',
 ms_project_deploy_log_version VARCHAR(256) DEFAULT NULL COMMENT '版本标识(部署包文件名)',
 ms_project_deploy_log_status VARCHAR(16) NOT NULL COMMENT '执行状态: SUCCESS/FAIL/RUNNING',
 ms_project_deploy_log_output LONGTEXT DEFAULT NULL COMMENT '执行输出',
 ms_project_deploy_log_error TEXT DEFAULT NULL COMMENT '错误信息',
 ms_project_deploy_log_start_time DATETIME DEFAULT NULL COMMENT '开始时间',
 ms_project_deploy_log_end_time DATETIME DEFAULT NULL COMMENT '结束时间',
 ms_project_deploy_log_cost_ms BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
 create_by INT DEFAULT NULL COMMENT '创建人',
 create_name VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_name VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 update_by INT DEFAULT NULL COMMENT '更新人',
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署日志';

CREATE INDEX IF NOT EXISTS idx_deploy_log_project ON ms_project_deploy_log (ms_project_deploy_log_project_id);
CREATE INDEX IF NOT EXISTS idx_deploy_log_host ON ms_project_deploy_log (ms_project_deploy_log_host_id);
CREATE INDEX IF NOT EXISTS idx_deploy_log_action ON ms_project_deploy_log (ms_project_deploy_log_action);











-- 部署包归档：打通「资产 → 部署包 → 部署目录」链路（版本追溯/回滚支撑）
CREATE TABLE IF NOT EXISTS ms_project_upload_archive (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_project_upload_archive_project_id BIGINT NOT NULL COMMENT '关联项目ID',
 ms_project_upload_archive_project_name VARCHAR(128) DEFAULT NULL COMMENT '项目名称(冗余)',
 ms_project_upload_archive_host_id BIGINT DEFAULT NULL COMMENT '目标主机ID(资产维度锚点)',
 ms_project_upload_archive_host_name VARCHAR(128) DEFAULT NULL COMMENT '主机名称(冗余)',
 ms_project_upload_archive_action VARCHAR(16) NOT NULL COMMENT '操作类型: DEPLOY/UPGRADE/ROLLBACK',
 ms_project_upload_archive_version VARCHAR(256) NOT NULL COMMENT '版本标识(原始包文件名)',
 ms_project_upload_archive_type VARCHAR(32) DEFAULT NULL COMMENT '包类型: SINGLE_FILE/COMPRESSED',
 ms_project_upload_archive_upload_path VARCHAR(512) DEFAULT NULL COMMENT '归档存储路径',
 ms_project_upload_archive_deploy_dir VARCHAR(512) DEFAULT NULL COMMENT '末端部署目录',
 ms_project_upload_archive_file_size BIGINT DEFAULT NULL COMMENT '包大小(字节)',
 ms_project_upload_archive_md5 VARCHAR(64) DEFAULT NULL COMMENT '包MD5',
 ms_project_upload_archive_status VARCHAR(16) DEFAULT NULL COMMENT '部署结果: SUCCESS/FAIL',
 ms_project_upload_archive_deploy_time DATETIME DEFAULT NULL COMMENT '部署时间',
 create_by INT DEFAULT NULL COMMENT '创建人',
 create_name VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_name VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 update_by INT DEFAULT NULL COMMENT '更新人',
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署包归档';

CREATE INDEX IF NOT EXISTS idx_archive_project ON ms_project_upload_archive (ms_project_upload_archive_project_id);
CREATE INDEX IF NOT EXISTS idx_archive_host ON ms_project_upload_archive (ms_project_upload_archive_host_id);
CREATE INDEX IF NOT EXISTS idx_archive_version ON ms_project_upload_archive (ms_project_upload_archive_version);
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_log_dir VARCHAR(512) NULL;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_log_file VARCHAR(256) NULL;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_auto_start TINYINT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_enabled TINYINT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_type VARCHAR(16) DEFAULT;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_target VARCHAR(512) NULL;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_health_timeout INT DEFAULT 30;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_auto_rollback TINYINT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_deploy_retry INT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_archive_keep INT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_watchdog_enabled TINYINT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_watchdog_interval INT DEFAULT 30;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_watchdog_max_fail INT DEFAULT 1;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_check_script TEXT NULL;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_preflight_gate TINYINT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_metric_enabled TINYINT DEFAULT 0;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_metric_interval INT DEFAULT 60;
ALTER TABLE ms_project ADD COLUMN IF NOT EXISTS ms_project_metric_host_ids VARCHAR(1024) NULL;











-- 项目指标时间序列（采集结果持久化，供前端图表拉历史；由采集器幂等建表兜底）
CREATE TABLE IF NOT EXISTS ms_project_metric (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 project_id BIGINT NOT NULL COMMENT '关联项目ID',
 host_id BIGINT DEFAULT NULL COMMENT '目标主机ID',
 metric_name VARCHAR(64) NOT NULL COMMENT '指标名: project.process.mem.mb / project.process.cpu / project.port.alive / project.http.code',
 metric_value DOUBLE DEFAULT NULL COMMENT '指标值',
 collect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目指标时间序列';

CREATE INDEX IF NOT EXISTS idx_pm_project ON ms_project_metric (project_id);
CREATE INDEX IF NOT EXISTS idx_pm_metric ON ms_project_metric (metric_name);












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_monitor.sql
-- ---------------------------------------------------------------------
-- ============================================================
-- 监控模块初始化 SQL（合并版）
-- 模块: spring-support-monitor-starter
-- 来源: 原 V1.0__init_monitor + V1.1__alarm_and_agent + V1.2__server_node + V1.3__monitor_host_link
-- 命名规范: 列名使用完整表名作为前缀，如 ms_monitor_name
-- 数据库: MySQL 8.x / H2 (MySQL 模式) / 达梦 / Oracle 通过方言转换
-- 幂等: 建表 IF NOT EXISTS，补列 IF NOT EXISTS，重复执行安全
-- ============================================================

SET NAMES utf8mb4;











SET FOREIGN_KEY_CHECKS = 0;











-- ----------------------------------------------------------
-- 1. 资产主表(主机视图)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_name` VARCHAR(128) NOT NULL COMMENT '名称',
 `ms_monitor_host` VARCHAR(256) NOT NULL COMMENT '主机地址 / IP',
 `ms_monitor_port` INT DEFAULT 22 COMMENT '连接端口(SSH 22 / Redis 6379 / MySQL 3306 等)',
 `ms_monitor_service_type` VARCHAR(32) NOT NULL COMMENT '服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ',
 `ms_monitor_server_type` VARCHAR(32) DEFAULT NULL COMMENT '服务器子类型(仅 SERVER): LINUX / WINDOWS / MACOS / JVM',
 `ms_monitor_server_os_version` VARCHAR(64) DEFAULT NULL COMMENT '操作系统版本',
 `ms_monitor_group` VARCHAR(64) DEFAULT NULL COMMENT '分组',
 `ms_monitor_description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
 `ms_monitor_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用 1启用 0禁用',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控资产';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_service ON ms_monitor (`ms_monitor_service_type`);
CREATE INDEX IF NOT EXISTS idx_ms_monitor_enabled ON ms_monitor (`ms_monitor_enabled`);











-- ----------------------------------------------------------
-- 2. 全局配置
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_config` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_config_key` VARCHAR(64) NOT NULL COMMENT '配置项 key',
 `ms_monitor_config_value` LONGTEXT DEFAULT NULL COMMENT '配置项 value',
 `ms_monitor_config_desc` VARCHAR(255) DEFAULT NULL COMMENT '配置项描述',
 `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控全局配置';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ms_monitor_config_key ON ms_monitor_config (`ms_monitor_config_key`);











-- ----------------------------------------------------------
-- 3. 采集任务
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_task` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_task_monitor_id` BIGINT NOT NULL COMMENT '关联 ms_monitor.id',
 `ms_monitor_task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
 `ms_monitor_task_service_type` VARCHAR(32) NOT NULL COMMENT '服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ',
 `ms_monitor_task_provider` VARCHAR(32) NOT NULL COMMENT '采集方式: LOCAL / TERMINAL / HTTP / PROMETHEUS',
 `ms_monitor_task_collect_config` LONGTEXT NOT NULL COMMENT '采集参数 JSON',
 `ms_monitor_task_cron` VARCHAR(64) NOT NULL COMMENT 'cron 表达式',
 `ms_monitor_task_timeout` INT DEFAULT 5000 COMMENT '超时(毫秒)',
 `ms_monitor_task_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
 `ms_monitor_task_remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
 `ms_monitor_task_last_run_time` DATETIME DEFAULT NULL COMMENT '最近一次触发时间',
 `create_name` VARCHAR(64) DEFAULT NULL,
 `create_by` VARCHAR(64) DEFAULT NULL,
 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
 `update_name` VARCHAR(64) DEFAULT NULL,
 `update_by` VARCHAR(64) DEFAULT NULL,
 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控采集任务';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_task_monitor ON ms_monitor_task (`ms_monitor_task_monitor_id`);
CREATE INDEX IF NOT EXISTS idx_ms_monitor_task_sp ON ms_monitor_task (`ms_monitor_task_service_type`, `ms_monitor_task_provider`);
CREATE INDEX IF NOT EXISTS idx_ms_monitor_task_enabled ON ms_monitor_task (`ms_monitor_task_enabled`);











-- ----------------------------------------------------------
-- 4. 解析规则 (任务 1:N)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_config` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_config_task_id` BIGINT NOT NULL COMMENT '关联 ms_monitor_task.id',
 `ms_monitor_metric_config_metric_name` VARCHAR(64) NOT NULL COMMENT '指标名(存入时序的指标名称)',
 `ms_monitor_metric_config_target_table` VARCHAR(64) NOT NULL COMMENT '目标表: ms_monitor_metric / ms_monitor_metric_cpu / ...',
 `ms_monitor_metric_config_parse_rule` LONGTEXT NOT NULL COMMENT '解析规则 JSON, 如 {type:regex, pattern, group, unit}',
 `ms_monitor_metric_config_metric_unit` VARCHAR(16) DEFAULT NULL COMMENT '指标单位',
 `ms_monitor_metric_config_remark` VARCHAR(255) DEFAULT NULL,
 `create_name` VARCHAR(64) DEFAULT NULL,
 `create_by` VARCHAR(64) DEFAULT NULL,
 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
 `update_name` VARCHAR(64) DEFAULT NULL,
 `update_by` VARCHAR(64) DEFAULT NULL,
 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控解析规则';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_config_task ON ms_monitor_metric_config (`ms_monitor_metric_config_task_id`);











-- ----------------------------------------------------------
-- 5. Guacamole 网关
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_gateway` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_gateway_monitor_id` BIGINT NOT NULL COMMENT '关联 ms_monitor.id',
 `ms_monitor_gateway_name` VARCHAR(128) NOT NULL COMMENT '网关名称',
 `ms_monitor_gateway_type` VARCHAR(32) DEFAULT 'GUACAMOLE' COMMENT '网关类型: GUACAMOLE / SSH / WEB',
 `ms_monitor_gateway_host` VARCHAR(256) DEFAULT NULL COMMENT '网关主机',
 `ms_monitor_gateway_port` INT DEFAULT NULL COMMENT '网关端口',
 `ms_monitor_gateway_config_url` VARCHAR(512) DEFAULT NULL COMMENT '连接配置 URL',
 `ms_monitor_gateway_username` VARCHAR(64) DEFAULT NULL,
 `ms_monitor_gateway_password` VARCHAR(255) DEFAULT NULL COMMENT '密码密文',
 `ms_monitor_gateway_enabled` TINYINT(1) DEFAULT 1,
 `ms_monitor_gateway_remark` VARCHAR(255) DEFAULT NULL,
 `create_name` VARCHAR(64) DEFAULT NULL,
 `create_by` VARCHAR(64) DEFAULT NULL,
 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
 `update_name` VARCHAR(64) DEFAULT NULL,
 `update_by` VARCHAR(64) DEFAULT NULL,
 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控网关';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_gateway_monitor ON ms_monitor_gateway (`ms_monitor_gateway_monitor_id`);











-- ----------------------------------------------------------
-- 6. 通用 server 时序指标
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_monitor_id` BIGINT NOT NULL COMMENT 'ms_monitor.id',
 `ms_monitor_metric_metric_name` VARCHAR(64) NOT NULL COMMENT '指标名',
 `ms_monitor_metric_metric_value` DOUBLE NOT NULL COMMENT '指标值',
 `ms_monitor_metric_metric_tags` VARCHAR(1024) DEFAULT NULL COMMENT '标签 JSON',
 `ms_monitor_metric_collect_time` DATETIME NOT NULL COMMENT '采集时间',
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控通用时序';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_sm ON ms_monitor_metric (`ms_monitor_metric_monitor_id`, `ms_monitor_metric_metric_name`, `ms_monitor_metric_collect_time`);











-- ----------------------------------------------------------
-- 7. server 指标小时聚合
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_statistics` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_statistics_monitor_id` BIGINT NOT NULL COMMENT 'ms_monitor.id',
 `ms_monitor_metric_statistics_metric_name` VARCHAR(64) NOT NULL COMMENT '指标名',
 `ms_monitor_metric_statistics_hour_bucket` DATETIME NOT NULL COMMENT '小时桶',
 `ms_monitor_metric_statistics_avg_value` DOUBLE DEFAULT NULL,
 `ms_monitor_metric_statistics_min_value` DOUBLE DEFAULT NULL,
 `ms_monitor_metric_statistics_max_value` DOUBLE DEFAULT NULL,
 `ms_monitor_metric_statistics_count_value` BIGINT DEFAULT 0 COMMENT '样本数',
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控小时聚合';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ms_monitor_metric_statistics_bucket ON ms_monitor_metric_statistics (`ms_monitor_metric_statistics_monitor_id`, `ms_monitor_metric_statistics_metric_name`, `ms_monitor_metric_statistics_hour_bucket`);











-- ----------------------------------------------------------
-- 8. CPU 明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_cpu` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_cpu_monitor_id` BIGINT NOT NULL,
 `ms_monitor_metric_cpu_cpu_model` VARCHAR(128) DEFAULT NULL COMMENT 'CPU 型号',
 `ms_monitor_metric_cpu_cpu_cores` INT DEFAULT NULL COMMENT '核数',
 `ms_monitor_metric_cpu_cpu_usage` DOUBLE DEFAULT NULL COMMENT '使用率 0-100',
 `ms_monitor_metric_cpu_load1` DOUBLE DEFAULT NULL COMMENT '1 分钟平均负载',
 `ms_monitor_metric_cpu_load5` DOUBLE DEFAULT NULL COMMENT '5 分钟平均负载',
 `ms_monitor_metric_cpu_load15` DOUBLE DEFAULT NULL COMMENT '15 分钟平均负载',
 `ms_monitor_metric_cpu_collect_time` DATETIME NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控 CPU 明细';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_cpu_mt ON ms_monitor_metric_cpu (`ms_monitor_metric_cpu_monitor_id`, `ms_monitor_metric_cpu_collect_time`);











-- ----------------------------------------------------------
-- 9. 内存明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_memory` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_memory_monitor_id` BIGINT NOT NULL,
 `ms_monitor_metric_memory_total_bytes` BIGINT DEFAULT NULL COMMENT '总内存(字节)',
 `ms_monitor_metric_memory_used_bytes` BIGINT DEFAULT NULL COMMENT '已用(字节)',
 `ms_monitor_metric_memory_free_bytes` BIGINT DEFAULT NULL COMMENT '空闲(字节)',
 `ms_monitor_metric_memory_usage_percent` DOUBLE DEFAULT NULL COMMENT '使用率 0-100',
 `ms_monitor_metric_memory_collect_time` DATETIME NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控内存明细';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_memory_mt ON ms_monitor_metric_memory (`ms_monitor_metric_memory_monitor_id`, `ms_monitor_metric_memory_collect_time`);











-- ----------------------------------------------------------
-- 10. 磁盘明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_disk` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_disk_monitor_id` BIGINT NOT NULL,
 `ms_monitor_metric_disk_mount_point` VARCHAR(128) DEFAULT NULL COMMENT '挂载点',
 `ms_monitor_metric_disk_file_system` VARCHAR(64) DEFAULT NULL COMMENT '文件系统',
 `ms_monitor_metric_disk_total_bytes` BIGINT DEFAULT NULL,
 `ms_monitor_metric_disk_used_bytes` BIGINT DEFAULT NULL,
 `ms_monitor_metric_disk_usage_percent` DOUBLE DEFAULT NULL,
 `ms_monitor_metric_disk_collect_time` DATETIME NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控磁盘明细';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_disk_mt ON ms_monitor_metric_disk (`ms_monitor_metric_disk_monitor_id`, `ms_monitor_metric_disk_collect_time`);











-- ----------------------------------------------------------
-- 11. 网络明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_network` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_network_monitor_id` BIGINT NOT NULL,
 `ms_monitor_metric_network_interface_name` VARCHAR(64) DEFAULT NULL COMMENT '网卡名',
 `ms_monitor_metric_network_rx_bytes` BIGINT DEFAULT NULL COMMENT '接收字节数',
 `ms_monitor_metric_network_tx_bytes` BIGINT DEFAULT NULL COMMENT '发送字节数',
 `ms_monitor_metric_network_speed_bps` BIGINT DEFAULT NULL COMMENT '网卡速率(bps)',
 `ms_monitor_metric_network_collect_time` DATETIME NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控网络明细';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_network_mt ON ms_monitor_metric_network (`ms_monitor_metric_network_monitor_id`, `ms_monitor_metric_network_collect_time`);











-- ----------------------------------------------------------
-- 12. JVM 明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_jvm` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_metric_jvm_monitor_id` BIGINT NOT NULL,
 `ms_monitor_metric_jvm_heap_used_bytes` BIGINT DEFAULT NULL,
 `ms_monitor_metric_jvm_heap_max_bytes` BIGINT DEFAULT NULL,
 `ms_monitor_metric_jvm_heap_usage_percent` DOUBLE DEFAULT NULL,
 `ms_monitor_metric_jvm_young_gc_count` BIGINT DEFAULT NULL,
 `ms_monitor_metric_jvm_full_gc_count` BIGINT DEFAULT NULL,
 `ms_monitor_metric_jvm_threads` INT DEFAULT NULL,
 `ms_monitor_metric_jvm_daemon_threads` INT DEFAULT NULL,
 `ms_monitor_metric_jvm_collect_time` DATETIME NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控 JVM 明细';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_metric_jvm_mt ON ms_monitor_metric_jvm (`ms_monitor_metric_jvm_monitor_id`, `ms_monitor_metric_jvm_collect_time`);











-- ----------------------------------------------------------
-- 13. Redis 时序指标
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_redis_metric` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_redis_metric_monitor_id` BIGINT NOT NULL COMMENT 'ms_monitor.id(REDIS 类型)',
 `ms_monitor_redis_metric_metric_name` VARCHAR(64) NOT NULL,
 `ms_monitor_redis_metric_metric_value` DOUBLE NOT NULL,
 `ms_monitor_redis_metric_metric_tags` VARCHAR(1024) DEFAULT NULL,
 `ms_monitor_redis_metric_collect_time` DATETIME NOT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控 Redis 时序';

CREATE INDEX IF NOT EXISTS idx_ms_monitor_redis_metric_sm ON ms_monitor_redis_metric (`ms_monitor_redis_metric_monitor_id`, `ms_monitor_redis_metric_metric_name`, `ms_monitor_redis_metric_collect_time`);











-- ----------------------------------------------------------
-- 14. Redis 小时聚合
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_redis_metric_statistics` (
 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `ms_monitor_redis_metric_statistics_monitor_id` BIGINT NOT NULL,
 `ms_monitor_redis_metric_statistics_metric_name` VARCHAR(64) NOT NULL,
 `ms_monitor_redis_metric_statistics_hour_bucket` DATETIME NOT NULL,
 `ms_monitor_redis_metric_statistics_avg_value` DOUBLE DEFAULT NULL,
 `ms_monitor_redis_metric_statistics_min_value` DOUBLE DEFAULT NULL,
 `ms_monitor_redis_metric_statistics_max_value` DOUBLE DEFAULT NULL,
 `ms_monitor_redis_metric_statistics_count_value` BIGINT DEFAULT 0,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控 Redis 小时聚合';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ms_monitor_redis_metric_statistics_bucket ON ms_monitor_redis_metric_statistics (`ms_monitor_redis_metric_statistics_monitor_id`, `ms_monitor_redis_metric_statistics_metric_name`, `ms_monitor_redis_metric_statistics_hour_bucket`);











-- ----------------------------------------------------------
-- 15. 告警策略
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_policy (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_monitor_alarm_policy_name VARCHAR(64) NOT NULL COMMENT '策略名称',
 ms_monitor_alarm_policy_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态 1启用 0停用',
 ms_monitor_alarm_policy_remark VARCHAR(255) NULL COMMENT '备注',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警策略';

















-- ----------------------------------------------------------
-- 16. 告警规则
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_rule (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_monitor_alarm_rule_policy_id BIGINT NOT NULL COMMENT '所属策略ID',
 ms_monitor_alarm_rule_monitor_id BIGINT NULL COMMENT '限定目标ID,空为全部目标',
 ms_monitor_alarm_rule_metric_name VARCHAR(64) NOT NULL COMMENT '指标名',
 ms_monitor_alarm_rule_condition VARCHAR(4) NOT NULL DEFAULT 'GT' COMMENT '条件: GT/GE/EQ/LE/LT',
 ms_monitor_alarm_rule_threshold DECIMAL(19,4) NOT NULL DEFAULT 0 COMMENT '阈值',
 ms_monitor_alarm_rule_consecutive_count INT NOT NULL DEFAULT 1 COMMENT '连续触发次数',
 ms_monitor_alarm_rule_silence_minutes INT NOT NULL DEFAULT 10 COMMENT '静默期(分钟)',
 ms_monitor_alarm_rule_level CHAR(2) NULL DEFAULT 'P2' COMMENT '级别: P0~P4',
 ms_monitor_alarm_rule_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态 1启用 0停用',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警规则';

CREATE INDEX IF NOT EXISTS idx_alarm_rule_policy ON ms_monitor_alarm_rule (ms_monitor_alarm_rule_policy_id);
CREATE INDEX IF NOT EXISTS idx_alarm_rule_metric ON ms_monitor_alarm_rule (ms_monitor_alarm_rule_metric_name);











-- ----------------------------------------------------------
-- 17. 告警事件
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_event (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_monitor_alarm_event_monitor_id BIGINT NULL COMMENT '监控目标ID',
 ms_monitor_alarm_event_monitor_name VARCHAR(64) NULL COMMENT '目标名称(冗余)',
 ms_monitor_alarm_event_rule_id BIGINT NULL COMMENT '命中规则ID',
 ms_monitor_alarm_event_metric_name VARCHAR(64) NOT NULL COMMENT '指标名',
 ms_monitor_alarm_event_alarm_value DECIMAL(19,4) NULL COMMENT '实际值',
 ms_monitor_alarm_event_threshold DECIMAL(19,4) NULL COMMENT '阈值',
 ms_monitor_alarm_event_level CHAR(2) NULL DEFAULT 'P2' COMMENT '级别: P0~P4',
 ms_monitor_alarm_event_status VARCHAR(12) NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/PROCESSED/FALSE_ALARM',
 ms_monitor_alarm_event_handle_remark VARCHAR(512) NULL COMMENT '处理备注',
 ms_monitor_alarm_event_trigger_time DATETIME NULL COMMENT '触发时间',
 ms_monitor_alarm_event_handle_time DATETIME NULL COMMENT '处理时间',
 ms_monitor_alarm_event_rule_snapshot VARCHAR(1024) NULL COMMENT '触发时规则配置快照(JSON)',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警事件';

CREATE INDEX IF NOT EXISTS idx_alarm_event_monitor ON ms_monitor_alarm_event (ms_monitor_alarm_event_monitor_id);
CREATE INDEX IF NOT EXISTS idx_alarm_event_status ON ms_monitor_alarm_event (ms_monitor_alarm_event_status);











-- ----------------------------------------------------------
-- 18. 告警维护窗口
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_maintenance_window (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_monitor_alarm_maintenance_window_policy_id BIGINT NULL COMMENT '策略ID, 空为全局',
 ms_monitor_alarm_maintenance_window_name VARCHAR(64) NULL COMMENT '窗口名称',
 ms_monitor_alarm_maintenance_window_level CHAR(2) NULL COMMENT '级别: P0~P4/空为全局',
 ms_monitor_alarm_maintenance_window_start_time VARCHAR(8) NULL COMMENT '开始时间 HH:mm',
 ms_monitor_alarm_maintenance_window_end_time VARCHAR(8) NULL COMMENT '结束时间 HH:mm',
 ms_monitor_alarm_maintenance_window_weekdays VARCHAR(32) NULL COMMENT '生效星期, 逗号分隔, 空为每天',
 ms_monitor_alarm_maintenance_window_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用 1启用 0停用',
 ms_monitor_alarm_maintenance_window_remark VARCHAR(255) NULL COMMENT '备注',
 ms_monitor_alarm_maintenance_window_create_time DATETIME NULL COMMENT '创建时间',
 ms_monitor_alarm_maintenance_window_update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警维护窗口';

CREATE INDEX IF NOT EXISTS idx_alarm_mw_enabled ON ms_monitor_alarm_maintenance_window (ms_monitor_alarm_maintenance_window_enabled);











-- ----------------------------------------------------------
-- 19. 协议服务器节点(服务管理)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_node (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_node_name VARCHAR(64) NOT NULL COMMENT '节点名称(唯一)',
 ms_server_node_protocol VARCHAR(32) NOT NULL COMMENT '协议类型: HTTP / HTTP_PROXY / TCP_PROXY / SIP',
 ms_server_node_engine VARCHAR(32) DEFAULT 'jdk' COMMENT '传输引擎: http= jdk/nio/aio, 代理= jdk, sip= sip',
 ms_server_node_host VARCHAR(128) DEFAULT '0.0.0.0' COMMENT '监听地址',
 ms_server_node_port INT NOT NULL COMMENT '监听端口',
 ms_server_node_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 1 启用(随应用自动拉起), 0 停用',
 ms_server_node_remark VARCHAR(255) NULL COMMENT '备注',
 ms_server_node_config TEXT NULL COMMENT '协议专属配置(JSON): sip.token, proxy.backendHost/backendPort 等',
 ms_server_node_filters TEXT NULL COMMENT 'ServerFilter 插件配置(JSON 数组)',
 create_name VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名',
 create_by INT DEFAULT NULL COMMENT '创建人',
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 update_name VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名',
 update_by INT DEFAULT NULL COMMENT '更新人',
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务管理-协议服务器节点';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ms_server_node_name ON ms_server_node (ms_server_node_name);
ALTER TABLE ms_monitor ADD COLUMN IF NOT EXISTS ms_monitor_agent_key VARCHAR(64) NULL;
ALTER TABLE ms_monitor ADD COLUMN IF NOT EXISTS ms_server_host_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_ms_monitor_server_host ON ms_monitor (ms_server_host_id);
ALTER TABLE ms_monitor_alarm_event ADD COLUMN IF NOT EXISTS ms_monitor_alarm_event_rule_snapshot VARCHAR(1024) NULL;











SET FOREIGN_KEY_CHECKS = 1;












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_server.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- 服务器资产模块 DDL 初始化脚本 (spring-support-server-starter) 合并版
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明: 表名与列名均以 ms_server_* 前缀组织, 与监控模块(ms_monitor_*)风格一致
-- 本文件为唯一 DDL: 全部建表 + 主机延迟检测补列 + 系统配置初始化数据
-- 来源: 原 V1.0__init_server + V1.1__add_host_delay_detect + V1.0__initdata_server
-- 幂等: 建表 IF NOT EXISTS, 补列 IF NOT EXISTS, INSERT IGNORE
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 服务器分组(树形)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_group (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_group_parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分组ID, 0为根',
 ms_server_group_name VARCHAR(64) NOT NULL COMMENT '分组名称',
 ms_server_group_sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
 ms_server_group_remark VARCHAR(255) NULL COMMENT '备注',
 ms_server_group_color VARCHAR(32) NULL COMMENT '分组颜色(十六进制,如 #6d5dfc)',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器分组';

CREATE INDEX IF NOT EXISTS idx_server_group_parent ON ms_server_group (ms_server_group_parent_id);











-- ---------------------------------------------------------------------
-- 2. SSH 密钥库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_key (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_key_name VARCHAR(64) NOT NULL COMMENT '密钥名称',
 ms_server_key_type VARCHAR(16) NULL COMMENT '密钥类型: RSA/ED25519/ECDSA',
 ms_server_key_public_key TEXT NULL COMMENT '公钥内容(明文)',
 ms_server_key_private_key TEXT NULL COMMENT '私钥内容(AES加密)',
 ms_server_key_password VARCHAR(512) NULL COMMENT '私钥口令(AES加密)',
 ms_server_key_remark VARCHAR(255) NULL COMMENT '备注',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SSH密钥库';

















-- ---------------------------------------------------------------------
-- 3. 登录身份
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_identity (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_identity_name VARCHAR(64) NOT NULL COMMENT '身份名称',
 ms_server_identity_auth_type VARCHAR(16) NOT NULL DEFAULT 'PASSWORD' COMMENT '认证类型: PASSWORD/PUBLIC_KEY/MAILBOX',
 ms_server_identity_username VARCHAR(64) NOT NULL COMMENT '登录用户名',
 ms_server_identity_password VARCHAR(512) NULL COMMENT '登录密码(AES加密)',
 ms_server_identity_key_id BIGINT NULL COMMENT '引用密钥ID(PUBLIC_KEY类型)',
 ms_server_identity_email_source VARCHAR(16) NULL DEFAULT 'IMAP' COMMENT '邮箱来源(MAILBOX类型): IMAP(真实邮箱)/TEMP(临时邮箱API)',
 ms_server_identity_provider VARCHAR(32) NULL COMMENT '临时邮箱服务商(TEMP来源): fce/luckmail/mailnest/mailtm',
 ms_server_identity_imap_host VARCHAR(128) NULL COMMENT '邮箱IMAP服务器(MAILBOX类型)',
 ms_server_identity_imap_port INT NULL COMMENT '邮箱IMAP端口(MAILBOX类型,默认993)',
 ms_server_identity_remark VARCHAR(255) NULL COMMENT '备注',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录身份';

CREATE INDEX IF NOT EXISTS idx_server_identity_key ON ms_server_identity (ms_server_identity_key_id);











-- ---------------------------------------------------------------------
-- 4. 服务器主机台账
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_host (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_host_name VARCHAR(64) NOT NULL COMMENT '主机名称',
 ms_server_host_code VARCHAR(64) NULL COMMENT '主机编码',
 ms_server_host_address VARCHAR(128) NOT NULL COMMENT '主机地址',
 ms_server_host_port INT NOT NULL DEFAULT 22 COMMENT 'SSH端口',
 ms_server_host_os_type VARCHAR(16) NOT NULL DEFAULT 'LINUX' COMMENT '系统类型: LINUX/WINDOWS/MACOS',
 ms_server_host_os_arch VARCHAR(32) NULL COMMENT '系统架构',
 ms_server_group_id BIGINT NULL COMMENT '所属分组ID',
 ms_server_host_tags VARCHAR(512) NULL COMMENT '标签(JSON数组)',
 ms_server_host_status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态 1启用 0停用',
 ms_server_host_online TINYINT NOT NULL DEFAULT -1 COMMENT '在线状态 1在线 0离线 -1未知',
 ms_server_host_online_time DATETIME NULL COMMENT '最近在线时间',
 ms_server_host_agent_key CHAR(32) NULL COMMENT '探针标识',
 ms_server_host_description VARCHAR(255) NULL COMMENT '描述',
 ms_server_host_cpu_cores INT NULL COMMENT 'CPU核心数',
 ms_server_host_memory_mb BIGINT NULL COMMENT '内存容量(MB)',
 ms_server_host_disk_gb BIGINT NULL COMMENT '磁盘容量(GB)',
 ms_server_host_os_version VARCHAR(128) NULL COMMENT '系统版本',
 ms_server_host_public_ip VARCHAR(64) NULL COMMENT '公网IP',
 ms_server_host_private_ip VARCHAR(64) NULL COMMENT '内网IP',
 ms_server_host_online_enabled INT NULL DEFAULT 1 COMMENT '在离线检测开关(主机级)',
 ms_server_host_online_interval INT NULL DEFAULT 60 COMMENT '在离线检测间隔秒(主机级)',
 ms_server_host_collect_enabled INT NULL DEFAULT 0 COMMENT '硬件采集开关(主机级)',
 ms_server_host_collect_mode VARCHAR(16) NULL DEFAULT 'SCHEDULED' COMMENT '采集方式(主机级)',
 ms_server_host_collect_interval INT NULL DEFAULT 3600 COMMENT '采集间隔秒(主机级)',
 ms_server_host_last_collect_time DATETIME NULL COMMENT '最近采集时间(主机级)',
 ms_server_host_agent_enabled INT NULL DEFAULT 0 COMMENT 'Agent上报开关(主机级)',
 ms_server_host_remote_enabled INT NULL DEFAULT 1 COMMENT '远控开关(主机级)',
 ms_server_host_delay_enabled INT NULL DEFAULT 0 COMMENT '延迟检测开关(主机级)',
 ms_server_host_delay_interval INT NULL DEFAULT 60 COMMENT '延迟检测间隔秒(主机级)',
 ms_server_host_delay_ms BIGINT NULL COMMENT '最近延迟毫秒(检测结果)',
 ms_server_host_last_delay_time DATETIME NULL COMMENT '最近延迟检测时间(主机级)',
 ms_server_host_ports TEXT NULL COMMENT '端口扫描结果(JSON数组)',
 ms_server_host_last_port_scan_time DATETIME NULL COMMENT '最近端口扫描时间',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器主机';

CREATE UNIQUE INDEX IF NOT EXISTS uk_server_host_agent ON ms_server_host (ms_server_host_agent_key);
CREATE INDEX IF NOT EXISTS idx_server_host_group ON ms_server_host (ms_server_group_id);
CREATE INDEX IF NOT EXISTS idx_server_host_address ON ms_server_host (ms_server_host_address);











-- ---------------------------------------------------------------------
-- 5. 主机-身份关联
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_host_identity (
 id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_host_identity_host_id BIGINT NOT NULL COMMENT '主机ID',
 ms_server_host_identity_identity_id BIGINT NOT NULL COMMENT '身份ID',
 ms_server_host_identity_sort INT NOT NULL DEFAULT 1 COMMENT '优先级, 越小越优先',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '主机身份关联';

CREATE INDEX IF NOT EXISTS idx_host_identity_host ON ms_server_host_identity (ms_server_host_identity_host_id);
CREATE INDEX IF NOT EXISTS idx_host_identity_identity ON ms_server_host_identity (ms_server_host_identity_identity_id);











-- ---------------------------------------------------------------------
-- 6. 远程终端会话审计
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_terminal_session (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_terminal_session_host_id BIGINT NULL COMMENT '主机ID',
 ms_terminal_session_host_name VARCHAR(64) NULL COMMENT '主机名称',
 ms_terminal_session_protocol VARCHAR(8) NOT NULL COMMENT '协议: SSH/RDP/VNC/SFTP',
 ms_terminal_session_user VARCHAR(64) NULL COMMENT '发起用户',
 ms_terminal_session_client_ip VARCHAR(64) NULL COMMENT '客户端IP',
 ms_terminal_session_connect_time DATETIME NULL COMMENT '连接时间',
 ms_terminal_session_close_time DATETIME NULL COMMENT '关闭时间',
 ms_terminal_session_status VARCHAR(8) NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
 ms_terminal_session_close_reason VARCHAR(128) NULL COMMENT '关闭原因',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端会话审计';

CREATE INDEX IF NOT EXISTS idx_terminal_session_host ON ms_terminal_session (ms_terminal_session_host_id);
CREATE INDEX IF NOT EXISTS idx_terminal_session_status ON ms_terminal_session (ms_terminal_session_status);











-- ---------------------------------------------------------------------
-- 7. 终端命令片段
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_terminal_snippet (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_terminal_snippet_name VARCHAR(64) NOT NULL COMMENT '片段名称',
 ms_terminal_snippet_content TEXT NULL COMMENT '片段内容',
 ms_terminal_snippet_remark VARCHAR(255) NULL COMMENT '备注',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端命令片段';

















-- ---------------------------------------------------------------------
-- 8. 终端路径书签
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_terminal_bookmark (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_terminal_bookmark_host_id BIGINT NOT NULL COMMENT '主机ID',
 ms_terminal_bookmark_name VARCHAR(64) NULL COMMENT '书签名称',
 ms_terminal_bookmark_path VARCHAR(255)NOT NULL COMMENT '书签路径',
 ms_terminal_bookmark_sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '终端路径书签';

CREATE INDEX IF NOT EXISTS idx_terminal_bookmark_host ON ms_terminal_bookmark (ms_terminal_bookmark_host_id);











-- ---------------------------------------------------------------------
-- 9. 批量计划任务
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_exec_job (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_exec_job_name VARCHAR(64) NOT NULL COMMENT '任务名称',
 ms_exec_job_cron VARCHAR(32) NOT NULL COMMENT 'cron表达式(6域)',
 ms_exec_job_command TEXT NOT NULL COMMENT '执行命令',
 ms_exec_job_host_ids VARCHAR(512) NOT NULL COMMENT '目标主机ID集合(JSON数组)',
 ms_exec_job_timeout INT NOT NULL DEFAULT 60 COMMENT '单机超时(秒)',
 ms_exec_job_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态 1启用 0停用',
 ms_exec_job_last_run_time DATETIME NULL COMMENT '上次运行时间',
 ms_exec_job_next_run_time DATETIME NULL COMMENT '下次运行时间',
 ms_exec_job_remark VARCHAR(255) NULL COMMENT '备注',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '批量计划任务';

















-- ---------------------------------------------------------------------
-- 10. 批量执行日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_exec_log (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_exec_log_type VARCHAR(8) NOT NULL COMMENT '类型: COMMAND/FILE/JOB',
 ms_exec_log_host_id BIGINT NULL COMMENT '主机ID',
 ms_exec_log_host_name VARCHAR(64) NULL COMMENT '主机名称',
 ms_exec_log_job_id BIGINT NULL COMMENT '关联任务ID',
 ms_exec_log_command VARCHAR(512) NULL COMMENT '命令或文件路径',
 ms_exec_log_output LONGTEXT NULL COMMENT '执行输出(截断)',
 ms_exec_log_exit_code INT NULL COMMENT '退出码',
 ms_exec_log_status VARCHAR(8) NOT NULL COMMENT '状态: SUCCESS/FAIL',
 ms_exec_log_error VARCHAR(1024) NULL COMMENT '错误信息',
 ms_exec_log_start_time DATETIME NULL COMMENT '开始时间',
 ms_exec_log_end_time DATETIME NULL COMMENT '结束时间',
 ms_exec_log_cost_ms BIGINT NULL COMMENT '耗时(毫秒)',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '批量执行日志';

CREATE INDEX IF NOT EXISTS idx_exec_log_host ON ms_exec_log (ms_exec_log_host_id);
CREATE INDEX IF NOT EXISTS idx_exec_log_job ON ms_exec_log (ms_exec_log_job_id);
CREATE INDEX IF NOT EXISTS idx_exec_log_type ON ms_exec_log (ms_exec_log_type);











-- ---------------------------------------------------------------------
-- 11. 服务器系统配置 (key-value)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_config (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 ms_server_config_key VARCHAR(128) NOT NULL COMMENT '配置键',
 ms_server_config_value VARCHAR(2048) NULL COMMENT '配置值(JSON字符串)',
 ms_server_config_desc VARCHAR(255) NULL COMMENT '配置说明',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务器系统配置';

CREATE UNIQUE INDEX IF NOT EXISTS uk_server_config_key ON ms_server_config (ms_server_config_key);











-- 免费代理（每次获取免费代理时清空重录——页面从本表读取）
CREATE TABLE IF NOT EXISTS ms_proxy (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 host VARCHAR(64) NOT NULL COMMENT '代理主机',
 port INT NOT NULL COMMENT '代理端口',
 protocol VARCHAR(16) NULL COMMENT '协议（Http/Https/Socks4/Socks5）',
 source VARCHAR(64) NULL COMMENT '来源',
 create_by INT NULL COMMENT '创建人',
 create_name VARCHAR(64) NULL COMMENT '创建人名称',
 create_time DATETIME NULL COMMENT '创建时间',
 update_name VARCHAR(64) NULL COMMENT '更新人名称',
 update_by INT NULL COMMENT '更新人',
 update_time DATETIME NULL COMMENT '更新时间',
 PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '免费代理';

CREATE INDEX IF NOT EXISTS idx_proxy_host_port ON ms_proxy (host, port);
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_delay_enabled INT NULL DEFAULT 0;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_delay_interval INT NULL DEFAULT 60;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_delay_ms BIGINT NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_last_delay_time DATETIME NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_ports TEXT NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_last_port_scan_time DATETIME NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_gpu_count INT NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_gpu_model VARCHAR(256) NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_networks TEXT NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_cpu_usage DECIMAL(5,2) NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_mem_usage DECIMAL(5,2) NULL;
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_disk_usage DECIMAL(5,2) NULL;









-- [通用化] 原动态块无法自动改写，保留为注释（MySQL 老库补列需 DBA 手工执行）：
-- -- ===================================================================== -- AI 模型扩展列与用量同步日志（并入自 V1.2__add_model_pipeline_and_usage_sync_log.sql -- / V1.3__add_model_aliases.sql / V1.4__add_model_latency_test.sql） -- 说明: sys_ai_model / sys_ai_usage 为 spring-support-ai-starter 的表, 此处以 -- information_schema + PREPARE/EXECUTE 动态补列/建表, 仅对 MySQL 生效; -- H2 测试库不兼容 PREPARE 语法时由 continueOnError 跳过(新库建表已由 -- 各模块全量 DDL 包含全部列)。 -- =====================================================================  -- 1. 模型管线列 (required/optional pipelines) SET @dbname = DATABASE();
-- SET @tablename = 'sys_ai_model';
-- SET @columnname = 'sys_ai_model_required_pipelines';
-- SET @preparedStatement = (SELECT IF( ( SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname ) > 0, 'SELECT 1', CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` VARCHAR(512) DEFAULT NULL COMMENT ''必填管线(JSON)'' AFTER `sys_ai_model_capabilities`') ));
-- PREPARE alterIfNotExists FROM @preparedStatement;
-- EXECUTE alterIfNotExists;
-- DEALLOCATE PREPARE alterIfNotExists;

-- [通用化] 原动态块无法自动改写，保留为注释（MySQL 老库补列需 DBA 手工执行）：
-- SET @columnname = 'sys_ai_model_optional_pipelines';
-- SET @preparedStatement = (SELECT IF( ( SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname ) > 0, 'SELECT 1', CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` VARCHAR(512) DEFAULT NULL COMMENT ''非必填管线(JSON)'' AFTER `sys_ai_model_required_pipelines`') ));
-- PREPARE alterIfNotExists FROM @preparedStatement;
-- EXECUTE alterIfNotExists;
-- DEALLOCATE PREPARE alterIfNotExists;



-- 2. 用量同步日志表
CREATE TABLE IF NOT EXISTS `sys_ai_usage_sync_log` (
 `sys_ai_usage_sync_log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
 `sys_ai_usage_sync_log_level` VARCHAR(16) NULL DEFAULT 'INFO' COMMENT '日志级别 INFO/WARN/ERROR',
 `sys_ai_usage_sync_log_source` VARCHAR(64) NULL DEFAULT 'schedule' COMMENT '来源（schedule/manual/parser）',
 `sys_ai_usage_sync_log_message` TEXT NULL COMMENT '日志内容',
 `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 PRIMARY KEY (`sys_ai_usage_sync_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量同步日志';

CREATE INDEX IF NOT EXISTS idx_usage_sync_log_time ON sys_ai_usage_sync_log (`create_time`);
CREATE INDEX IF NOT EXISTS idx_sys_ai_usage_call_time ON sys_ai_usage (sys_ai_usage_call_time);








;





-- [通用化] 原动态段残留语句，已注释化（由上方通用 ALTER/CREATE INDEX 幂等语句取代）：
-- PREPARE fs_stmt FROM @idx1;;



;





-- [通用化] 原动态段残留语句，已注释化（由上方通用 ALTER/CREATE INDEX 幂等语句取代）：
-- EXECUTE fs_stmt;;



;





-- [通用化] 原动态段残留语句，已注释化（由上方通用 ALTER/CREATE INDEX 幂等语句取代）：
-- DEALLOCATE PREPARE fs_stmt;;



;











CREATE TABLE IF NOT EXISTS filesystem_upload_upload_log (
 filesystem_upload_upload_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 filesystem_upload_upload_log_upload_id VARCHAR(64) NOT NULL,
 filesystem_upload_upload_log_stage INT DEFAULT 2,
 filesystem_upload_upload_log_status INT NOT NULL,
 filesystem_upload_upload_log_message VARCHAR(500),
 filesystem_upload_upload_log_retry_count INT DEFAULT 0,
 filesystem_upload_upload_log_start_time TIMESTAMP,
 filesystem_upload_upload_log_end_time TIMESTAMP,
 create_time TIMESTAMP,
 update_time TIMESTAMP,
 create_by BIGINT,
 create_name VARCHAR(50),
 update_by BIGINT,
 update_name VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_filesystem_upload_upload_log_upload_id ON filesystem_upload_upload_log(filesystem_upload_upload_log_upload_id);








;





-- [通用化] 原动态段残留语句，已注释化（由上方通用 ALTER/CREATE INDEX 幂等语句取代）：
-- PREPARE fs_stage_stmt FROM @fs_stage_col_exists;;



;





-- [通用化] 原动态段残留语句，已注释化（由上方通用 ALTER/CREATE INDEX 幂等语句取代）：
-- EXECUTE fs_stage_stmt;;



;





-- [通用化] 原动态段残留语句，已注释化（由上方通用 ALTER/CREATE INDEX 幂等语句取代）：
-- DEALLOCATE PREPARE fs_stage_stmt;;



;
ALTER TABLE filesystem_upload_task ADD COLUMN IF NOT EXISTS filesystem_upload_task_stage INT DEFAULT 0;










