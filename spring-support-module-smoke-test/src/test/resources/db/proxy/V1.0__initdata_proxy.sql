INSERT INTO proxy_server (
    proxy_server_id,
    proxy_server_name,
    proxy_server_host,
    proxy_server_port,
    proxy_server_type,
    proxy_server_context_path,
    proxy_server_status,
    proxy_server_description,
    proxy_server_auto_start,
    proxy_server_max_connections,
    proxy_server_timeout,
    create_time,
    update_time
) VALUES (
    1,
    'smoke-proxy-server',
    '127.0.0.1',
    18080,
    'HTTP',
    '/proxy',
    'STOPPED',
    'proxy smoke test server',
    '0',
    128,
    30000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO proxy_server_setting (
    proxy_server_setting_id,
    proxy_server_setting_name,
    proxy_server_setting_type,
    proxy_server_setting_server_id,
    proxy_server_setting_order,
    proxy_server_setting_description,
    proxy_server_setting_enabled,
    create_time,
    update_time
) VALUES (
    1,
    'smoke-default-filter',
    'DEFAULT',
    1,
    1,
    'proxy smoke test filter',
    '1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
