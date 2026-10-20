-- =====================================================================
-- V2.0__initdata_all_device.sql - spring-support-device-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。此前分散的
--   V1.0.1__initdata_all_device.sql 与 V1.2__device_task_state_index.sql
--   已在本次收敛中并入本文件并删除。
-- 约束: 全部语句必须幂等（ON DUPLICATE KEY UPDATE），可反复执行。
-- =====================================================================

-- 任务状态种子（幂等）：缺失时补种，避免 shouldRun() 因无状态行而永不执行。
-- TimeScheduler 每个 tick 按 device_task_state_name 查一行；
-- 唯一索引 uk_device_task_state_name 保证同一任务只有一条状态行。
INSERT INTO device_task_state
    (device_task_state_id, device_task_state_name, device_task_state_running,
     device_task_state_interval, device_task_state_description)
VALUES
    (REPLACE(UUID(), '-', ''), 'deviceOfflineCheck', 0, 30, '设备在离线检测任务'),
    (REPLACE(UUID(), '-', ''), 'deviceEventPoll', 0, 30, '设备事件轮询任务')
ON DUPLICATE KEY UPDATE
    device_task_state_interval = VALUES(device_task_state_interval);
