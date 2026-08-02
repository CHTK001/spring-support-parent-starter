-- ============================================
-- 设备管理模块增量脚本：通道管理、地图位置、定时检测
-- 版本：1.1
-- 作者：CH
-- ============================================

-- 设备表增加地图位置字段
ALTER TABLE device_info
    ADD COLUMN device_info_latitude DECIMAL(10, 6) COMMENT '纬度（地图位置）',
    ADD COLUMN device_info_longitude DECIMAL(10, 6) COMMENT '经度（地图位置）',
    ADD COLUMN device_info_address VARCHAR(255) COMMENT '详细地址',
    ADD COLUMN device_info_serial VARCHAR(64) COMMENT '设备序列号',
    ADD COLUMN device_info_firmware VARCHAR(32) COMMENT '固件版本',
    ADD COLUMN device_info_channel_count INT DEFAULT 0 COMMENT '通道数量',
    ADD COLUMN device_info_offline_threshold INT DEFAULT 60 COMMENT '离线检测阈值（秒）',
    ADD COLUMN device_info_updated_at DATETIME COMMENT '更新时间';

-- 通道表增加状态与排序字段
ALTER TABLE device_channel
    ADD COLUMN device_channel_status VARCHAR(20) DEFAULT 'enabled' COMMENT '通道状态：enabled、disabled',
    ADD COLUMN device_channel_sort INT DEFAULT 0 COMMENT '排序';

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
INSERT INTO device_task_state (device_task_state_id, device_task_state_name, device_task_state_running, device_task_state_interval, device_task_state_description)
VALUES
    (REPLACE(UUID(),'-',''), 'deviceOfflineCheck', 0, 30, '设备在离线检测任务'),
    (REPLACE(UUID(),'-',''), 'deviceEventPoll', 0, 30, '设备事件轮询任务');

-- 索引
CREATE INDEX IF NOT EXISTS idx_device_info_location ON device_info(device_info_latitude, device_info_longitude);
CREATE INDEX IF NOT EXISTS idx_device_task_name ON device_task_state(device_task_state_name);