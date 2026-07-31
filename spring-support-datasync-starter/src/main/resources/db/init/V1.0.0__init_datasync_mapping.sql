-- DataSync 数据库模式：datasync_mapping 映射主表
CREATE TABLE IF NOT EXISTS datasync_mapping (
    datasync_mapping_id VARCHAR(255) PRIMARY KEY COMMENT '映射ID',
    datasync_mapping_input_id VARCHAR(255) COMMENT '输入标识',
    datasync_mapping_source_id VARCHAR(255) COMMENT '源标识',
    datasync_mapping_output_id VARCHAR(255) COMMENT '输出标识',
    datasync_mapping_sink_id VARCHAR(255) COMMENT ' sink标识',
    datasync_mapping_batch INT DEFAULT 100 COMMENT '批量大小',
    datasync_mapping_cron_type VARCHAR(255) COMMENT '定时类型',
    datasync_mapping_cron VARCHAR(255) COMMENT '定时表达式',
    datasync_mapping_params TEXT COMMENT '额外参数',
    datasync_mapping_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    datasync_mapping_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    datasync_mapping_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- DataSync 数据库模式：datasync_field_mapping 字段映射表
CREATE TABLE IF NOT EXISTS datasync_field_mapping (
    datasync_field_mapping_id VARCHAR(255) PRIMARY KEY COMMENT '字段映射ID',
    datasync_field_mapping_mapping_id VARCHAR(255) COMMENT '所属映射ID',
    datasync_field_mapping_source_field VARCHAR(255) COMMENT '源字段名',
    datasync_field_mapping_target_field VARCHAR(255) COMMENT '目标字段名',
    datasync_field_mapping_converter VARCHAR(255) COMMENT '类型转换器',
    datasync_field_mapping_sort INT COMMENT '排序号',
    FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES datasync_mapping(datasync_mapping_id)
);

INSERT INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('db-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);

INSERT INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('f1', 'db-user-sync', 'name', 'user_name', 'toString', 0);
