DROP TABLE IF EXISTS proxy_server_setting;
DROP TABLE IF EXISTS proxy_server;

CREATE TABLE proxy_server (
    proxy_server_id INT PRIMARY KEY AUTO_INCREMENT,
    proxy_server_name VARCHAR(255),
    proxy_server_host VARCHAR(255),
    proxy_server_port INT,
    proxy_server_type VARCHAR(255),
    proxy_server_context_path VARCHAR(255),
    proxy_server_status VARCHAR(11),
    proxy_server_description VARCHAR(255),
    proxy_server_config VARCHAR(255),
    proxy_server_auto_start CHAR(1),
    proxy_server_max_connections INT,
    proxy_server_timeout INT,
    create_name VARCHAR(255),
    create_by INT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    update_name VARCHAR(255),
    update_by INT
);

CREATE TABLE proxy_server_setting (
    proxy_server_setting_id INT PRIMARY KEY AUTO_INCREMENT,
    proxy_server_setting_name VARCHAR(255),
    proxy_server_setting_type VARCHAR(255),
    proxy_server_setting_server_id INT,
    proxy_server_setting_order INT,
    proxy_server_setting_description VARCHAR(255),
    proxy_server_setting_enabled CHAR(1),
    proxy_server_setting_class_name VARCHAR(255),
    proxy_server_setting_version VARCHAR(255),
    proxy_server_setting_config VARCHAR(255),
    proxy_server_setting_filter_id VARCHAR(255),
    proxy_server_setting_https_enabled CHAR(1),
    proxy_server_setting_https_cert_type VARCHAR(11),
    proxy_server_setting_https_pem_cert BLOB,
    proxy_server_setting_https_pem_key BLOB,
    proxy_server_setting_https_pem_key_password VARCHAR(255),
    proxy_server_setting_https_keystore BLOB,
    proxy_server_setting_https_keystore_password VARCHAR(255),
    create_name VARCHAR(255),
    create_by INT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    update_name VARCHAR(255),
    update_by INT
);
