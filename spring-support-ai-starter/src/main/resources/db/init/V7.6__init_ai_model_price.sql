-- V7.6: 模型价格独立表（与指标业务分离）
-- 价格数据从 sys_ai_model_metrics 拆出，按 model_key 粒度独立维护；
-- 指标表旧价格列保留只读（历史回退），不再新增写入。
CREATE TABLE IF NOT EXISTS `sys_ai_model_price` (
    `sys_ai_model_price_id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `sys_ai_model_price_model_key`          VARCHAR(160)  NOT NULL COMMENT '归一化模型键（唯一）',
    `sys_ai_model_price_model`              VARCHAR(220)  NULL COMMENT '原始模型标识/slug',
    `sys_ai_model_price_provider`           VARCHAR(128)  NULL COMMENT '厂商',
    `sys_ai_model_price_input`              DOUBLE        NULL COMMENT '输入价 USD/百万 token',
    `sys_ai_model_price_output`             DOUBLE        NULL COMMENT '输出价 USD/百万 token',
    `sys_ai_model_price_cache_hit`          DOUBLE        NULL COMMENT '缓存命中价 USD/百万',
    `sys_ai_model_price_cache_write`        DOUBLE        NULL COMMENT '缓存写入价 USD/百万',
    `sys_ai_model_price_cost_per_task`      DOUBLE        NULL COMMENT '单任务平均成本（USD）',
    `sys_ai_model_price_pricing_rules`      TEXT          NULL COMMENT '峰谷 / 按 N 次定价规则 JSON',
    `sys_ai_model_price_manual_locked`      TEXT          NULL COMMENT '手动锁定价格字段名 JSON 数组',
    `sys_ai_model_price_manual_updated_at`  BIGINT        NULL COMMENT '价格手动更新时间（毫秒）',
    `sys_ai_model_price_manual_updated_by`  VARCHAR(64)   NULL COMMENT '价格手动更新人',
    `sys_ai_model_price_source`             VARCHAR(40)   NULL COMMENT '数据源（upstream:/models / catalog 等）',
    `sys_ai_model_price_status`             TINYINT       NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time`                           BIGINT        NULL COMMENT '创建时间（毫秒）',
    `update_time`                           BIGINT        NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_model_price_id`),
    UNIQUE KEY `uk_sys_ai_model_price_key` (`sys_ai_model_price_model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型价格库（与指标分离，按模型键独立计费）';

-- 存量回填：把指标表中含任一价格数据的行迁入价格表（幂等，已有键不重复插入）。
-- legacy manual_locked 中若锁定过价格字段，则整组复制进价格表；
-- 价格表读取锁时只认价格字段名，多余条目无副作用。
INSERT INTO `sys_ai_model_price` (
    `sys_ai_model_price_model_key`, `sys_ai_model_price_model`, `sys_ai_model_price_provider`,
    `sys_ai_model_price_input`, `sys_ai_model_price_output`,
    `sys_ai_model_price_cache_hit`, `sys_ai_model_price_cache_write`,
    `sys_ai_model_price_cost_per_task`, `sys_ai_model_price_pricing_rules`,
    `sys_ai_model_price_manual_locked`, `sys_ai_model_price_source`,
    `sys_ai_model_price_status`, `create_time`, `update_time`
)
SELECT
    m.`sys_ai_model_metrics_model_key`,
    m.`sys_ai_model_metrics_model`,
    m.`sys_ai_model_metrics_provider`,
    m.`sys_ai_model_metrics_price_input`,
    m.`sys_ai_model_metrics_price_output`,
    m.`sys_ai_model_metrics_price_cache_hit`,
    m.`sys_ai_model_metrics_price_cache_write`,
    m.`sys_ai_model_metrics_cost_per_task`,
    m.`sys_ai_model_metrics_pricing_rules`,
    CASE
        WHEN m.`sys_ai_model_metrics_manual_locked` IS NOT NULL
             AND (m.`sys_ai_model_metrics_manual_locked` LIKE '%price_input_per_musd%'
                  OR m.`sys_ai_model_metrics_manual_locked` LIKE '%price_output_per_musd%'
                  OR m.`sys_ai_model_metrics_manual_locked` LIKE '%price_cache_hit_per_musd%'
                  OR m.`sys_ai_model_metrics_manual_locked` LIKE '%price_cache_write_per_musd%'
                  OR m.`sys_ai_model_metrics_manual_locked` LIKE '%cost_per_task%')
        THEN m.`sys_ai_model_metrics_manual_locked`
        ELSE NULL
    END,
    m.`sys_ai_model_metrics_source`,
    1,
    m.`create_time`,
    m.`update_time`
FROM `sys_ai_model_metrics` m
WHERE (m.`sys_ai_model_metrics_price_input` IS NOT NULL
        OR m.`sys_ai_model_metrics_price_output` IS NOT NULL
        OR m.`sys_ai_model_metrics_price_cache_hit` IS NOT NULL
        OR m.`sys_ai_model_metrics_price_cache_write` IS NOT NULL
        OR m.`sys_ai_model_metrics_cost_per_task` IS NOT NULL)
  AND NOT EXISTS (
        SELECT 1 FROM `sys_ai_model_price` p
        WHERE p.`sys_ai_model_price_model_key` = m.`sys_ai_model_metrics_model_key`
  );
