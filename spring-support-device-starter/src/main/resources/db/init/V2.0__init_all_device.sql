-- =====================================================================
-- V2.0__init_all_device.sql - spring-support-device-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。此前分散的
--   V1.0__init_all_device.sql / V1.1__add_cloud_yao_fields.sql /
--   V1.1__add_device_asset_p0.sql / V1.2__device_group.sql /
--   V1.2__device_task_state_index.sql
--   已在本次收敛中并入本文件并删除。
--
-- 合并方式:
--   1. 原来靠 ALTER 补的列，一律折进建表语句 —— 新库一次建全，
--      不再依赖「先建表再补列」的两段式；
--   2. 存量库补列的 ALTER 仍原样保留在文件末尾（information_schema 判存 + 动态 DDL），
--      否则老库缺列；新建库上这些语句会因列已存在而跳过。
--
-- 约束: MySQL 专属语法（COMMENT 内联注释 / ENGINE=InnoDB / SET @var + PREPARE 动态 DDL）。
--   MySQL 8.4 不支持 ADD COLUMN IF NOT EXISTS、MariaDB 11.8 同样拒绝，
--   故存量补列一律沿用 information_schema 判存的动态 DDL 写法。
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
    device_organization_updated_at DATETIME COMMENT '更新时间'
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
    device_info_created_at DATETIME COMMENT '创建时间',
    `device_info_latitude` DECIMAL(10, 6),
    `device_info_longitude` DECIMAL(10, 6),
    `device_info_address` VARCHAR(255),
    `device_info_serial` VARCHAR(64),
    `device_info_firmware` VARCHAR(32),
    `device_info_channel_count` INT DEFAULT 0,
    `device_info_offline_threshold` INT DEFAULT 60,
    device_info_updated_at DATETIME,
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
    KEY `idx_device_info_org` (`device_info_org_id`),
    KEY `idx_device_info_location` (`device_info_latitude`, `device_info_longitude`)
) COMMENT '设备基本信息';

-- 同步幂等键：同一厂商同一外部设备只允许一行；手工录入 external_id 为 NULL 不受影响
CREATE UNIQUE INDEX uk_device_vendor_external
    ON device_info (device_info_vendor, device_info_external_id);

CREATE INDEX idx_device_online_last
    ON device_info (device_info_online, device_info_last_online_at);

CREATE INDEX idx_device_owner
    ON device_info (device_info_owner_id);

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
    `device_channel_sort` INT DEFAULT 0
) COMMENT '设备通道';

-- ---------------------------------------------------------------------
-- 设备预置位表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_preset (
    device_preset_id VARCHAR(32) PRIMARY KEY COMMENT '预置位主键 ID',
    device_preset_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_preset_no INT NOT NULL COMMENT '预置位编号',
    device_preset_name VARCHAR(100) COMMENT '预置位名称'
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
    device_event_record_occurred_at DATETIME COMMENT '发生时间'
) COMMENT '设备事件记录';

