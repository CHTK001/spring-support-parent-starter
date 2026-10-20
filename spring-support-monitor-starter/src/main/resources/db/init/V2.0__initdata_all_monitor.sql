-- =====================================================================
-- V2.0__initdata_all_monitor.sql - spring-support-monitor-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。此前分散的
--   V1.0__initdata_all_monitor.sql 已在本次收敛中并入本文件并删除。
-- 内容: 监控模块初始配置（由原 V1.0__init_all_monitor.sql 内嵌段归位）
-- 约束: 全部语句必须幂等，可反复执行。
-- =====================================================================
-- 监控模块初始配置（由原 V1.0__init_all_monitor.sql 内嵌段归位）

-- ============================================================
-- 初始配置
-- ============================================================
-- 保留期：真正被 MonitorScheduler#cleanupOldData 读取（configService.getInt）
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.data.retention.days', '1', '历史数据保留天数(滚动窗口，1=24小时)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.data.retention.days');

-- 【已移除】monitor.aggregate.interval
--   MonitorScheduler#aggregateHourly 的 cron 是写死的 "0 0 * * * ?"（@Scheduled 注解），
--   这个键从未被任何代码读取。留着会让人以为改它能调聚合粒度，实际改了没有任何效果。
--   要改聚合频率请改 MonitorScheduler#aggregateHourly 的 @Scheduled。
--
-- 【已移除】monitor.cleanup.cron
--   清理 cron 原本写死 "0 0 3 * * ?"，该键同样从未被读取。已把
--   MonitorScheduler#cleanupOldData 的注解改为 ${monitor.cleanup.cron:0 0 * * * ?}，
--   即改为读 Spring 属性（默认每小时）。
--   保留期 1 天时清理必须每小时跑：清理若每天一次，实际留存上界是
--   「保留期 + 最多 24 小时」= 48 小时，与「只保留 24 小时」的预期不符。

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.collect.interval.ms', '10000', '采集周期(毫秒)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.collect.interval.ms');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.local.skip.macos', 'true', '本地采集在 macOS 下跳过(oshi 动态库不支持)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.local.skip.macos');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.prometheus.enabled', 'false', 'Prometheus 采集总开关'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.prometheus.enabled');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.prometheus.url', '', 'Prometheus 服务地址'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.prometheus.url');

-- ============================================================
-- 项目守护/指标告警策略与规则种子（由 spring-support-project-starter 喂指标）
-- ============================================================
INSERT INTO ms_monitor_alarm_policy (`ms_monitor_alarm_policy_name`, `ms_monitor_alarm_policy_enabled`, `ms_monitor_alarm_policy_remark`)
SELECT '项目守护与指标', 1, '项目守护失败自动拉起与指标采集相关告警'
WHERE NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_policy WHERE `ms_monitor_alarm_policy_name` = '项目守护与指标');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.watchdog.fail', 'GT', 1, 1, 10, 'P2', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.watchdog.fail');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.watchdog.restart.fail', 'GT', 1, 1, 10, 'P1', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.watchdog.restart.fail');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.port.alive', 'LE', 0, 2, 10, 'P1', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.port.alive');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.collect.fail', 'GT', 0, 3, 30, 'P3', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.collect.fail');

