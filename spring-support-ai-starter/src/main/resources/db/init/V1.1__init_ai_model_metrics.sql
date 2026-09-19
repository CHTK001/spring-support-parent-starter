-- ============================================================================
-- 模型指标库（V1.1）
-- 独立于「配置模型 sys_ai_provider_model」：全量存储数据源（Artificial Analysis）
-- 采集到的模型指标，与系统里配置了哪些模型无关。模型价格/能力按 model_key
-- 关联此表；manual_locked 中标记的字段在全量同步时不被覆盖（手动锁定）。
-- 计费取价顺序：官方定价 sys_ai_pricing → 本指标库价格兜底预估。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_ai_model_metrics` (
    `sys_ai_model_metrics_id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `sys_ai_model_metrics_model_key`             VARCHAR(160)  NOT NULL COMMENT '归一化模型键（唯一）',
    `sys_ai_model_metrics_model`                 VARCHAR(220)  NULL COMMENT '原始模型标识/slug',
    `sys_ai_model_metrics_provider`              VARCHAR(128)  NULL COMMENT '厂商',
    `sys_ai_model_metrics_price_input`           DOUBLE        NULL COMMENT '输入价 USD/百万 token',
    `sys_ai_model_metrics_price_output`          DOUBLE        NULL COMMENT '输出价 USD/百万 token',
    `sys_ai_model_metrics_price_cache_hit`       DOUBLE        NULL COMMENT '缓存命中价 USD/百万',
    `sys_ai_model_metrics_price_cache_write`     DOUBLE        NULL COMMENT '缓存写入价 USD/百万',
    `sys_ai_model_metrics_intelligence_index`    DOUBLE        NULL COMMENT '智能指数',
    `sys_ai_model_metrics_output_speed_tps`      DOUBLE        NULL COMMENT '输出速度 token/秒',
    `sys_ai_model_metrics_latency_first_token`   DOUBLE        NULL COMMENT '首 token 延迟（秒）',
    `sys_ai_model_metrics_context_window`        BIGINT        NULL COMMENT '上下文窗口 token',
    `sys_ai_model_metrics_icon_url`              VARCHAR(512)  NULL COMMENT '模型图标',
    `sys_ai_model_metrics_support_reasoning`     TINYINT(1)    NULL COMMENT '是否支持深度思考',
    `sys_ai_model_metrics_support_web_search`    TINYINT(1)    NULL COMMENT '是否支持联网检索',
    `sys_ai_model_metrics_support_image_input`   TINYINT(1)    NULL COMMENT '是否支持视觉输入',
    `sys_ai_model_metrics_support_function_call` TINYINT(1)    NULL COMMENT '是否支持函数调用',
    `sys_ai_model_metrics_manual_locked`         TEXT          NULL COMMENT '手动锁定字段名 JSON 数组',
    `sys_ai_model_metrics_manual_updated_at`     BIGINT        NULL COMMENT '手动更新时间（毫秒）',
    `sys_ai_model_metrics_manual_updated_by`     VARCHAR(64)   NULL COMMENT '手动更新人',
    `sys_ai_model_metrics_source`                VARCHAR(40)   NULL COMMENT '数据源 artificialanalysis',
    `sys_ai_model_metrics_status`                TINYINT       NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time`                                BIGINT        NULL COMMENT '创建时间（毫秒）',
    `update_time`                                BIGINT        NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_model_metrics_id`),
    UNIQUE KEY `uk_sys_ai_model_metrics_key` (`sys_ai_model_metrics_model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型指标库（全量，模型按 key 关联取价）';