-- ---------------------------------------------------------------------
-- 设备门禁表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_door (
    device_door_id VARCHAR(32) PRIMARY KEY COMMENT '门禁主键 ID',
    device_door_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_door_no INT NOT NULL COMMENT '门编号',
    device_door_name VARCHAR(100) COMMENT '门名称',
    device_door_status VARCHAR(20) COMMENT '门状态：locked 已锁、unlocked 已开'
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
-- 每套用自己的 device_credential_name 区分；每厂商可标记一套为默认
-- （device_credential_is_default），厂商级离线阈值取默认套的阈值。
--
-- 离线阈值一并入库：原先只有 DeviceProviderConfigServiceImpl 里的静态 Map，
-- 重启即丢；此表落库后由该服务读取。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_provider_credential (

    device_credential_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_credential_vendor VARCHAR(20) NOT NULL COMMENT '厂商标识：hikvision / dahua',
    device_credential_platform_type VARCHAR(40) COMMENT '平台类型：cloud-yao / cloud-eye / secure-platform / dahua-cloud / lechange；SDK 直连可空',
    device_credential_name VARCHAR(100) NOT NULL COMMENT '凭据名称，如「华东区账号」；同厂商同平台下唯一',
    device_credential_app_key VARCHAR(128) COMMENT 'AppKey（AK）；SDK 直连模式可空',
    -- SK 加密后长度变长
    device_credential_app_secret VARCHAR(512) COMMENT 'AppSecret（SK），加密存储',
    device_credential_base_url VARCHAR(255) COMMENT '平台 endpoint 覆盖（留空用各 Session 内置默认）',
    device_credential_offline_threshold INT DEFAULT 60 COMMENT '该套凭据覆盖设备的离线检测阈值（秒）',
    device_credential_is_default TINYINT DEFAULT 0 COMMENT '是否为该厂商的默认套：0 否、1 是',
    device_credential_enabled TINYINT DEFAULT 1 COMMENT '是否启用：0 否、1 是',
    device_credential_remark VARCHAR(255) COMMENT '备注',
    device_credential_created_at DATETIME COMMENT '创建时间',
    device_credential_updated_at DATETIME COMMENT '更新时间',
    -- 原 V1.1__add_cloud_yao_fields.sql 的云曜字段，已折进建表
    device_credential_product_code VARCHAR(64) COMMENT '云曜产品编码 ProductCode（云曜平台必填）',
    device_credential_project_id VARCHAR(64) COMMENT '云曜项目 ID ProjectID（云曜平台必填）',
    KEY `idx_dpc_vendor_platform` (`device_credential_vendor`, `device_credential_platform_type`)
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
    device_task_state_description VARCHAR(255) COMMENT '描述'
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
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组表';

CREATE INDEX idx_group_parent ON device_group(group_parent);
CREATE INDEX idx_group_order  ON device_group(group_order);

-- 设备-分组归属表（一台设备可属于多个分组）
CREATE TABLE IF NOT EXISTS device_group_device (
    id           VARCHAR(36) PRIMARY KEY COMMENT '主键',
    group_id     VARCHAR(36) NOT NULL COMMENT '分组ID',
    device_id    VARCHAR(36) NOT NULL COMMENT '设备ID',
    device_order INT DEFAULT 0 COMMENT '分组内排序',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gd (group_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备-分组归属表';

CREATE INDEX idx_gd_group  ON device_group_device(group_id);
CREATE INDEX idx_gd_device ON device_group_device(device_id);

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

-- ---------------------------------------------------------------------
-- 设备任务状态：按任务名查询/更新的唯一索引
--
-- 背景：TimeScheduler 每个 tick 调用 shouldRun()，按 device_task_state_name
-- 查询一行；此前该列没有索引，属于全表扫描。唯一索引同时保证同一任务只有一条状态行。
-- 清理历史重复任务名（若存在），保证唯一索引可创建
-- （原 V1.2__device_task_state_index.sql，已并入本文件）
-- ---------------------------------------------------------------------
DELETE t1 FROM device_task_state t1
INNER JOIN device_task_state t2
WHERE t1.device_task_state_name = t2.device_task_state_name
  AND t1.device_task_state_id > t2.device_task_state_id;

CREATE UNIQUE INDEX uk_device_task_state_name
    ON device_task_state (device_task_state_name);


-- =====================================================================
-- 存量库补列（老库已有旧表形时补列；新库上列已存在，下面的语句会跳过）
--
-- 为何不用 ADD COLUMN IF NOT EXISTS：MySQL 8.4 不支持该语法、MariaDB 11.8 同样拒绝，
-- 写上去会直接语法错。改用 information_schema 判存 + 动态 DDL，两库都成立。
-- =====================================================================

-- ---------------------------------------------------------------------
-- device_info：资产台账字段
-- ---------------------------------------------------------------------
SET @has_asset_code := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_code'
);
SET @sql_asset_code := IF(@has_asset_code > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_code VARCHAR(64) COMMENT ''设备编码（资产编码/厂商序列号，列表可复制）'' AFTER device_info_firmware');
PREPARE stmt_asset_code FROM @sql_asset_code;
EXECUTE stmt_asset_code;
DEALLOCATE PREPARE stmt_asset_code;

SET @has_asset_external := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_external_id'
);
SET @sql_asset_external := IF(@has_asset_external > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_external_id VARCHAR(128) COMMENT ''厂商侧设备唯一标识（同步幂等键）'' AFTER device_info_code');
PREPARE stmt_asset_external FROM @sql_asset_external;
EXECUTE stmt_asset_external;
DEALLOCATE PREPARE stmt_asset_external;

SET @has_asset_credential := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_credential_id'
);
SET @sql_asset_credential := IF(@has_asset_credential > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_credential_id VARCHAR(32) COMMENT ''绑定的提供商凭据 ID'' AFTER device_info_external_id');
PREPARE stmt_asset_credential FROM @sql_asset_credential;
EXECUTE stmt_asset_credential;
DEALLOCATE PREPARE stmt_asset_credential;

SET @has_asset_owner_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_owner_id'
);
SET @sql_asset_owner_id := IF(@has_asset_owner_id > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_owner_id VARCHAR(32) COMMENT ''负责人用户 ID'' AFTER device_info_credential_id');
PREPARE stmt_asset_owner_id FROM @sql_asset_owner_id;
EXECUTE stmt_asset_owner_id;
DEALLOCATE PREPARE stmt_asset_owner_id;

SET @has_asset_owner_name := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_owner_name'
);
SET @sql_asset_owner_name := IF(@has_asset_owner_name > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_owner_name VARCHAR(64) COMMENT ''负责人姓名（冗余，列表直出）'' AFTER device_info_owner_id');
PREPARE stmt_asset_owner_name FROM @sql_asset_owner_name;
EXECUTE stmt_asset_owner_name;
DEALLOCATE PREPARE stmt_asset_owner_name;

SET @has_asset_owner_dept := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_owner_dept'
);
SET @sql_asset_owner_dept := IF(@has_asset_owner_dept > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_owner_dept VARCHAR(128) COMMENT ''负责人部门'' AFTER device_info_owner_name');
PREPARE stmt_asset_owner_dept FROM @sql_asset_owner_dept;
EXECUTE stmt_asset_owner_dept;
DEALLOCATE PREPARE stmt_asset_owner_dept;

SET @has_asset_owner_phone := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_owner_phone'
);
SET @sql_asset_owner_phone := IF(@has_asset_owner_phone > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_owner_phone VARCHAR(32) COMMENT ''负责人电话'' AFTER device_info_owner_dept');
PREPARE stmt_asset_owner_phone FROM @sql_asset_owner_phone;
EXECUTE stmt_asset_owner_phone;
DEALLOCATE PREPARE stmt_asset_owner_phone;

SET @has_asset_source := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_source'
);
SET @sql_asset_source := IF(@has_asset_source > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_source VARCHAR(16) DEFAULT ''local'' COMMENT ''来源：local 手工、vendor 厂商同步'' AFTER device_info_owner_phone');
PREPARE stmt_asset_source FROM @sql_asset_source;
EXECUTE stmt_asset_source;
DEALLOCATE PREPARE stmt_asset_source;

SET @has_asset_stream_main := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_stream_main_url'
);
SET @sql_asset_stream_main := IF(@has_asset_stream_main > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_stream_main_url VARCHAR(1024) COMMENT ''主码流地址'' AFTER device_info_source');
PREPARE stmt_asset_stream_main FROM @sql_asset_stream_main;
EXECUTE stmt_asset_stream_main;
DEALLOCATE PREPARE stmt_asset_stream_main;

SET @has_asset_stream_sub := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_stream_sub_url'
);
SET @sql_asset_stream_sub := IF(@has_asset_stream_sub > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_stream_sub_url VARCHAR(1024) COMMENT ''子码流地址'' AFTER device_info_stream_main_url');
PREPARE stmt_asset_stream_sub FROM @sql_asset_stream_sub;
EXECUTE stmt_asset_stream_sub;
DEALLOCATE PREPARE stmt_asset_stream_sub;

SET @has_asset_last_online := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_last_online_at'
);
SET @sql_asset_last_online := IF(@has_asset_last_online > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_last_online_at DATETIME COMMENT ''最后在线时间'' AFTER device_info_stream_sub_url');
PREPARE stmt_asset_last_online FROM @sql_asset_last_online;
EXECUTE stmt_asset_last_online;
DEALLOCATE PREPARE stmt_asset_last_online;

SET @has_asset_last_offline := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_last_offline_at'
);
SET @sql_asset_last_offline := IF(@has_asset_last_offline > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_last_offline_at DATETIME COMMENT ''最后离线时间'' AFTER device_info_last_online_at');
PREPARE stmt_asset_last_offline FROM @sql_asset_last_offline;
EXECUTE stmt_asset_last_offline;
DEALLOCATE PREPARE stmt_asset_last_offline;

SET @has_asset_install_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_install_at'
);
SET @sql_asset_install_at := IF(@has_asset_install_at > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_install_at DATE COMMENT ''安装日期'' AFTER device_info_last_offline_at');
PREPARE stmt_asset_install_at FROM @sql_asset_install_at;
EXECUTE stmt_asset_install_at;
DEALLOCATE PREPARE stmt_asset_install_at;

SET @has_asset_warranty := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_warranty_end'
);
SET @sql_asset_warranty := IF(@has_asset_warranty > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_warranty_end DATE COMMENT ''维保到期'' AFTER device_info_install_at');
PREPARE stmt_asset_warranty FROM @sql_asset_warranty;
EXECUTE stmt_asset_warranty;
DEALLOCATE PREPARE stmt_asset_warranty;

SET @has_asset_area := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_area'
);
SET @sql_asset_area := IF(@has_asset_area > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_area VARCHAR(128) COMMENT ''区域/楼层'' AFTER device_info_warranty_end');
PREPARE stmt_asset_area FROM @sql_asset_area;
EXECUTE stmt_asset_area;
DEALLOCATE PREPARE stmt_asset_area;

SET @has_asset_tags := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_tags'
);
SET @sql_asset_tags := IF(@has_asset_tags > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_tags VARCHAR(255) COMMENT ''标签，逗号分隔'' AFTER device_info_area');
PREPARE stmt_asset_tags FROM @sql_asset_tags;
EXECUTE stmt_asset_tags;
DEALLOCATE PREPARE stmt_asset_tags;

SET @has_asset_status := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_status'
);
SET @sql_asset_status := IF(@has_asset_status > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_status VARCHAR(16) DEFAULT ''normal'' COMMENT ''状态：normal/disabled/maintenance/scrapped'' AFTER device_info_tags');
PREPARE stmt_asset_status FROM @sql_asset_status;
EXECUTE stmt_asset_status;
DEALLOCATE PREPARE stmt_asset_status;

SET @has_asset_remark := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_remark'
);
SET @sql_asset_remark := IF(@has_asset_remark > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_remark VARCHAR(500) COMMENT ''备注'' AFTER device_info_status');
PREPARE stmt_asset_remark FROM @sql_asset_remark;
EXECUTE stmt_asset_remark;
DEALLOCATE PREPARE stmt_asset_remark;

SET @has_asset_version := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_version'
);
SET @sql_asset_version := IF(@has_asset_version > 0, 'SELECT 1',
    'ALTER TABLE device_info ADD COLUMN device_info_version INT DEFAULT 0 COMMENT ''乐观锁/增量序号'' AFTER device_info_remark');
PREPARE stmt_asset_version FROM @sql_asset_version;
EXECUTE stmt_asset_version;
DEALLOCATE PREPARE stmt_asset_version;

-- 密码改为加密存储后长度变长（AES Hex），原 100 不够
SET @has_asset_pwd := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_info' AND COLUMN_NAME = 'device_info_password'
      AND CHARACTER_MAXIMUM_LENGTH < 255
);
SET @sql_asset_pwd := IF(@has_asset_pwd > 0,
    'ALTER TABLE device_info MODIFY COLUMN device_info_password VARCHAR(255) COMMENT ''密码（加密存储）''',
    'SELECT 1');
PREPARE stmt_asset_pwd FROM @sql_asset_pwd;
EXECUTE stmt_asset_pwd;
DEALLOCATE PREPARE stmt_asset_pwd;

-- ---------------------------------------------------------------------
-- device_provider_credential：SK 加密后长度变长 + 云曜字段
-- ---------------------------------------------------------------------
SET @has_cred_secret := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_app_secret'
      AND CHARACTER_MAXIMUM_LENGTH < 512
);
SET @sql_cred_secret := IF(@has_cred_secret > 0,
    'ALTER TABLE device_provider_credential MODIFY COLUMN device_credential_app_secret VARCHAR(512) COMMENT ''AppSecret（SK），加密存储''',
    'SELECT 1');
PREPARE stmt_cred_secret FROM @sql_cred_secret;
EXECUTE stmt_cred_secret;
DEALLOCATE PREPARE stmt_cred_secret;

SET @has_cred_product_code := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_product_code'
);
SET @sql_cred_product_code := IF(@has_cred_product_code > 0, 'SELECT 1',
    'ALTER TABLE device_provider_credential ADD COLUMN device_credential_product_code VARCHAR(64) COMMENT ''云曜产品编码 ProductCode（云曜平台必填）'' AFTER device_credential_app_secret');
