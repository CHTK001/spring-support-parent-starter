-- =====================================================================
-- V1.0__initdata_all_device.sql - 设备模块初始化数据
-- 设备任务状态初始化已归入 V1.0__init_all_device.sql 的兼容初始化段。
-- =====================================================================

INSERT INTO device_task_state (device_task_state_id, device_task_state_name, device_task_state_running, device_task_state_interval, device_task_state_description)
VALUES
    (REPLACE(UUID(),'-',''), 'deviceOfflineCheck', 0, 30, '设备在离线检测任务'),
    (REPLACE(UUID(),'-',''), 'deviceEventPoll', 0, 30, '设备事件轮询任务');
