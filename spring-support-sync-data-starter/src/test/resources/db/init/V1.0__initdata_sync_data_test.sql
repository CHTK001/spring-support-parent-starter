-- 插入测试任务
INSERT INTO monitor_sync_task (sync_task_id, sync_task_name, sync_task_description, sync_task_status, sync_task_batch_size)
VALUES (1, '测试同步任务', '用于WebSocket实时监控测试', 'STOPPED', 100);

-- 插入测试节点
INSERT INTO monitor_sync_node (sync_node_id, sync_task_id, sync_node_key, sync_node_name, sync_node_type, sync_node_spi_name, sync_node_config, sync_node_enabled)
VALUES
(1, 1, 'input-1', '测试输入节点', 'INPUT', 'mock', '{"count": 100}', 1),
(2, 1, 'output-1', '测试输出节点', 'OUTPUT', 'mock', '{}', 1);
