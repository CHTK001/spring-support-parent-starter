-- =====================================================================
-- V1.0__initdata_all.sql - datasource-starter 全量种子（合并自 initdata_* + init 内嵌种子）
-- 通道: INITDATA 虚拟线程异步；全部幂等（INSERT IGNORE / WHERE NOT EXISTS）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 来源: V1.0.0__initdata_datasync_mapping.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- DataSync 数据库模式：映射示例数据（异步执行，不阻塞启动）
-- 由 V1.0.0__init_datasync_mapping.sql 建表后，本脚本插入演示用映射配置
-- 幂等：INSERT IGNORE（主键/唯一键冲突时跳过），重复执行安全
-- =====================================================================

INSERT IGNORE INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('db-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);











INSERT IGNORE INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('f1', 'db-user-sync', 'name', 'user_name', 'toString', 0);












-- ---------------------------------------------------------------------
-- 来源: V1.0__initdata_monitor.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- 监控模块初始化数据（异步执行，不阻塞启动）
-- 由 V1.0__init_monitor.sql 建表后，本脚本补充配置与告警规则种子数据
-- 幂等：INSERT ... SELECT WHERE NOT EXISTS 模式，重复执行安全
-- 兼容 MySQL 8 / H2(MySQL模式) / PostgreSQL（INSERT SELECT 为三库通用语法）
-- =====================================================================

-- ============================================================
-- 初始配置
-- ============================================================
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.data.retention.days', '30', '历史数据保留天数'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.data.retention.days');











INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.aggregate.interval', '1h', '聚合粒度'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.aggregate.interval');











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











INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.cleanup.cron', '0 0 3 * * ?', '数据清理任务 cron'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.cleanup.cron');











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












-- ---------------------------------------------------------------------
-- 来源: V1.0__initdata_server.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- 服务器模块初始化数据（异步执行，不阻塞启动）
-- 由 V1.0__init_server.sql 建表后，本脚本补充系统配置种子数据
-- 兼容 MySQL 8 / H2(MySQL模式)：H2 不支持 INSERT IGNORE 时由 continue-on-error 跳过，
--       新库建表后首次启动即插入，重复执行靠 uk_server_config_key 唯一键保护
-- =====================================================================

INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.host', '"124.221.230.112"', 'guacd 网关地址（远控隧道）');











INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.port', '"4822"', 'guacd 网关端口（远控隧道）');











INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.client.host', '""', 'guacd-client 网关地址（浏览器侧 WS 隧道 host:port，空串=同域）');











INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.sweep.interval', '"60"', '连接缓存健康检查间隔(秒)');











INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.idle.timeout', '"300"', '连接空闲超时(秒)');












-- ---------------------------------------------------------------------
-- 来源: V1.1__initdata_device.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- 设备管理模块初始化数据（异步执行，不阻塞启动）
-- 由 V1.1__add_channel_and_location.sql 建 device_task_state 表后，
-- 本脚本补充后台定时任务状态种子数据
-- 幂等：device_task_state_name 有索引保护，重复执行靠业务层去重
-- =====================================================================

-- 初始化任务状态
INSERT INTO device_task_state (device_task_state_id, device_task_state_name, device_task_state_running, device_task_state_interval, device_task_state_description)
VALUES
    (REPLACE(UUID(),'-',''), 'deviceOfflineCheck', 0, 30, '设备在离线检测任务'),
    (REPLACE(UUID(),'-',''), 'deviceEventPoll', 0, 30, '设备事件轮询任务');












-- ---------------------------------------------------------------------
-- 来源: V1.1__initdata_dimensions.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- 教育 AI 维度表初始化数据（异步执行，不阻塞启动）
-- 由 V1.1__init_dimensions.sql 建表后，本脚本补充学科/年级种子数据
-- 幂等：INSERT IGNORE（MySQL/H2 唯一键保护），重复执行安全
-- =====================================================================

