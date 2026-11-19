-- =====================================================================
-- V2.6__init_all_device.sql - spring-support-device-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 版本递进原因: FlywayLike 按「脚本名 + 版本」记账，命中成功记录即跳过，
--   故本次「清除动态 DDL + 审计列内联」必须递进版本号（V2.4/V2.5 -> V2.6），
--   否则存量库不会重跑、内联后的最终表结构不会创建。
-- 迁移口径: 本文件是该模块唯一的建表入口，收敛并取代
--   V2.4__init_all_device.sql（建表基线）与 V2.5__device_audit_align.sql
--   （审计列对齐）。原 V2.4 尾部的动态补列段、V2.5 的动态审计对齐段全部删除，
--   涉及的补列/改列名/建索引已逐表内联进各自建表语句：
--   1) 列名规范化（字段以表名为前缀的既定规则）：
--      - device_provider_credential：device_credential_* -> device_provider_credential_*
--      - device_metric_latest：device_metric_* / device_info_id -> device_metric_latest_*
--   2) 审计列统一对齐 SysBase：21 张业务表统一带 6 审计列
--      （create_time / update_time / create_by / create_name / update_by / update_name），
--      原 created_at / updated_at 列并入其中；create_time DEFAULT CURRENT_TIMESTAMP、
--      update_time ON UPDATE CURRENT_TIMESTAMP 数据库默认值兜底。
--   3) 独立索引语句全部折进建表语句的 KEY / UNIQUE KEY 子句。
-- 清理口径: 原 V2.4 尾部的 COLLATION 统一语句与 device_task_state 去重清理
--   属于一次性库级/数据修复，不再保留；新库直接按 utf8mb4_unicode_ci 建表。
-- 幂等: 全文件只含 CREATE TABLE IF NOT EXISTS，可反复执行。
-- 约束: MySQL 专属语法（COMMENT 内联注释 / ENGINE=InnoDB）。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 设备组织表（树形结构，按厂商隔离）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_organization (

    device_organization_id VARCHAR(32) PRIMARY KEY COMMENT '组织主键 ID',
    device_organization_parent_id VARCHAR(32) COMMENT '父级组织 ID',
    device_organization_name VARCHAR(100) NOT NULL COMMENT '组织名称',
    device_organization_path VARCHAR(500) COMMENT '路径，例 1/2/3',
    device_organization_vendor VARCHAR(20) COMMENT '所属厂商，例如 hikvision、dahua',
    -- 原 V1.1__add_device_asset_p0.sql 的补列，已折进建表
    device_organization_external_id VARCHAR(128) COMMENT '厂商侧组织唯一标识',
    device_organization_sort INT DEFAULT 0 COMMENT '排序号',
    device_organization_deleted TINYINT DEFAULT 0 COMMENT '厂商已删除：0 正常、1 删除',
    -- SysBase 审计列（原 device_organization_updated_at 并入 update_time）
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名'
) COMMENT '设备组织树';

