INSERT INTO monitor_sync_task (
    sync_task_id,
    sync_task_name,
    sync_task_desc,
    sync_task_status,
    sync_task_batch_size,
    sync_task_retry_count,
    sync_task_retry_interval,
    sync_task_ack_enabled,
    sync_task_transaction_enabled,
    sync_task_sync_mode,
    sync_task_create_time,
    sync_task_update_time
) VALUES (
    1,
    '测试同步任务',
    '用于 Web UI 联调与监控测试',
    'STOPPED',
    100,
    3,
    1000,
    1,
    0,
    'FULL',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO monitor_sync_node (
    sync_node_id,
    sync_task_id,
    sync_node_key,
    sync_node_name,
    sync_node_type,
    sync_node_spi_name,
    sync_node_config,
    sync_node_position,
    sync_node_enabled,
    sync_node_create_time,
    sync_node_update_time
) VALUES
    (1, 1, 'input-1', '测试输入节点', 'INPUT', 'mock', '{"count": 100}', '{"x":120,"y":160}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 1, 'output-1', '测试输出节点', 'OUTPUT', 'mock', '{}', '{"x":520,"y":160}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO monitor_sync_connection (
    sync_connection_id,
    sync_task_id,
    source_node_id,
    source_node_key,
    source_handle,
    target_node_id,
    target_node_key,
    target_handle,
    connection_type,
    connection_label
) VALUES (
    1,
    1,
    1,
    'input-1',
    'output',
    2,
    'output-1',
    'input',
    'DATA',
    '默认连线'
);
