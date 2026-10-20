-- 设备任务状态：按任务名查询/更新的唯一索引 + 任务种子
--
-- 背景：TimeScheduler 每个 tick 调用 shouldRun()，按 device_task_state_name
-- 查询一行；此前该列没有索引，属于全表扫描。唯一索引同时保证同一任务只有一条状态行。
-- 清理历史重复任务名（若存在），保证唯一索引可创建
DELETE t1 FROM device_task_state t1
INNER JOIN device_task_state t2
WHERE t1.device_task_state_name = t2.device_task_state_name
  AND t1.device_task_state_id > t2.device_task_state_id;

CREATE UNIQUE INDEX uk_device_task_state_name
    ON device_task_state (device_task_state_name);

-- 任务状态种子（幂等）：缺失时补种，避免 shouldRun() 因无状态行而永不执行。
INSERT INTO device_task_state
    (device_task_state_id, device_task_state_name, device_task_state_running,
     device_task_state_interval, device_task_state_description)
VALUES
    (REPLACE(UUID(), '-', ''), 'deviceOfflineCheck', 0, 30, '设备在离线检测任务'),
    (REPLACE(UUID(), '-', ''), 'deviceEventPoll', 0, 30, '设备事件轮询任务')
ON DUPLICATE KEY UPDATE
    device_task_state_interval = VALUES(device_task_state_interval);
