-- =====================================================================
-- V2.3__device_schema_repair.sql - 存量库结构补齐
--
-- 背景：V2.0 是「唯一建表入口」，但它在历史演进中不断追加 CREATE TABLE 与补列语句；
--   已经被标记执行过的库存不到这些新增内容，于是出现结构性漂移 ——
--   典型表现是列表接口 500「数据操作失败，请稍后重试」（实体有列、库里没有）。
--
-- 本文件把当前 V2.0 里「老库可能缺」的对象全部再声明一遍：
--   - device_person / device_metric_latest / device_vehicle / device_barrier_gate /
--     device_vehicle_pass / device_attendance_record / device_vehicle_pass_cursor
--   - device_provider_credential 的 group_no / metric_collect_enabled / metric_collect_interval
--
-- 约束：全部语句幂等（CREATE TABLE IF NOT EXISTS + information_schema 判存后动态 ALTER），
--       新库与老库都可反复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 厂商人员表
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
    device_person_created_at    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    device_person_updated_at    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_person_vendor (`device_person_vendor`),
    KEY idx_person_external (`device_person_vendor`, `device_person_external_id`),
    KEY idx_person_org (`device_person_vendor`, `device_person_org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='厂商人员表';

-- ---------------------------------------------------------------------
-- 环境指标最新快照
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_metric_latest (
    device_metric_id VARCHAR(32) PRIMARY KEY COMMENT '主键 ID',
    device_info_id VARCHAR(32) NOT NULL COMMENT '设备 ID',
    device_metric_key VARCHAR(128) NOT NULL COMMENT '节点稳定标识（厂商节点 ID 或名称+单位）',
    device_metric_name VARCHAR(64) COMMENT '节点名称',
    device_metric_value VARCHAR(128) COMMENT '读数（字符串兼容风向/天气现象）',
    device_metric_unit VARCHAR(32) COMMENT '单位',
    device_metric_data_time DATETIME COMMENT '厂商侧数据时间',
    device_metric_collected_at DATETIME COMMENT '本地采集时间',
    UNIQUE KEY uk_device_metric (device_info_id, device_metric_key),
    KEY idx_device_metric_device (device_info_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备环境指标最新快照';

-- ---------------------------------------------------------------------
-- 车辆档案 / 车闸点位 / 过车记录 / 过车拉取游标 / 考勤记录
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
    device_vehicle_created_at   DATETIME DEFAULT NULL COMMENT '创建时间',
    device_vehicle_updated_at   DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (device_vehicle_id),
    KEY idx_vehicle_plate (device_vehicle_plate_no),
    KEY idx_vehicle_vendor_ext (device_vehicle_vendor, device_vehicle_external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆档案';

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
    device_barrier_gate_created_at  DATETIME DEFAULT NULL COMMENT '创建时间',
    device_barrier_gate_updated_at  DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (device_barrier_gate_id),
    KEY idx_barrier_device (device_barrier_gate_device_id),
    KEY idx_barrier_direction (device_barrier_gate_direction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车闸点位';

CREATE TABLE IF NOT EXISTS device_vehicle_pass (
    device_vehicle_pass_id           VARCHAR(64) NOT NULL COMMENT '主键',
    device_vehicle_pass_device_id    VARCHAR(64) DEFAULT NULL COMMENT '所属设备 ID',
    device_vehicle_pass_gate_id      VARCHAR(64) DEFAULT NULL COMMENT '车闸标识',
    device_vehicle_pass_plate_no     VARCHAR(32) DEFAULT NULL COMMENT '车牌号',
    device_vehicle_pass_direction    VARCHAR(16) DEFAULT NULL COMMENT '方向：in/out',
    device_vehicle_pass_time         DATETIME DEFAULT NULL COMMENT '过车时间',
    device_vehicle_pass_photo_url    VARCHAR(1024) DEFAULT NULL COMMENT '抓拍图片地址',
    device_vehicle_pass_vehicle_type VARCHAR(32) DEFAULT NULL COMMENT '车辆类型',
    device_vehicle_pass_created_at   DATETIME DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (device_vehicle_pass_id),
    KEY idx_pass_gate_time (device_vehicle_pass_gate_id, device_vehicle_pass_time),
    KEY idx_pass_device_time (device_vehicle_pass_device_id, device_vehicle_pass_time),
    KEY idx_pass_plate_time (device_vehicle_pass_plate_no, device_vehicle_pass_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆过车记录';

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
    device_attendance_record_created_at   DATETIME DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (device_attendance_record_id),
    KEY idx_attendance_time (device_attendance_record_occurred_at),
    KEY idx_attendance_device_time (device_attendance_record_device_id, device_attendance_record_occurred_at),
    KEY idx_attendance_source (device_attendance_record_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考勤记录';

CREATE TABLE IF NOT EXISTS device_vehicle_pass_cursor (
    device_vehicle_pass_cursor_device_id   VARCHAR(64) NOT NULL COMMENT '设备标识',
    device_vehicle_pass_cursor_last_msg_id BIGINT NOT NULL DEFAULT '0' COMMENT '最后处理的平台消息 ID',
    device_vehicle_pass_cursor_updated_at  DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (device_vehicle_pass_cursor_device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '过车拉取游标';

-- ---------------------------------------------------------------------
-- device_provider_credential 补列（云眸组编号 + 凭据级指标采集）
-- 老库缺这几列时，凭据列表接口会因「未知列」直接 500。
-- ---------------------------------------------------------------------
SET @has_cred_group_no := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential'
      AND COLUMN_NAME = 'device_credential_group_no'
);
SET @sql_cred_group_no := IF(@has_cred_group_no > 0, 'SELECT 1',
    'ALTER TABLE device_provider_credential ADD COLUMN device_credential_group_no VARCHAR(64) COMMENT ''云眸组编号 groupNo（云眸平台注册设备必填）'' AFTER device_credential_project_id');
PREPARE stmt_cred_group_no FROM @sql_cred_group_no;
EXECUTE stmt_cred_group_no;
DEALLOCATE PREPARE stmt_cred_group_no;

SET @has_cred_metric_enabled := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential'
      AND COLUMN_NAME = 'device_credential_metric_collect_enabled'
);
SET @sql_cred_metric_enabled := IF(@has_cred_metric_enabled > 0, 'SELECT 1',
    'ALTER TABLE device_provider_credential ADD COLUMN device_credential_metric_collect_enabled TINYINT DEFAULT 0 COMMENT ''该凭据维度的环境指标采集开关'' AFTER device_credential_org_sync_interval');
PREPARE stmt_cred_metric_enabled FROM @sql_cred_metric_enabled;
EXECUTE stmt_cred_metric_enabled;
DEALLOCATE PREPARE stmt_cred_metric_enabled;

SET @has_cred_metric_interval := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_provider_credential'
      AND COLUMN_NAME = 'device_credential_metric_collect_interval'
);
SET @sql_cred_metric_interval := IF(@has_cred_metric_interval > 0, 'SELECT 1',
    'ALTER TABLE device_provider_credential ADD COLUMN device_credential_metric_collect_interval INT COMMENT ''该凭据维度的环境指标采集间隔（秒）'' AFTER device_credential_metric_collect_enabled');
PREPARE stmt_cred_metric_interval FROM @sql_cred_metric_interval;
EXECUTE stmt_cred_metric_interval;
DEALLOCATE PREPARE stmt_cred_metric_interval;