-- ----------------------------
-- 学科种子
-- ----------------------------
INSERT IGNORE INTO edu_subject (subject_code, subject_name, subject_grade_levels, subject_sort_order, subject_status) VALUES
    ('chinese', '语文', 'PRIMARY,JUNIOR,SENIOR', 1, 1),
    ('math',    '数学', 'PRIMARY,JUNIOR,SENIOR,UNIVERSITY', 2, 1),
    ('english', '英语', 'PRIMARY,JUNIOR,SENIOR,UNIVERSITY', 3, 1),
    ('physics', '物理', 'JUNIOR,SENIOR,UNIVERSITY', 4, 1),
    ('chemistry', '化学', 'JUNIOR,SENIOR,UNIVERSITY', 5, 1),
    ('biology', '生物', 'JUNIOR,SENIOR', 6, 1),
    ('history', '历史', 'JUNIOR,SENIOR', 7, 1),
    ('geography', '地理', 'JUNIOR,SENIOR', 8, 1),
    ('politics', '政治', 'JUNIOR,SENIOR,UNIVERSITY', 9, 1);











-- ----------------------------
-- 年级种子
-- ----------------------------
INSERT IGNORE INTO edu_grade (grade_level, grade_name, grade_order) VALUES
    ('PRIMARY', '1', 1),('PRIMARY', '2', 2),('PRIMARY', '3', 3),
    ('PRIMARY', '4', 4),('PRIMARY', '5', 5),('PRIMARY', '6', 6),
    ('JUNIOR', '初一', 1),('JUNIOR', '初二', 2),('JUNIOR', '初三', 3),
    ('SENIOR', '高一', 1),('SENIOR', '高二', 2),('SENIOR', '高三', 3),
    ('UNIVERSITY', '大一', 1),('UNIVERSITY', '大二', 2),('UNIVERSITY', '大三', 3),('UNIVERSITY', '大四', 4);












-- ---------------------------------------------------------------------
-- 来源: V1.2__initdata_fun_module.sql
-- ---------------------------------------------------------------------
-- =====================================================================
-- 趣味模块配置初始化数据（异步执行，不阻塞启动）
-- 由 V1.2__add_fun_module.sql 建表后，本脚本补充模块配置与成语种子数据
-- 幂等：INSERT IGNORE（edu_fun_config.module_code 唯一键保护），重复执行安全
-- =====================================================================

-- ----------------------------
-- 初始数据：趣味模块配置
-- ----------------------------
INSERT IGNORE INTO edu_fun_config (module_code, module_name, module_desc, module_icon, module_path, enabled, sort_order, config_json) VALUES
('INSECT', '昆虫识图', '交互式3D昆虫图鉴，认识63种昆虫的身体构造', 'Bug', '/edu/fun/insect', 1, 1, '{"url":"https://insect-world.pages.dev","width":"100%","height":"800px"}'),
('IDIOM_GAME', '成语消消乐', '根据学段生成的成语配对游戏', 'Trophy', '/edu/fun/idiom', 1, 2, '{"defaultCount":10,"showPinyin":true}'),
('IDIOM_BANK', '成语库', '管理成语数据和学段映射', 'Collection', '/edu/fun/idiom-bank', 1, 3, '{}');











