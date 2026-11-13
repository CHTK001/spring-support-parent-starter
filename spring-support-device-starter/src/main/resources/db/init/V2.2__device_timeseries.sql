-- =====================================================================
-- V2.2__device_timeseries.sql - 设备时序点明细表（bean 引擎）
--
-- 背景：传感器历史、门禁记录、车闸记录改为统一走时序引擎
--   （plugin.device.timeseries.engine=bean/influxdb/redis）。
--   bean 引擎落这张表，influxdb/redis 引擎不读本表。
--
-- 语义：CREATE TABLE IF NOT EXISTS + 幂等索引，存量库重复执行安全。
-- 说明：新建库的同一份结构也已并入 V2.0__init_all_device.sql，
--   本文件负责给「已经建过库」的环境补表。
-- =====================================================================

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