PREPARE stmt_cred_product_code FROM @sql_cred_product_code;
EXECUTE stmt_cred_product_code;
DEALLOCATE PREPARE stmt_cred_product_code;

SET @has_cred_project_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential' AND COLUMN_NAME = 'device_credential_project_id'
);
SET @sql_cred_project_id := IF(@has_cred_project_id > 0, 'SELECT 1',
    'ALTER TABLE device_provider_credential ADD COLUMN device_credential_project_id VARCHAR(64) COMMENT ''云曜项目 ID ProjectID（云曜平台必填）'' AFTER device_credential_product_code');
PREPARE stmt_cred_project_id FROM @sql_cred_project_id;
EXECUTE stmt_cred_project_id;
DEALLOCATE PREPARE stmt_cred_project_id;

-- ---------------------------------------------------------------------
-- device_organization：外部 ID、排序、删除标记
-- ---------------------------------------------------------------------
SET @has_org_external := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'device_organization_external_id'
);
SET @sql_org_external := IF(@has_org_external > 0, 'SELECT 1',
    'ALTER TABLE device_organization ADD COLUMN device_organization_external_id VARCHAR(128) COMMENT ''厂商侧组织唯一标识'' AFTER device_organization_vendor');
PREPARE stmt_org_external FROM @sql_org_external;
EXECUTE stmt_org_external;
DEALLOCATE PREPARE stmt_org_external;