-- ---------------------------------------------------------------------
-- 设备信息表
-- ---------------------------------------------------------------------
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
    -- 密码改为加密存储后长度变长（AES Hex），原 100 不够
    device_info_password VARCHAR(255) COMMENT '密码（加密存储）',
    device_info_online TINYINT DEFAULT 0 COMMENT '是否在线：0 离线、1 在线',
    `device_info_latitude` DECIMAL(10, 6),
    `device_info_longitude` DECIMAL(10, 6),
    `device_info_address` VARCHAR(255),
    `device_info_serial` VARCHAR(64),
    `device_info_firmware` VARCHAR(32),
    `device_info_channel_count` INT DEFAULT 0,
    `device_info_offline_threshold` INT DEFAULT 60,
    -- 原 V1.1__add_device_asset_p0.sql 的资产字段补列，已折进建表
    device_info_code VARCHAR(64) COMMENT '设备编码（资产编码/厂商序列号，列表可复制）',
    device_info_external_id VARCHAR(128) COMMENT '厂商侧设备唯一标识（同步幂等键）',
    device_info_credential_id VARCHAR(32) COMMENT '绑定的提供商凭据 ID',
    device_info_owner_id VARCHAR(32) COMMENT '负责人用户 ID',
    device_info_owner_name VARCHAR(64) COMMENT '负责人姓名（冗余，列表直出）',
    device_info_owner_dept VARCHAR(128) COMMENT '负责人部门',
    device_info_owner_phone VARCHAR(32) COMMENT '负责人电话',
    device_info_source VARCHAR(16) DEFAULT 'local' COMMENT '来源：local 手工、vendor 厂商同步',
    device_info_stream_main_url VARCHAR(1024) COMMENT '主码流地址',
    device_info_stream_sub_url VARCHAR(1024) COMMENT '子码流地址',
    device_info_last_online_at DATETIME COMMENT '最后在线时间',
    device_info_last_offline_at DATETIME COMMENT '最后离线时间',
    device_info_install_at DATE COMMENT '安装日期',
    device_info_warranty_end DATE COMMENT '维保到期',
    device_info_area VARCHAR(128) COMMENT '区域/楼层',
    device_info_tags VARCHAR(255) COMMENT '标签，逗号分隔',
    device_info_status VARCHAR(16) DEFAULT 'normal' COMMENT '状态：normal/disabled/maintenance/scrapped',
    device_info_remark VARCHAR(500) COMMENT '备注',
    device_info_version INT DEFAULT 0 COMMENT '乐观锁/增量序号',
    -- SysBase 审计列（原 device_info_created_at / device_info_updated_at 并入）
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    KEY `idx_device_info_org` (`device_info_org_id`),
    KEY `idx_device_info_location` (`device_info_latitude`, `device_info_longitude`),
    -- 同步幂等键：同一厂商同一外部设备只允许一行；手工录入 external_id 为 NULL 不受影响
    UNIQUE KEY uk_device_vendor_external (`device_info_vendor`, `device_info_external_id`),
    KEY idx_device_online_last (`device_info_online`, `device_info_last_online_at`),
    KEY idx_device_owner (`device_info_owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '设备基本信息';

-- ---------------------------------------------------------------------
-- 设备通道表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_channel (
    device_channel_id VARCHAR(32) PRIMARY KEY COMMENT '通道主键 ID',
    device_channel_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_channel_no INT NOT NULL COMMENT '通道号',
    device_channel_name VARCHAR(100) COMMENT '通道名称',
    device_channel_type VARCHAR(20) COMMENT '通道类型：video 视频、door 门禁',
    `device_channel_status` VARCHAR(20) DEFAULT 'enabled',
    `device_channel_sort` INT DEFAULT 0,
    -- SysBase 审计列
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名'
) COMMENT '设备通道';

-- ---------------------------------------------------------------------
-- 设备预置位表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_preset (
    device_preset_id VARCHAR(32) PRIMARY KEY COMMENT '预置位主键 ID',
    device_preset_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_preset_no INT NOT NULL COMMENT '预置位编号',
    device_preset_name VARCHAR(100) COMMENT '预置位名称',
    -- SysBase 审计列
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名'
) COMMENT '设备云台预置位';

-- ---------------------------------------------------------------------
-- 设备事件记录表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_event_record (
    device_event_record_id VARCHAR(32) PRIMARY KEY COMMENT '事件记录主键 ID',
    device_event_record_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_event_record_channel_no INT COMMENT '通道号',
    device_event_record_event_type VARCHAR(30) COMMENT '事件类型：MOTION、IO_ALARM、ACCESS、TEMP、NETWORK、OTHER',
    device_event_record_detail TEXT COMMENT '事件详情',
    device_event_record_occurred_at DATETIME COMMENT '发生时间',
    -- SysBase 审计列
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名'
) COMMENT '设备事件记录';

-- ---------------------------------------------------------------------
-- 设备门禁表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_door (
    device_door_id VARCHAR(32) PRIMARY KEY COMMENT '门禁主键 ID',
    device_door_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_door_no INT NOT NULL COMMENT '门编号',
    device_door_name VARCHAR(100) COMMENT '门名称',
    device_door_status VARCHAR(20) COMMENT '门状态：locked 已锁、unlocked 已开',
    -- SysBase 审计列
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名'
) COMMENT '设备门禁';

-- ---------------------------------------------------------------------
-- 门禁出入记录落库表
--
-- 定时抽取（deviceAccessExtract）把各在线会话 queryAccessRecords 的结果
-- 按 (deviceId, occurredAt) 落库，供门禁记录页查询；不再依赖设备在线实时直查。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_access_record (
    device_access_record_id        VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_access_record_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_access_record_door_id   VARCHAR(64) COMMENT '门标识（厂商侧）',
    device_access_record_card_no   VARCHAR(64) COMMENT '卡号',
    device_access_record_user_name VARCHAR(100) COMMENT '人员姓名',
    device_access_record_event_type VARCHAR(30) COMMENT '事件类型：刷卡开门 / 密码开门 / 异常 等',
    device_access_record_direction VARCHAR(16) COMMENT '进出方向：in 进、out 出；厂商未回传为空',
    device_access_record_photo_url VARCHAR(512) COMMENT '抓拍照片地址',
    device_access_record_occurred_at DATETIME COMMENT '刷卡时间',
    -- SysBase 审计列
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    KEY idx_acc_device_time (`device_access_record_device_id`, `device_access_record_occurred_at`)
) COMMENT '门禁出入记录（定时抽取落库）';

-- ---------------------------------------------------------------------
-- 提供商凭据表（厂商自定义的 AK/SK 等接入参数）
--
-- 为什么需要：DeviceConnectInfo 携带 appKey/appSecret/platformType，但 device_info
-- 无对应列，API 边界接收后即丢弃——云平台凭据无处落库，后续会话/事件/云台/视频
-- 都无法重新认证。此表补上该缺口。
--
-- 粒度：同一 (厂商, 平台类型) 可登记**多套**凭据（如多个海康账号分属不同区域），
-- 每套用自己的 device_provider_credential_name 区分；每厂商可标记一套为默认
-- （device_provider_credential_is_default），厂商级离线阈值取默认套的阈值。
--
-- 离线阈值一并入库：原先只有 DeviceProviderConfigServiceImpl 里的静态 Map，
-- 重启即丢；此表落库后由该服务读取。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_provider_credential (

    device_provider_credential_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_provider_credential_vendor VARCHAR(20) NOT NULL COMMENT '厂商标识：hikvision / dahua',
    device_provider_credential_platform_type VARCHAR(40) COMMENT '平台类型：cloud-yao / cloud-eye / secure-platform / dahua-cloud / lechange；SDK 直连可空',
    device_provider_credential_name VARCHAR(100) NOT NULL COMMENT '凭据名称，如「华东区账号」；同厂商同平台下唯一',
    device_provider_credential_app_key VARCHAR(128) COMMENT 'AppKey（AK）；SDK 直连模式可空',
    -- SK 加密后长度变长
    device_provider_credential_app_secret VARCHAR(512) COMMENT 'AppSecret（SK），加密存储',
    device_provider_credential_base_url VARCHAR(255) COMMENT '平台 endpoint 覆盖（留空用各 Session 内置默认）',
    device_provider_credential_offline_threshold INT DEFAULT 60 COMMENT '该套凭据覆盖设备的离线检测阈值（秒）',
    device_provider_credential_is_default TINYINT DEFAULT 0 COMMENT '是否为该厂商的默认套：0 否、1 是',
    device_provider_credential_enabled TINYINT DEFAULT 1 COMMENT '是否启用：0 否、1 是',
    device_provider_credential_remark VARCHAR(255) COMMENT '备注',
    -- 健康留痕：单条「测试连接」与顶部「全部检测」都会写这两列
    device_provider_credential_last_test_at DATETIME COMMENT '最近一次连通性探测时间',
    device_provider_credential_last_test_ok TINYINT COMMENT '最近一次连通性探测结果：0 失败、1 成功、NULL 未检测',
    -- 原 V1.1__add_cloud_yao_fields.sql 的云曜字段，已折进建表
    device_provider_credential_product_code VARCHAR(64) COMMENT '云曜产品编码 ProductCode（云曜平台必填）',
    device_provider_credential_project_id VARCHAR(64) COMMENT '云曜项目 ID ProjectID（云曜平台必填）',
    device_provider_credential_group_no VARCHAR(64) COMMENT '云眸组编号 groupNo（云眸平台注册设备必填）',
    -- 凭据维度的自动检测/自动同步（高级配置，挂载到时间轮，默认全关）
    device_provider_credential_offline_detect_enabled TINYINT DEFAULT 0 COMMENT '该凭据维度的在离线检测开关',
    device_provider_credential_offline_detect_interval INT COMMENT '该凭据维度的在离线检测间隔（秒）',
    device_provider_credential_event_poll_enabled TINYINT DEFAULT 0 COMMENT '该凭据实例的事件轮询开关',
    device_provider_credential_event_poll_interval INT COMMENT '该凭据实例的事件轮询间隔（秒）',
    device_provider_credential_device_sync_enabled TINYINT DEFAULT 0 COMMENT '该凭据维度的设备自动同步开关',
    device_provider_credential_device_sync_interval INT COMMENT '该凭据维度的设备自动同步间隔（秒）',
    device_provider_credential_org_sync_enabled TINYINT DEFAULT 0 COMMENT '该凭据维度的组织自动同步开关',
    device_provider_credential_org_sync_interval INT COMMENT '该凭据维度的组织自动同步间隔（秒）',
    device_provider_credential_metric_collect_enabled TINYINT DEFAULT 0 COMMENT '该凭据维度的环境指标采集开关',
    device_provider_credential_metric_collect_interval INT COMMENT '该凭据维度的环境指标采集间隔（秒）',
    -- SysBase 审计列（原 device_credential_created_at / device_credential_updated_at 并入）
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    KEY `idx_dpc_vendor_platform` (`device_provider_credential_vendor`, `device_provider_credential_platform_type`)
) COMMENT '提供商接入凭据（AK/SK 等）';

-- ---------------------------------------------------------------------
-- 后台定时任务配置表
-- 用于 Trigger 模式：定时检测是否运行
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_task_state (

    device_task_state_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_task_state_name VARCHAR(50) NOT NULL COMMENT '任务名称（唯一）',
    device_task_state_running TINYINT DEFAULT 0 COMMENT '是否正在运行：0 否、1 是',
    device_task_state_last_run_at DATETIME COMMENT '最后运行时间',
    device_task_state_interval INT DEFAULT 30 COMMENT '运行间隔（秒）',
    device_task_state_description VARCHAR(255) COMMENT '描述',
    -- SysBase 审计列
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    -- 同一任务只允许一条状态行（原 V1.2__device_task_state_index.sql，已折进建表）
    UNIQUE KEY uk_device_task_state_name (`device_task_state_name`)
) COMMENT '后台任务运行状态（Trigger 模式）';

-- ---------------------------------------------------------------------
-- 设备分组管理：树形分组 + 设备归属
-- （原 V1.2__device_group.sql，已并入本文件）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_group (
    group_id       VARCHAR(36) PRIMARY KEY COMMENT '主键',
    group_name     VARCHAR(64) NOT NULL COMMENT '分组名称',
    group_parent   VARCHAR(36) COMMENT '上级分组ID；顶级为空',
    group_order    INT DEFAULT 0 COMMENT '排序序号',
    group_remark   VARCHAR(512) COMMENT '备注',
    -- SysBase 审计列（原 created_at / updated_at 并入）
    create_time    DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by      INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name    VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by      INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name    VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    KEY idx_group_parent (group_parent),
    KEY idx_group_order  (group_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组表';

-- 设备-分组归属表（一台设备可属于多个分组）
CREATE TABLE IF NOT EXISTS device_group_device (
    id           VARCHAR(36) PRIMARY KEY COMMENT '主键',
    group_id     VARCHAR(36) NOT NULL COMMENT '分组ID',
    device_id    VARCHAR(36) NOT NULL COMMENT '设备ID',
    device_order INT DEFAULT 0 COMMENT '分组内排序',
    -- SysBase 审计列（原 created_at 并入 create_time）
    create_time  DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by    INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name  VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by    INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name  VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    UNIQUE KEY uk_gd (group_id, device_id),
    KEY idx_gd_group  (group_id),
    KEY idx_gd_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备-分组归属表';

-- ---------------------------------------------------------------------
-- 设备在线状态流水：在线率、最后在线、离线时长
-- （原 V1.1__add_device_asset_p0.sql，已并入本文件）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_online_log (
    device_online_log_id        VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_online_log_device_id VARCHAR(32) NOT NULL COMMENT '设备 ID',
    device_online_log_online    TINYINT     NOT NULL COMMENT '1 在线、0 离线',
    device_online_log_at        DATETIME    NOT NULL COMMENT '发生时间',
    KEY idx_online_log_device (`device_online_log_device_id`, `device_online_log_at`)
) COMMENT '设备在线状态流水';

-- ---------------------------------------------------------------------
-- 同步状态/记录/推送队列（设备目录同步）
-- （原 V1.1__add_device_asset_p0.sql，已并入本文件）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_sync_state (
    device_sync_state_id       VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_sync_state_vendor   VARCHAR(32) NOT NULL COMMENT '厂商标识',
    device_sync_state_resource VARCHAR(32) NOT NULL COMMENT '资源：device/channel/org/access/parking',
    device_sync_state_cursor   VARCHAR(255) COMMENT '增量游标',
    device_sync_state_last_at  DATETIME     COMMENT '最后同步时间',
    device_sync_state_last_ok  TINYINT      DEFAULT 1 COMMENT '最后结果：1 成功、0 失败',
    device_sync_state_message  VARCHAR(500) COMMENT '最后结果说明',
    UNIQUE KEY uk_sync_state (`device_sync_state_vendor`, `device_sync_state_resource`)
) COMMENT '厂商同步游标状态';

CREATE TABLE IF NOT EXISTS device_sync_record (
    device_sync_record_id          VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_sync_record_task_id     VARCHAR(32) NOT NULL COMMENT '任务 ID',
    device_sync_record_vendor      VARCHAR(32) COMMENT '厂商标识',
    device_sync_record_mode        VARCHAR(16) COMMENT 'pull/push/preview',
    device_sync_record_phase       VARCHAR(32) COMMENT '阶段',
    device_sync_record_total       INT DEFAULT 0 COMMENT '总数',
    device_sync_record_added       INT DEFAULT 0 COMMENT '新增数',
    device_sync_record_updated     INT DEFAULT 0 COMMENT '更新数',
    device_sync_record_removed     INT DEFAULT 0 COMMENT '删除数',
    device_sync_record_failed      INT DEFAULT 0 COMMENT '失败数',
    device_sync_record_status      VARCHAR(16) COMMENT 'running/success/partial/failed',
    device_sync_record_message     VARCHAR(1000) COMMENT '说明',
    device_sync_record_started_at  DATETIME COMMENT '开始时间',
    device_sync_record_finished_at DATETIME COMMENT '结束时间',
    KEY idx_sync_record_time (`device_sync_record_started_at`)
) COMMENT '同步执行记录';

CREATE TABLE IF NOT EXISTS device_sync_outbox (
    device_sync_outbox_id        VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_sync_outbox_device_id VARCHAR(32) COMMENT '设备 ID',
    device_sync_outbox_vendor    VARCHAR(32) COMMENT '厂商标识',
    device_sync_outbox_action    VARCHAR(16) COMMENT 'create/update/delete',
    device_sync_outbox_payload   TEXT COMMENT '推送载荷',
    device_sync_outbox_status    VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/sent/failed/dead',
    device_sync_outbox_retry     INT DEFAULT 0 COMMENT '重试次数',
    device_sync_outbox_next_at   DATETIME COMMENT '下次重试时间',
    device_sync_outbox_message   VARCHAR(500) COMMENT '失败原因',
    device_sync_outbox_created_at DATETIME COMMENT '创建时间',
    KEY idx_outbox_due (`device_sync_outbox_status`, `device_sync_outbox_next_at`)
) COMMENT '设备变更推送队列';

-- ---------------------------------------------------------------------
-- 厂商人员表（按厂商侧人员标识幂等入库，行政归属挂接厂商侧组织标识）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_person (
    device_person_id            VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_person_name          VARCHAR(64) NOT NULL COMMENT '人员姓名',
    device_person_title         VARCHAR(64) COMMENT '职称（平台未提供时为空）',
    device_person_phone         VARCHAR(32) COMMENT '手机（平台未提供时为空）',
    device_person_email         VARCHAR(64) COMMENT '邮箱（平台未提供时为空）',
    device_person_vendor        VARCHAR(32) NOT NULL COMMENT '厂商标识：hikvision / dahua',
    device_person_external_id   VARCHAR(128) NOT NULL COMMENT '厂商侧人员唯一标识',
    device_person_org_id        VARCHAR(128) COMMENT '厂商侧组织标识（部门 ID），挂接 device_organization',
    device_person_deleted       TINYINT     DEFAULT 0 COMMENT '已删除标记：1 已删除、0 正常',
    -- SysBase 审计列（原 device_person_created_at / device_person_updated_at 并入）
    create_time                 DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                 DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                   INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                 VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                   INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                 VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    KEY idx_person_vendor (`device_person_vendor`),
    KEY idx_person_external (`device_person_vendor`, `device_person_external_id`),
    KEY idx_person_org (`device_person_vendor`, `device_person_org_id`)
) COMMENT '厂商人员表';

-- ---------------------------------------------------------------------
-- 任务配置（时间轮/可配置检查时间）
-- （原 V1.1__add_device_asset_p0.sql，已并入本文件）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_task_config (
    device_task_config_id           VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_task_config_code         VARCHAR(64) NOT NULL COMMENT '任务编码',
    device_task_config_name         VARCHAR(100) COMMENT '任务名称',
    device_task_config_enabled      TINYINT DEFAULT 1 COMMENT '是否启用',
    device_task_config_cron         VARCHAR(64) COMMENT 'cron 表达式（优先）',
    device_task_config_interval     INT COMMENT '固定间隔（秒）',
    device_task_config_next_at      DATETIME COMMENT '下次执行时间',
    device_task_config_last_at      DATETIME COMMENT '最后执行时间',
    device_task_config_last_status  VARCHAR(16) COMMENT '最后状态',
    device_task_config_last_message VARCHAR(1000) COMMENT '最后结果说明',
    device_task_config_timeout      INT DEFAULT 300 COMMENT '超时（秒）',
    device_task_config_params       TEXT COMMENT '参数 JSON（厂商、组织、批量大小）',
    device_task_config_lock_owner   VARCHAR(64) COMMENT '锁持有者',
    device_task_config_lock_until   DATETIME COMMENT '锁到期时间',
    UNIQUE KEY uk_task_config_code (`device_task_config_code`)
) COMMENT '设备域定时任务配置';

-- ------------------------------------------------------------
-- 环境指标最新快照（气象站/温湿度传感器）
-- 列名已按表名前缀规范化（原 device_metric_* / device_info_id）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_metric_latest (
    device_metric_latest_id           VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_metric_latest_device_id    VARCHAR(32) NOT NULL COMMENT '设备 ID',
    device_metric_latest_key          VARCHAR(128) NOT NULL COMMENT '节点稳定标识（厂商节点 ID 或名称+单位）',
    device_metric_latest_name         VARCHAR(64) COMMENT '节点名称',
    device_metric_latest_value        VARCHAR(128) COMMENT '读数（字符串兼容风向/天气现象）',
    device_metric_latest_unit         VARCHAR(32) COMMENT '单位',
    device_metric_latest_data_time    DATETIME COMMENT '厂商侧数据时间',
    device_metric_latest_collected_at DATETIME COMMENT '本地采集时间',
    -- SysBase 审计列
    create_time                       DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                       DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                         INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                       VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                         INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                       VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    UNIQUE KEY uk_device_metric (device_metric_latest_device_id, device_metric_latest_key),
    KEY idx_device_metric_device (device_metric_latest_device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备环境指标最新快照';

-- ---------------------------------------------------------------------
-- 设备时序点（plugin.device.timeseries.engine=bean 的明细表）
-- 传感器历史与门禁/车闸时序记录统一落这一张表；
-- 切到 influxdb / redis 时本表不再写入，结构保留以便随时切回。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_series_point (
    device_series_point_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_series_category VARCHAR(32) NOT NULL COMMENT '分类：metric 传感器 / access 门禁 / vehicle 车闸',
    device_series_device_id VARCHAR(64) NOT NULL COMMENT '设备 ID',
    device_series_channel VARCHAR(64) DEFAULT NULL COMMENT '通道 / 门点 / 车闸标识',
    device_series_key VARCHAR(128) NOT NULL COMMENT '序列键：节点标识 / 卡号或人员 / 车牌号',
    device_series_name VARCHAR(100) DEFAULT NULL COMMENT '展示名',
    device_series_value VARCHAR(256) DEFAULT NULL COMMENT '记录值：读数 / 事件类型 / 进出方向',
    device_series_unit VARCHAR(32) DEFAULT NULL COMMENT '单位',
    device_series_time DATETIME(3) NOT NULL COMMENT '发生时间（毫秒精度）',
    device_series_extra TEXT COMMENT '附加字段 JSON（门点 / 抓拍 / 车型）',
    device_series_created_at DATETIME DEFAULT NULL COMMENT '入库时间',
    KEY idx_dsp_cat_dev_time (device_series_category, device_series_device_id, device_series_time),
    KEY idx_dsp_cat_key_time (device_series_category, device_series_key, device_series_time),
    KEY idx_dsp_time (device_series_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备时序点（bean 引擎明细）';

-- ---------------------------------------------------------------------
-- webhook 入站凭据 / 幂等回执 / 告警
-- 原先分散在 db/manual/device_webhook_alarm.sql，现并入唯一 DDL 入口。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_webhook_appkey (
    device_webhook_appkey_id          VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_webhook_appkey_key         VARCHAR(64) NOT NULL COMMENT '公开标识 appKey',
    device_webhook_appkey_secret      VARCHAR(128) NOT NULL COMMENT '签名密钥 secret',
    device_webhook_appkey_name        VARCHAR(100) NOT NULL COMMENT '凭据名称，同时作为入站 source',
    device_webhook_appkey_scopes      VARCHAR(200) DEFAULT 'access,alarm' COMMENT '允许主题：access,alarm,vehicle',
    device_webhook_appkey_expires_at  DATETIME NULL COMMENT '过期时间；NULL 表示永久有效',
    device_webhook_appkey_enabled     TINYINT DEFAULT 1 COMMENT '1 启用、0 停用',
    device_webhook_appkey_last_used_at DATETIME NULL COMMENT '最近一次成功调用时间',
    -- SysBase 审计列（原 device_webhook_appkey_created_at 并入 create_time）
    create_time                       DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                       DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                         INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                       VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                         INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                       VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    UNIQUE KEY uk_webhook_appkey_key (device_webhook_appkey_key)
) COMMENT '设备 webhook 入站应用凭据';

CREATE TABLE IF NOT EXISTS device_webhook_receipt (
    device_webhook_receipt_id          VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_webhook_receipt_source      VARCHAR(100) NOT NULL COMMENT '来源（appKey 名称）',
    device_webhook_receipt_external_id VARCHAR(128) NOT NULL COMMENT '外部事件 ID 或包体摘要',
    device_webhook_receipt_topic       VARCHAR(32) NOT NULL COMMENT '主题：access / alarm / vehicle',
    -- SysBase 审计列（原 device_webhook_receipt_created_at 并入 create_time）
    create_time                        DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                        DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                          INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                        VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                          INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                        VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    UNIQUE KEY uk_webhook_receipt (device_webhook_receipt_source, device_webhook_receipt_external_id, device_webhook_receipt_topic),
    KEY idx_webhook_receipt_time (create_time)
) COMMENT 'webhook 入站幂等回执';

CREATE TABLE IF NOT EXISTS device_alarm (
    device_alarm_id            VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_alarm_source        VARCHAR(100) COMMENT '来源（appKey 名称 / 厂商标识）',
    device_alarm_external_id   VARCHAR(128) COMMENT '外部事件 ID / 包体摘要',
    device_alarm_device_id     VARCHAR(32) COMMENT '所属设备 ID',
    device_alarm_channel_no    INT COMMENT '通道号',
    device_alarm_type          VARCHAR(40) COMMENT 'MOTION / IO_ALARM / OFFLINE / ACCESS / OTHER',
    device_alarm_level         VARCHAR(16) COMMENT 'critical / major / minor / info',
    device_alarm_title         VARCHAR(200) COMMENT '告警标题',
    device_alarm_detail        TEXT COMMENT '告警详情',
    device_alarm_status        VARCHAR(16) DEFAULT 'unhandled' COMMENT 'unhandled / handling / resolved / ignored',
    device_alarm_handler       VARCHAR(64) COMMENT '处理人',
    device_alarm_handled_at    DATETIME NULL COMMENT '处理时间',
    device_alarm_occurred_at   DATETIME NULL COMMENT '发生时间',
    -- SysBase 审计列（原 device_alarm_created_at 并入 create_time）
    create_time                DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                  INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                  INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    KEY idx_alarm_time (device_alarm_occurred_at),
    KEY idx_alarm_status (device_alarm_status),
    KEY idx_alarm_level (device_alarm_level),
    KEY idx_alarm_device (device_alarm_device_id)
) COMMENT '设备告警（webhook 入站落库）';

-- ---------------------------------------------------------------------
-- 车辆档案 / 车闸点位 / 过车记录 / 过车拉取游标
-- 原先分散在 db/manual/device_vehicle_barrier.sql，现并入唯一 DDL 入口。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_vehicle (
    device_vehicle_id           VARCHAR(64) NOT NULL COMMENT '主键',
    device_vehicle_plate_no     VARCHAR(32) NOT NULL COMMENT '车牌号',
    device_vehicle_plate_color  VARCHAR(16) DEFAULT NULL COMMENT '车牌颜色',
    device_vehicle_type         VARCHAR(32) DEFAULT NULL COMMENT '车辆类型',
    device_vehicle_owner_name   VARCHAR(64) DEFAULT NULL COMMENT '车主姓名',
    device_vehicle_owner_phone  VARCHAR(32) DEFAULT NULL COMMENT '车主电话',
    device_vehicle_list_type    VARCHAR(16) DEFAULT 'normal' COMMENT '名单类型：white/black/normal',
    device_vehicle_valid_from   DATETIME DEFAULT NULL COMMENT '有效期起',
    device_vehicle_valid_to     DATETIME DEFAULT NULL COMMENT '有效期止',
    device_vehicle_vendor       VARCHAR(32) DEFAULT NULL COMMENT '厂商标识',
    device_vehicle_external_id  VARCHAR(128) DEFAULT NULL COMMENT '厂商侧车辆标识',
    device_vehicle_device_id    VARCHAR(64) DEFAULT NULL COMMENT '绑定设备 ID（车闸所在设备）',
    device_vehicle_belonging    VARCHAR(128) DEFAULT NULL COMMENT '车牌所属（单位/车场）',
    device_vehicle_remark       VARCHAR(512) DEFAULT NULL COMMENT '备注',
    device_vehicle_deleted      TINYINT(1) DEFAULT '0' COMMENT '删除标记',
    -- SysBase 审计列（原 device_vehicle_created_at / device_vehicle_updated_at 并入）
    create_time                 DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                 DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                   INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                 VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                   INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                 VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    PRIMARY KEY (device_vehicle_id),
    KEY idx_vehicle_plate (device_vehicle_plate_no),
    KEY idx_vehicle_vendor_ext (device_vehicle_vendor, device_vehicle_external_id)
) COMMENT '车辆档案';

CREATE TABLE IF NOT EXISTS device_barrier_gate (
    device_barrier_gate_id          VARCHAR(64) NOT NULL COMMENT '主键',
    device_barrier_gate_name        VARCHAR(128) DEFAULT NULL COMMENT '车闸名称',
    device_barrier_gate_device_id   VARCHAR(64) DEFAULT NULL COMMENT '所属设备',
    device_barrier_gate_channel_no  INT DEFAULT NULL COMMENT '通道号',
    device_barrier_gate_direction   VARCHAR(16) DEFAULT 'both' COMMENT '方向：in/out/both',
    device_barrier_gate_status      VARCHAR(16) DEFAULT 'unknown' COMMENT '状态',
    device_barrier_gate_vendor      VARCHAR(32) DEFAULT NULL COMMENT '厂商标识',
    device_barrier_gate_external_id VARCHAR(128) DEFAULT NULL COMMENT '厂商侧车闸标识',
    device_barrier_gate_deleted     TINYINT(1) DEFAULT '0' COMMENT '删除标记',
    -- SysBase 审计列（原 device_barrier_gate_created_at / device_barrier_gate_updated_at 并入）
    create_time                     DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                     DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                       INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                     VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                       INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                     VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    PRIMARY KEY (device_barrier_gate_id),
    KEY idx_barrier_device (device_barrier_gate_device_id),
    KEY idx_barrier_direction (device_barrier_gate_direction)
) COMMENT '车闸点位';

CREATE TABLE IF NOT EXISTS device_vehicle_pass (
    device_vehicle_pass_id           VARCHAR(64) NOT NULL COMMENT '主键',
    device_vehicle_pass_device_id    VARCHAR(64) DEFAULT NULL COMMENT '所属设备 ID',
    device_vehicle_pass_gate_id      VARCHAR(64) DEFAULT NULL COMMENT '车闸标识',
    device_vehicle_pass_plate_no     VARCHAR(32) DEFAULT NULL COMMENT '车牌号',
    device_vehicle_pass_direction    VARCHAR(16) DEFAULT NULL COMMENT '方向：in/out',
    device_vehicle_pass_time         DATETIME DEFAULT NULL COMMENT '过车时间',
    device_vehicle_pass_photo_url    VARCHAR(1024) DEFAULT NULL COMMENT '抓拍图片地址',
    device_vehicle_pass_vehicle_type VARCHAR(32) DEFAULT NULL COMMENT '车辆类型',
    -- SysBase 审计列（原 device_vehicle_pass_created_at 并入 create_time）
    create_time                      DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                      DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                        INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                      VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                        INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                      VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    PRIMARY KEY (device_vehicle_pass_id),
    KEY idx_pass_gate_time (device_vehicle_pass_gate_id, device_vehicle_pass_time),
    KEY idx_pass_device_time (device_vehicle_pass_device_id, device_vehicle_pass_time),
    KEY idx_pass_plate_time (device_vehicle_pass_plate_no, device_vehicle_pass_time)
) COMMENT '车辆过车记录';

CREATE TABLE IF NOT EXISTS device_attendance_record (
    device_attendance_record_id           VARCHAR(64) NOT NULL COMMENT '主键',
    device_attendance_record_device_id    VARCHAR(64) DEFAULT NULL COMMENT '设备 ID',
    device_attendance_record_person_name  VARCHAR(64) DEFAULT NULL COMMENT '人员姓名',
    device_attendance_record_person_no    VARCHAR(64) DEFAULT NULL COMMENT '人员编号（工号）',
    device_attendance_record_card_no      VARCHAR(64) DEFAULT NULL COMMENT '卡号',
    device_attendance_record_direction    VARCHAR(16) DEFAULT NULL COMMENT '方向：in/out',
    device_attendance_record_occurred_at  DATETIME DEFAULT NULL COMMENT '考勤时间',
    device_attendance_record_source       VARCHAR(32) DEFAULT NULL COMMENT '来源：webhook/access-sync',
    device_attendance_record_source_id    VARCHAR(128) DEFAULT NULL COMMENT '来源侧幂等键',
    -- SysBase 审计列（原 device_attendance_record_created_at 并入 create_time）
    create_time                           DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                           DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                             INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                           VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                             INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                           VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    PRIMARY KEY (device_attendance_record_id),
    KEY idx_attendance_time (device_attendance_record_occurred_at),
    KEY idx_attendance_device_time (device_attendance_record_device_id, device_attendance_record_occurred_at),
    KEY idx_attendance_source (device_attendance_record_source_id)
) COMMENT '考勤记录';

CREATE TABLE IF NOT EXISTS device_vehicle_pass_cursor (
    device_vehicle_pass_cursor_device_id   VARCHAR(64) NOT NULL COMMENT '设备标识',
    device_vehicle_pass_cursor_last_msg_id BIGINT NOT NULL DEFAULT '0' COMMENT '最后处理的平台消息 ID',
    -- SysBase 审计列（原 device_vehicle_pass_cursor_updated_at 并入 update_time）
    create_time                            DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                            DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by                              INT NULL DEFAULT NULL COMMENT '创建人 ID',
    create_name                            VARCHAR(64) NULL DEFAULT NULL COMMENT '创建人姓名',
    update_by                              INT NULL DEFAULT NULL COMMENT '更新人 ID',
    update_name                            VARCHAR(64) NULL DEFAULT NULL COMMENT '更新人姓名',
    PRIMARY KEY (device_vehicle_pass_cursor_device_id)
) COMMENT '过车拉取游标';