-- ----------------------------
-- 初始数据：成语（小学阶段）
-- 幂等说明：edu_idiom 无自增主键显式列（表 DDL 中 idiom_id 为 INT PK），
-- 按内容去重插入（INSERT IGNORE 依赖唯一键；edu_idiom 无内容唯一键时由 initdata 异步执行器
-- 的 ONCE 扫描模式 + 版本记录保证只执行一次，重跑不会重复插入）
-- ----------------------------
INSERT IGNORE INTO edu_idiom (idiom_content, idiom_pinyin, idiom_meaning, idiom_source, idiom_example, idiom_difficulty, idiom_tags) VALUES
('守株待兔', 'shou zhu dai tu', '比喻死守狭隘经验，不知变通', '韩非子·五蠹', '我们不能守株待兔，要主动出击。', 1, '寓言,动物'),
('画蛇添足', 'hua she tian zu', '比喻做多余的事，反而弄巧成拙', '战国策·齐策二', '这样做简直是画蛇添足。', 1, '寓言,动物'),
('亡羊补牢', 'wang yang bu lao', '比喻出了差错后及时补救', '战国策·楚策四', '亡羊补牢，为时未晚。', 1, '寓言,动物'),
('井底之蛙', 'jing di zhi wa', '比喻见识狭隘的人', '庄子·秋水', '不要做井底之蛙，要开阔眼界。', 1, '寓言,动物'),
('狐假虎威', 'hu jia hu wei', '比喻借别人的威势吓唬人', '战国策·楚策一', '他不过是狐假虎威罢了。', 1, '寓言,动物'),
('对牛弹琴', 'dui niu tan qin', '比喻对不懂道理的人讲道理', '理惑论', '跟他说这些简直是对牛弹琴。', 2, '寓言,动物'),
('鹤立鸡群', 'he li ji qun', '比喻一个人的才能或仪表出众', '世说新语·容止', '他在人群中鹤立鸡群。', 2, '动物,外貌'),
('杯弓蛇影', 'bei gong she ying', '比喻疑神疑鬼，自相惊扰', '风俗通义·世间', '不要杯弓蛇影，自己吓自己。', 2, '寓言,动物'),
('马到成功', 'ma dao cheng gong', '比喻事情一开始就取得成功', '元曲选·郑德辉', '祝你马到成功！', 2, '动物,成功'),
('龙飞凤舞', 'long fei feng wu', '形容书法笔势有力，也形容山势蜿蜒', '宋·苏轼', '他的字龙飞凤舞，很有气势。', 2, '动物,艺术'),
('虎头蛇尾', 'hu tou she wei', '比喻做事有始无终', '元·康进之', '做事不能虎头蛇尾。', 3, '动物,做事'),
('鸡飞蛋打', 'ji fei dan da', '比喻一无所得', '民间谚语', '结果鸡飞蛋打，什么都没捞到。', 3, '动物,失败'),
('猴年马月', 'hou nian ma yue', '比喻遥遥无期', '民间谚语', '这事要等到猴年马月啊。', 3, '动物,时间'),
('狗急跳墙', 'gou ji tiao qiang', '比喻坏人在走投无路时豁出去', '敦煌变文集·燕子赋', '小心狗急跳墙。', 3, '动物,处境'),
('螳臂当车', 'tang bi dang che', '比喻不自量力', '庄子·人间世', '他的反对不过是螳臂当车。', 4, '动物,寓言'),
('鱼目混珠', 'yu mu hun zhu', '比喻以假乱真', '玉清经', '市场上鱼目混珠的现象很多。', 4, '动物,真假'),
('鹏程万里', 'peng cheng wan li', '比喻前程远大', '庄子·逍遥游', '祝你鹏程万里！', 4, '动物,前途'),
('塞翁失马', 'sai weng shi ma', '比喻坏事在一定条件下可以变为好事', '淮南子·人间训', '塞翁失马，焉知非福。', 5, '寓言,哲理'),
('叶公好龙', 'ye gong hao long', '比喻口头上说爱好，实际上并不真爱好', '新序·杂事', '他不过是叶公好龙罢了。', 5, '寓言,哲理'),
('掩耳盗铃', 'yan er dao ling', '比喻自己欺骗自己', '吕氏春秋·自知', '这样做简直是掩耳盗铃。', 5, '寓言,哲理');











-- ----------------------------
-- 初始数据：成语学段映射（与 edu_idiom 同批插入，幂等同上）
-- ----------------------------
INSERT IGNORE INTO edu_idiom_grade (idiom_id, grade_level, grade, semester, textbook_version) VALUES
(1,'小学','一年级','上','人教版'),(2,'小学','一年级','上','人教版'),(3,'小学','一年级','上','人教版'),
(4,'小学','二年级','上','人教版'),(5,'小学','二年级','上','人教版'),(6,'小学','二年级','下','人教版'),
(7,'小学','三年级','上','人教版'),(8,'小学','三年级','下','人教版'),(9,'小学','四年级','上','人教版'),
(10,'小学','四年级','下','人教版'),(11,'小学','五年级','上','人教版'),(12,'小学','五年级','下','人教版'),
(13,'小学','六年级','上','人教版'),(14,'小学','六年级','下','人教版'),
(15,'初中','初一','上','人教版'),(16,'初中','初一','下','人教版'),(17,'初中','初二','上','人教版'),
(18,'初中','初二','下','人教版'),(19,'初中','初三','上','人教版'),(20,'初中','初三','下','人教版');












