-- =====================================================================
-- V5.9__add_channel_circuit.sql
-- 通道: ALTER 变更
-- 说明: 通道人工熔断开关（对标 Sub2API Circuit Breaker 的手动介入能力）。
--   探针状态机负责「按指标自动下线/恢复」（V5.7），本变更追加人工覆写字段：
--     AUTO   —— 跟随探针状态（默认）
--     OPEN   —— 人工熔断：路由层立即剔除该连接，不再向上游发请求
--     CLOSED —— 人工强制放行：即使探针判定 DOWN 也继续路由（排障用）
--   人工开关落库，重启后仍生效；自动熔断（请求级连续失败）在内存中按 provider::model 维度
--   维护冷却期，不占用数据库写入。
-- =====================================================================
ALTER TABLE `sys_ai_channel_health`
  ADD COLUMN `circuit_state` VARCHAR(16) NOT NULL DEFAULT 'AUTO'
      COMMENT '人工熔断状态（AUTO 跟随探针 / OPEN 强制熔断 / CLOSED 强制放行）' AFTER `state`,
  ADD COLUMN `circuit_reason` VARCHAR(512) DEFAULT NULL
      COMMENT '人工熔断/放行的原因说明' AFTER `circuit_state`,
  ADD COLUMN `circuit_updated_at` BIGINT DEFAULT NULL
      COMMENT '人工熔断状态变更时间（毫秒）' AFTER `circuit_reason`;

-- 存量行默认跟随探针状态
UPDATE `sys_ai_channel_health`
   SET `circuit_state` = 'AUTO'
 WHERE `circuit_state` IS NULL OR `circuit_state` = '';

CREATE INDEX `idx_circuit_state` ON `sys_ai_channel_health` (`circuit_state`);
