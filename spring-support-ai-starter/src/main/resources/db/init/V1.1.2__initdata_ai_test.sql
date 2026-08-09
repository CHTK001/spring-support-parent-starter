-- ============================================================
-- AI 测试种子数据（异步 INITDATA 脚本）
-- 包含：供应商、模型、分组、AppKey 等测试数据
-- 使用 INSERT IGNORE 保证幂等
-- ============================================================

-- 1. 模型供应商（MockOpenAI）
INSERT IGNORE INTO `sys_ai_provider` (
    `sys_ai_provider_id`, `sys_ai_provider_name`, `sys_ai_provider_type`,
    `sys_ai_provider_base_url`, `sys_ai_provider_api_key`,
    `sys_ai_provider_models`, `sys_ai_provider_enabled`,
    `sys_ai_provider_deleted`, `create_name`, `create_time`, `update_time`
) VALUES (
    1, 'MockOpenAI', 'openai',
    'http://127.0.0.1:9901/v1', 'sk-mock',
    '["mock-gpt-4o"]', 1, 0, 'system',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 2. 模型定义（mock-gpt-4o）
INSERT IGNORE INTO `sys_ai_model` (
    `sys_ai_model_id`, `sys_ai_model_name`, `sys_ai_model_code`,
    `sys_ai_model_provider_id`, `sys_ai_model_capabilities`,
    `sys_ai_model_enabled`, `sys_ai_model_sort`,
    `sys_ai_model_create_time`, `sys_ai_model_update_time`
) VALUES (
    1, 'MockGPT-4o', 'mock-gpt-4o',
    1, 'chat', 1, 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 3. 模型分组（研发组）
INSERT IGNORE INTO `sys_ai_group` (
    `sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`,
    `sys_ai_group_models`, `sys_ai_group_enabled`,
    `sys_ai_group_delete`, `create_time`, `update_time`
) VALUES (
    1, '研发组', '测试分组',
    '["mock-gpt-4o"]', 1, 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 4. 分组-模型关联
INSERT IGNORE INTO `sys_ai_group_model` (
    `sys_ai_group_model_id`, `sys_ai_group_id`, `sys_ai_module_id`, `create_time`
) VALUES (
    1, 1, 1, CURRENT_TIMESTAMP
);

-- 5. AppKey（sk-chat-verify）
INSERT IGNORE INTO `sys_ai_app_key` (
    `sys_ai_app_key_id`, `sys_ai_app_key_app_key`, `sys_ai_app_key_secret`,
    `sys_ai_app_key_name`, `sys_ai_app_key_provider_id`, `sys_ai_app_key_model`,
    `sys_ai_app_key_group_id`, `sys_ai_app_key_status`, `sys_ai_app_key_delete`,
    `sys_ai_app_key_use_count`, `sys_ai_app_key_max_use`,
    `sys_ai_app_key_max_tokens`, `sys_ai_app_key_total_tokens`,
    `create_name`, `create_time`, `update_time`
) VALUES (
    1, 'sk-chat-verify', 'sk-chat-verify',
    '测试应用', 1, 'mock-gpt-4o',
    1, 1, 0,
    0, NULL, NULL, 0,
    'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);