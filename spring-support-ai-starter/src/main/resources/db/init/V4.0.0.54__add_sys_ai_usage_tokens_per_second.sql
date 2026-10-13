-- sys_ai_usage 增加 token/s 性能指标列
--
-- 解码速度：输出 Token ÷ 解码窗口（总耗时 - 首字耗时），排除首字延迟与网络往返，
-- 反映模型真实生成速率；端到端吞吐：总 Token ÷ 总耗时（含首字延迟），反映调用方体感。
-- 存量数据两列为 NULL，前端展示为「—」，新写入的记录自动携带。
ALTER TABLE `sys_ai_usage`
    ADD COLUMN `sys_ai_usage_tokens_per_second` DOUBLE NULL COMMENT '解码速度(Token/秒)' AFTER `sys_ai_usage_total_latency`,
    ADD COLUMN `sys_ai_usage_total_tokens_per_second` DOUBLE NULL COMMENT '端到端吞吐(Token/秒)' AFTER `sys_ai_usage_tokens_per_second`;
