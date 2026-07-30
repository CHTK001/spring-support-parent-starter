CREATE TABLE IF NOT EXISTS soft_software (
  software_id VARCHAR(64) NOT NULL PRIMARY KEY,
  software_name VARCHAR(255) NOT NULL,
  software_code VARCHAR(100) NOT NULL UNIQUE,
  software_version VARCHAR(50),
  software_latest_version VARCHAR(50),
  software_description CLOB,
  software_icon VARCHAR(500),
  software_category VARCHAR(100),
  software_tags VARCHAR(500),
  software_download_url VARCHAR(1000),
  software_file_size BIGINT,
  software_md5 VARCHAR(64),
  software_sha256 VARCHAR(100),
  software_min_os_version VARCHAR(50),
  software_cpu_arch VARCHAR(50),
  software_source_id VARCHAR(64),
  software_install_count INT DEFAULT 0,
  software_rating DOUBLE,
  software_is_official BOOLEAN DEFAULT FALSE,
  software_is_recommended BOOLEAN DEFAULT FALSE,
  software_is_free BOOLEAN DEFAULT TRUE,
  software_price INT DEFAULT 0,
  software_status TINYINT DEFAULT 0,
  software_sort INT DEFAULT 0,
  software_extra VARCHAR(10000),
  software_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  software_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  software_create_by VARCHAR(64),
  software_update_by VARCHAR(64)
);
CREATE INDEX idx_software_source ON soft_software(software_source_id);
CREATE INDEX idx_software_status ON soft_software(software_status);

CREATE TABLE IF NOT EXISTS soft_install_record (
  install_record_id VARCHAR(64) NOT NULL PRIMARY KEY,
  install_record_software_id VARCHAR(64) NOT NULL,
  install_record_user_id VARCHAR(64),
  install_record_device_id VARCHAR(64),
  install_record_source_id VARCHAR(64),
  install_record_version VARCHAR(50),
  install_record_target_version VARCHAR(50),
  install_record_operation VARCHAR(20),
  install_record_status TINYINT DEFAULT 0,
  install_record_progress INT DEFAULT 0,
  install_record_start_time TIMESTAMP,
  install_record_end_time TIMESTAMP,
  install_record_duration BIGINT,
  install_record_error_message VARCHAR(1000),
  install_record_error_detail CLOB,
  install_record_log_path VARCHAR(500),
  install_record_download_path VARCHAR(500),
  install_record_install_path VARCHAR(500),
  install_record_rollback_version VARCHAR(50),
  install_record_extra VARCHAR(10000),
  install_record_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  install_record_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_install_record_software ON soft_install_record(install_record_software_id);
CREATE INDEX idx_install_record_status ON soft_install_record(install_record_status);
CREATE INDEX idx_install_record_user ON soft_install_record(install_record_user_id);

CREATE TABLE IF NOT EXISTS soft_config (
  config_id VARCHAR(64) NOT NULL PRIMARY KEY,
  config_software_id VARCHAR(64) NOT NULL,
  config_user_id VARCHAR(64),
  config_device_id VARCHAR(64),
  config_key VARCHAR(100) NOT NULL,
  config_value CLOB,
  config_type VARCHAR(20) DEFAULT 'string',
  config_label VARCHAR(100),
  config_description VARCHAR(500),
  config_group VARCHAR(50),
  config_sort INT DEFAULT 0,
  config_is_visible BOOLEAN DEFAULT TRUE,
  config_is_editable BOOLEAN DEFAULT TRUE,
  config_default_value CLOB,
  config_range VARCHAR(10000),
  config_extra VARCHAR(10000),
  config_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  config_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  config_create_by VARCHAR(64),
  config_update_by VARCHAR(64)
);
CREATE INDEX idx_config_software ON soft_config(config_software_id);

CREATE TABLE IF NOT EXISTS soft_source (
  source_id VARCHAR(64) NOT NULL PRIMARY KEY,
  source_name VARCHAR(100) NOT NULL,
  source_code VARCHAR(50) NOT NULL UNIQUE,
  source_type TINYINT DEFAULT 0,
  source_url VARCHAR(1000),
  source_api_url VARCHAR(1000),
  source_username VARCHAR(100),
  source_password VARCHAR(255),
  source_icon VARCHAR(500),
  source_description VARCHAR(1000),
  source_priority INT DEFAULT 0,
  source_is_enabled BOOLEAN DEFAULT TRUE,
  source_is_default BOOLEAN DEFAULT FALSE,
  source_auto_sync BOOLEAN DEFAULT FALSE,
  source_sync_interval INT DEFAULT 60,
  source_last_sync_time TIMESTAMP,
  source_sync_status TINYINT DEFAULT 0,
  source_software_count INT DEFAULT 0,
  source_extra VARCHAR(10000),
  source_status TINYINT DEFAULT 0,
  source_sort INT DEFAULT 0,
  source_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  source_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  source_create_by VARCHAR(64),
  source_update_by VARCHAR(64)
);
CREATE INDEX idx_source_type ON soft_source(source_type);
CREATE INDEX idx_source_status ON soft_source(source_status);