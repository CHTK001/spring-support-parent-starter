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
    device_info_created_at DATETIME COMMENT '创建时间'
) COMMENT '设备基本信息';

-- 设备通道表
CREATE TABLE IF NOT EXISTS device_channel (
    device_channel_id VARCHAR(32) PRIMARY KEY COMMENT '通道主键 ID',
    device_channel_device_id VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_channel_no INT NOT NULL COMMENT '通道号',
    device_channel_name VARCHAR(100) COMMENT '通道名称',
    device_channel_type VARCHAR(20) COMMENT '通道类型：video 视频、door 门禁'
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
CREATE INDEX IF NOT EXISTS idx_device_info_org ON device_info(device_info_org_id);
CREATE INDEX IF NOT EXISTS idx_device_info_category ON device_info(device_info_category);
CREATE INDEX IF NOT EXISTS idx_device_channel_device ON device_channel(device_channel_device_id);
CREATE INDEX IF NOT EXISTS idx_device_event_device ON device_event_record(device_event_record_device_id);
CREATE INDEX IF NOT EXISTS idx_device_event_time ON device_event_record(device_event_record_occurred_at);
CREATE INDEX IF NOT EXISTS idx_device_door_device ON device_door(device_door_device_id);
CREATE INDEX IF NOT EXISTS idx_device_org_parent ON device_organization(device_organization_parent_id);