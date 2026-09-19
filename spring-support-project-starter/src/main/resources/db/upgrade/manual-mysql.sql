-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

ALTER TABLE `ms_project` ADD COLUMN `ms_project_watchdog_enabled` TINYINT DEFAULT 0 COMMENT '守护进程开关: 0关闭 1开启(持续检测+挂了自动拉起)';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_watchdog_interval` INT DEFAULT 30 COMMENT '守护检测间隔(秒,每项目独立设置,最小5秒)';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_watchdog_max_fail` INT DEFAULT 1 COMMENT '守护连续失败阈值: 连续N次不健康才自动重启(0=仅告警)';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_check_script` TEXT DEFAULT NULL COMMENT '自定义状态检测脚本(exit 0=健康,为空时回退健康检查类型探测)';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_preflight_gate` TINYINT DEFAULT 0 COMMENT '部署前置门禁: 部署前校验主机在线/延迟,不可达直接拦截';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_metric_enabled` TINYINT DEFAULT 0 COMMENT '指标采集开关: 0关闭 1开启(SSH轮询采集CPU/内存/磁盘)';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_metric_interval` INT DEFAULT 60 COMMENT '指标采集间隔秒(最小10秒)';
ALTER TABLE `ms_project` ADD COLUMN `ms_project_metric_host_ids` VARCHAR(512) COMMENT '指标采集目标主机ID集合(JSON数组,空=项目绑定主机)';