-- ---------------------------------------------------------------------
-- 来源: V1.0__init_monitor.sql / V1.0__init_server.sql 内嵌种子（幂等）
-- ---------------------------------------------------------------------
V1.0.0__init_datasync_mapping.sql
INSERT INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('db-user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);











V1.0.0__init_datasync_mapping.sql
INSERT INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('f1', 'db-user-sync', 'name', 'user_name', 'toString', 0);











V1.0__init_monitor.sql
-- ============================================================
-- 初始配置
-- ============================================================
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.data.retention.days', '30', '历史数据保留天数'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.data.retention.days');











V1.0__init_monitor.sql
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.aggregate.interval', '1h', '聚合粒度'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.aggregate.interval');











V1.0__init_monitor.sql
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.collect.interval.ms', '10000', '采集周期(毫秒)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.collect.interval.ms');











V1.0__init_monitor.sql
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.local.skip.macos', 'true', '本地采集在 macOS 下跳过(oshi 动态库不支持)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.local.skip.macos');











V1.0__init_monitor.sql
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.prometheus.enabled', 'false', 'Prometheus 采集总开关'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.prometheus.enabled');











V1.0__init_monitor.sql
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.prometheus.url', '', 'Prometheus 服务地址'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.prometheus.url');











V1.0__init_monitor.sql
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.cleanup.cron', '0 0 3 * * ?', '数据清理任务 cron'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.cleanup.cron');











V1.0__init_monitor.sql
-- ============================================================
-- 项目守护/指标告警策略与规则种子（由 spring-support-project-starter 喂指标）
-- ============================================================
INSERT INTO ms_monitor_alarm_policy (`ms_monitor_alarm_policy_name`, `ms_monitor_alarm_policy_enabled`, `ms_monitor_alarm_policy_remark`)
SELECT '项目守护与指标', 1, '项目守护失败自动拉起与指标采集相关告警'
WHERE NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_policy WHERE `ms_monitor_alarm_policy_name` = '项目守护与指标');











V1.0__init_monitor.sql
INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.watchdog.fail', 'GT', 1, 1, 10, 'P2', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.watchdog.fail');











V1.0__init_monitor.sql
INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.watchdog.restart.fail', 'GT', 1, 1, 10, 'P1', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.watchdog.restart.fail');











V1.0__init_monitor.sql
INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.port.alive', 'LE', 0, 2, 10, 'P1', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.port.alive');











V1.0__init_monitor.sql
INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.collect.fail', 'GT', 0, 3, 30, 'P3', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.collect.fail');











V1.0__init_server.sql
-- ---------------------------------------------------------------------
-- 系统配置初始化数据（原 V1.0__initdata_server.sql，INSERT IGNORE 幂等）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.host', '"124.221.230.112"', 'guacd 网关地址（远控隧道）');











V1.0__init_server.sql
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.port', '"4822"', 'guacd 网关端口（远控隧道）');











V1.0__init_server.sql
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('guacd.client.host', '""', 'guacd-client 网关地址（浏览器侧 WS 隧道 host:port，空串=同域）');











V1.0__init_server.sql
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.sweep.interval', '"60"', '连接缓存健康检查间隔(秒)');











V1.0__init_server.sql
INSERT IGNORE INTO ms_server_config (ms_server_config_key, ms_server_config_value, ms_server_config_desc)
VALUES ('connection.idle.timeout', '"300"', '连接空闲超时(秒)');





















