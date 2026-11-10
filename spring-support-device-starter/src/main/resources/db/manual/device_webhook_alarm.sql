-- ---------------------------------------------------------------------
-- 设备 webhook 入站 + 告警（4.0.0.51 新增，手工执行；不进 Flyway 版本目录，
-- 避免改动既有 V2.0 脚本的 checksum）
--
-- 作用：
--   1) device_webhook_appkey  外部系统推送门禁/告警用的 appKey + secret
--   2) device_webhook_receipt 入站幂等回执（不改既有门禁记录表结构）
--   3) device_alarm           告警事件落库，供告警页签查询/处理
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS device_webhook_appkey (
    device_webhook_appkey_id          VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_webhook_appkey_key         VARCHAR(64) NOT NULL COMMENT '公开标识 appKey',
    device_webhook_appkey_secret      VARCHAR(128) NOT NULL COMMENT '签名密钥 secret',
    device_webhook_appkey_name        VARCHAR(100) NOT NULL COMMENT '凭据名称，同时作为入站 source',
    device_webhook_appkey_scopes      VARCHAR(200) DEFAULT 'access,alarm' COMMENT '允许主题：access,alarm',
    device_webhook_appkey_expires_at  DATETIME NULL COMMENT '过期时间；NULL 表示永久有效',
    device_webhook_appkey_enabled     TINYINT DEFAULT 1 COMMENT '1 启用、0 停用',
    device_webhook_appkey_last_used_at DATETIME NULL COMMENT '最近一次成功调用时间',
    device_webhook_appkey_created_at  DATETIME NULL COMMENT '创建时间',
    UNIQUE KEY uk_webhook_appkey_key (device_webhook_appkey_key)
) COMMENT '设备 webhook 入站应用凭据';

CREATE TABLE IF NOT EXISTS device_webhook_receipt (
    device_webhook_receipt_id          VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_webhook_receipt_source      VARCHAR(100) NOT NULL COMMENT '来源（appKey 名称）',
    device_webhook_receipt_external_id VARCHAR(128) NOT NULL COMMENT '外部事件 ID 或包体摘要',
    device_webhook_receipt_topic       VARCHAR(32) NOT NULL COMMENT '主题：access / alarm',
    device_webhook_receipt_created_at  DATETIME NULL COMMENT '入库时间',
    UNIQUE KEY uk_webhook_receipt (device_webhook_receipt_source, device_webhook_receipt_external_id, device_webhook_receipt_topic),
    KEY idx_webhook_receipt_time (device_webhook_receipt_created_at)
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
    device_alarm_created_at    DATETIME NULL COMMENT '入库时间',
    KEY idx_alarm_time (device_alarm_occurred_at),
    KEY idx_alarm_status (device_alarm_status),
    KEY idx_alarm_level (device_alarm_level),
    KEY idx_alarm_device (device_alarm_device_id)
) COMMENT '设备告警（webhook 入站落库）';

-- ---------------------------------------------------------------------
-- 补齐既有 DDL：V2.0__init_all_device.sql 里的门禁出入记录表。
-- 宿主以 spring.sql.init.mode=never 启动时不会执行版本脚本，webhook 入库
-- 会直接报 Table 'monitordb.device_access_record' doesn't exist。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_access_record (
    device_access_record_id         VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_access_record_device_id  VARCHAR(32) NOT NULL COMMENT '所属设备 ID',
    device_access_record_door_id    VARCHAR(64) COMMENT '门标识（厂商侧）',
    device_access_record_card_no    VARCHAR(64) COMMENT '卡号',
    device_access_record_user_name  VARCHAR(100) COMMENT '人员姓名',
    device_access_record_event_type VARCHAR(30) COMMENT '事件类型',
    device_access_record_direction  VARCHAR(16) COMMENT '进出方向：in / out',
    device_access_record_photo_url  VARCHAR(512) COMMENT '抓拍照片地址',
    device_access_record_occurred_at DATETIME COMMENT '刷卡时间',
    KEY idx_acc_device_time (device_access_record_device_id, device_access_record_occurred_at)
) COMMENT '门禁出入记录（定时抽取 / webhook 推送共用）';
