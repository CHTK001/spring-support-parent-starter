CREATE TABLE IF NOT EXISTS datasync_mapping (
    datasync_mapping_id VARCHAR(255) PRIMARY KEY,
    datasync_mapping_input_id VARCHAR(255),
    datasync_mapping_source_id VARCHAR(255),
    datasync_mapping_output_id VARCHAR(255),
    datasync_mapping_sink_id VARCHAR(255),
    datasync_mapping_batch INT DEFAULT 100,
    datasync_mapping_cron_type VARCHAR(255),
    datasync_mapping_cron VARCHAR(255),
    datasync_mapping_params TEXT,
    datasync_mapping_enabled BOOLEAN DEFAULT TRUE,
    datasync_mapping_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    datasync_mapping_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS datasync_field_mapping (
    datasync_field_mapping_id VARCHAR(255) PRIMARY KEY,
    datasync_field_mapping_mapping_id VARCHAR(255),
    datasync_field_mapping_source_field VARCHAR(255),
    datasync_field_mapping_target_field VARCHAR(255),
    datasync_field_mapping_converter VARCHAR(255),
    datasync_field_mapping_sort INT,
    FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES datasync_mapping(datasync_mapping_id)
);

INSERT INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('test-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);

INSERT INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('f1', 'test-user-sync', 'name', 'user_name', 'toString', 0);
