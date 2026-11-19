-- =====================================================================
-- V2.6__initdata_all_device.sql - spring-support-device-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 版本递进原因: FlywayLike 按「脚本名 + 版本」记账，无成功记录即执行；
--   本文件收敛取代 V2.4__initdata_all_device.sql，种子内容不变、仅递进版本号。
-- 约束: 全部语句必须幂等（ON DUPLICATE KEY UPDATE），可反复执行；禁止任何 DDL。
-- =====================================================================

-- 任务状态种子（幂等）：缺失时补种，避免 shouldRun() 因无状态行而永不执行。
-- TimeScheduler 每个 tick 按 device_task_state_name 查一行；
-- 唯一索引 uk_device_task_state_name 保证同一任务只有一条状态行。
-- 冲突时只刷新描述，不覆盖 interval / running —— 用户改过的间隔不会被重启抹掉。
INSERT INTO device_task_state
    (device_task_state_id, device_task_state_name, device_task_state_running,
     device_task_state_interval, device_task_state_description)
VALUES
    (REPLACE(UUID(), '-', ''), 'deviceOfflineCheck', 0, 30, '设备在离线检测任务'),
    (REPLACE(UUID(), '-', ''), 'deviceEventPoll', 0, 30, '设备事件轮询任务'),
    (REPLACE(UUID(), '-', ''), 'deviceSync', 0, 600, '厂商设备目录同步任务'),
    (REPLACE(UUID(), '-', ''), 'deviceAccessExtract', 0, 60, '门禁出入记录抽取任务'),
    (REPLACE(UUID(), '-', ''), 'deviceVehiclePassExtract', 0, 60, '车闸过车记录抽取任务'),
    (REPLACE(UUID(), '-', ''), 'deviceAttendanceSync', 0, 300, '考勤记录归集任务')
ON DUPLICATE KEY UPDATE
    device_task_state_description = VALUES(device_task_state_description);