SET @has_org_sort := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'device_organization_sort'
);
SET @sql_org_sort := IF(@has_org_sort > 0, 'SELECT 1',
    'ALTER TABLE device_organization ADD COLUMN device_organization_sort INT DEFAULT 0 COMMENT ''排序号'' AFTER device_organization_external_id');
PREPARE stmt_org_sort FROM @sql_org_sort;
EXECUTE stmt_org_sort;
DEALLOCATE PREPARE stmt_org_sort;

SET @has_org_deleted := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'device_organization_deleted'
);
SET @sql_org_deleted := IF(@has_org_deleted > 0, 'SELECT 1',
    'ALTER TABLE device_organization ADD COLUMN device_organization_deleted TINYINT DEFAULT 0 COMMENT ''厂商已删除：0 正常、1 删除'' AFTER device_organization_sort');
PREPARE stmt_org_deleted FROM @sql_org_deleted;
EXECUTE stmt_org_deleted;
DEALLOCATE PREPARE stmt_org_deleted;

SET @has_org_updated := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_organization' AND COLUMN_NAME = 'device_organization_updated_at'
);
SET @sql_org_updated := IF(@has_org_updated > 0, 'SELECT 1',
    'ALTER TABLE device_organization ADD COLUMN device_organization_updated_at DATETIME COMMENT ''更新时间'' AFTER device_organization_deleted');
PREPARE stmt_org_updated FROM @sql_org_updated;
EXECUTE stmt_org_updated;
DEALLOCATE PREPARE stmt_org_updated;
