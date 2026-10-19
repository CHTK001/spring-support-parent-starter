-- =====================================================================
-- V1.0__init_all_device.sql - spring-support-device-starter full DDL
-- merged from loose scripts:
--   V1.0__init_device.sql
--   V1.1__add_channel_and_location.sql
-- =====================================================================


-- ---------------------------------------------------------------------
-- source: V1.0__init_device.sql
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
    device_info_created_at DATETIME COMMENT '创建时间',
    `device_info_latitude` DECIMAL(10, 6),
    `device_info_longitude` DECIMAL(10, 6),
    `device_info_address` VARCHAR(255),
    `device_info_serial` VARCHAR(64),
    `device_info_firmware` VARCHAR(32),
    `device_info_channel_count` INT DEFAULT 0,
    `device_info_offline_threshold` INT DEFAULT 60,
    `device_info_updated_at` DATETIME,
    KEY `idx_device_info_org` (`device_info_org_id`),
    KEY `idx_device_info_location` (`device_info_latitude`, `device_info_longitude`)
) COMMENT '设备基本信息';

-- 设备通道表
CREATE TABLE IF NOT EXISTS device_channel (
    device_channel_id VARCHAR(32) PRIMARY KEY COMMENT '通道主键 ID',
    device_channel_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_channel_no INT NOT NULL COMMENT '通道号',
    device_channel_name VARCHAR(100) COMMENT '通道名称',
    device_channel_type VARCHAR(20) COMMENT '通道类型：video 视频、door 门禁',
    `device_channel_status` VARCHAR(20) DEFAULT 'enabled',
    `device_channel_sort` INT DEFAULT 0
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

-- 索引

-- ============================================
-- 设备管理模块增量脚本：通道管理、地图位置、定时检测
-- 版本：1.1
-- 作者：CH
-- ============================================

-- 设备表增加地图位置字段
-- 位置索引（在补列之后创建）

-- ============================================
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
-- ============================================
CREATE TABLE IF NOT EXISTS device_provider_credential (

    device_credential_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_credential_vendor VARCHAR(20) NOT NULL COMMENT '厂商标识：hikvision / dahua',
    device_credential_platform_type VARCHAR(40) COMMENT '平台类型：cloud-yao / cloud-eye / secure-platform / dahua-cloud / lechange；SDK 直连可空',
    device_credential_name VARCHAR(100) NOT NULL COMMENT '凭据名称，如「华东区账号」；同厂商同平台下唯一',
    device_credential_app_key VARCHAR(128) COMMENT 'AppKey（AK）；SDK 直连模式可空',
    device_credential_app_secret VARCHAR(256) COMMENT 'AppSecret（SK）；SDK 直连模式可空',
    device_credential_base_url VARCHAR(255) COMMENT '平台 endpoint 覆盖（留空用各 Session 内置默认）',
    device_credential_offline_threshold INT DEFAULT 60 COMMENT '该套凭据覆盖设备的离线检测阈值（秒）',
    device_credential_is_default TINYINT DEFAULT 0 COMMENT '是否为该厂商的默认套：0 否、1 是',
    device_credential_enabled TINYINT DEFAULT 1 COMMENT '是否启用：0 否、1 是',
    device_credential_remark VARCHAR(255) COMMENT '备注',
    device_credential_created_at DATETIME COMMENT '创建时间',
    device_credential_updated_at DATETIME COMMENT '更新时间',
    KEY `idx_dpc_vendor_platform` (`device_credential_vendor`, `device_credential_platform_type`)
) COMMENT '提供商接入凭据（AK/SK 等）';
-- ============================================

-- 通道表增加状态与排序字段
-- ============================================
-- 后台定时任务配置表
-- 用于 Trigger 模式：定时检测是否运行
-- ============================================
CREATE TABLE IF NOT EXISTS device_task_state (

    device_task_state_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_task_state_name VARCHAR(50) NOT NULL COMMENT '任务名称（唯一）',
    device_task_state_running TINYINT DEFAULT 0 COMMENT '是否正在运行：0 否、1 是',
    device_task_state_last_run_at DATETIME COMMENT '最后运行时间',
    device_task_state_interval INT DEFAULT 30 COMMENT '运行间隔（秒）',
    device_task_state_description VARCHAR(255) COMMENT '描述'
) COMMENT '后台任务运行状态（Trigger 模式）';

-- 初始化任务状态
