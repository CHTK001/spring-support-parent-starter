CREATE TABLE IF NOT EXISTS datasync_mapping (
    mapping_id VARCHAR(255) PRIMARY KEY,
    input_id VARCHAR(255),
    source_id VARCHAR(255),
    output_id VARCHAR(255),
    sink_id VARCHAR(255),
    batch INT DEFAULT 100,
    cron_type VARCHAR(255),
    cron VARCHAR(255),
    params TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS datasync_field_mapping (
    id VARCHAR(255) PRIMARY KEY,
    mapping_id VARCHAR(255),
    source_field VARCHAR(255),
    target_field VARCHAR(255),
    converter VARCHAR(255),
    sort INT,
    FOREIGN KEY (mapping_id) REFERENCES datasync_mapping(mapping_id)
);

INSERT INTO datasync_mapping (mapping_id, input_id, source_id, output_id, sink_id, batch, cron_type, cron, params, enabled)
VALUES ('test-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);

INSERT INTO datasync_field_mapping (id, mapping_id, source_field, target_field, converter, sort)
VALUES ('f1', 'test-user-sync', 'name', 'user_name', 'toString', 0);
