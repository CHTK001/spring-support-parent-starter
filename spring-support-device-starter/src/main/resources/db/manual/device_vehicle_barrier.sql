-- 车辆档案 / 车闸点位 / 过车记录（手动执行，与 device_webhook_alarm.sql 同口径）
-- 字符集与设备库其它表保持一致：utf8mb4 / utf8mb4_general_ci

CREATE TABLE IF NOT EXISTS `device_vehicle` (
  `device_vehicle_id` varchar(64) NOT NULL COMMENT '主键',
  `device_vehicle_plate_no` varchar(32) NOT NULL COMMENT '车牌号',
  `device_vehicle_plate_color` varchar(16) DEFAULT NULL COMMENT '车牌颜色',
  `device_vehicle_type` varchar(32) DEFAULT NULL COMMENT '车辆类型',
  `device_vehicle_owner_name` varchar(64) DEFAULT NULL COMMENT '车主姓名',
  `device_vehicle_owner_phone` varchar(32) DEFAULT NULL COMMENT '车主电话',
  `device_vehicle_list_type` varchar(16) DEFAULT 'normal' COMMENT '名单类型：white/black/normal',
  `device_vehicle_valid_from` datetime DEFAULT NULL COMMENT '有效期起',
  `device_vehicle_valid_to` datetime DEFAULT NULL COMMENT '有效期止',
  `device_vehicle_vendor` varchar(32) DEFAULT NULL COMMENT '厂商标识',
  `device_vehicle_external_id` varchar(128) DEFAULT NULL COMMENT '厂商侧车辆标识',
  `device_vehicle_remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `device_vehicle_deleted` tinyint(1) DEFAULT '0' COMMENT '删除标记',
  `device_vehicle_created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `device_vehicle_updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`device_vehicle_id`),
  KEY `idx_vehicle_plate` (`device_vehicle_plate_no`),
  KEY `idx_vehicle_vendor_ext` (`device_vehicle_vendor`, `device_vehicle_external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆档案';

CREATE TABLE IF NOT EXISTS `device_barrier_gate` (
  `device_barrier_gate_id` varchar(64) NOT NULL COMMENT '主键',
  `device_barrier_gate_name` varchar(128) DEFAULT NULL COMMENT '车闸名称',
  `device_barrier_gate_device_id` varchar(64) DEFAULT NULL COMMENT '所属设备',
  `device_barrier_gate_channel_no` int DEFAULT NULL COMMENT '通道号',
  `device_barrier_gate_direction` varchar(16) DEFAULT 'both' COMMENT '方向：in/out/both',
  `device_barrier_gate_status` varchar(16) DEFAULT 'unknown' COMMENT '状态',
  `device_barrier_gate_vendor` varchar(32) DEFAULT NULL COMMENT '厂商标识',
  `device_barrier_gate_external_id` varchar(128) DEFAULT NULL COMMENT '厂商侧车闸标识',
  `device_barrier_gate_deleted` tinyint(1) DEFAULT '0' COMMENT '删除标记',
  `device_barrier_gate_created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `device_barrier_gate_updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`device_barrier_gate_id`),
  KEY `idx_barrier_device` (`device_barrier_gate_device_id`),
  KEY `idx_barrier_direction` (`device_barrier_gate_direction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车闸点位';

CREATE TABLE IF NOT EXISTS `device_vehicle_pass` (
  `device_vehicle_pass_id` varchar(64) NOT NULL COMMENT '主键',
  `device_vehicle_pass_gate_id` varchar(64) DEFAULT NULL COMMENT '车闸标识',
  `device_vehicle_pass_plate_no` varchar(32) DEFAULT NULL COMMENT '车牌号',
  `device_vehicle_pass_direction` varchar(16) DEFAULT NULL COMMENT '方向：in/out',
  `device_vehicle_pass_time` datetime DEFAULT NULL COMMENT '过车时间',
  `device_vehicle_pass_photo_url` varchar(1024) DEFAULT NULL COMMENT '抓拍图片地址',
  `device_vehicle_pass_vehicle_type` varchar(32) DEFAULT NULL COMMENT '车辆类型',
  `device_vehicle_pass_created_at` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`device_vehicle_pass_id`),
  KEY `idx_pass_gate_time` (`device_vehicle_pass_gate_id`, `device_vehicle_pass_time`),
  KEY `idx_pass_plate_time` (`device_vehicle_pass_plate_no`, `device_vehicle_pass_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆过车记录';

CREATE TABLE IF NOT EXISTS `device_vehicle_pass_cursor` (
  `device_vehicle_pass_cursor_device_id` varchar(64) NOT NULL COMMENT '设备标识',
  `device_vehicle_pass_cursor_last_msg_id` bigint NOT NULL DEFAULT '0' COMMENT '最后处理的平台消息 ID',
  `device_vehicle_pass_cursor_updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`device_vehicle_pass_cursor_device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='过车拉取游标';
