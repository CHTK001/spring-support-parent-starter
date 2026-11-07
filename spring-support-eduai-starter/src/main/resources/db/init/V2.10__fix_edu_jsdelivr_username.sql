-- =====================================================================
-- V2.10__fix_edu_jsdelivr_at_sign.sql - 修正 jsdelivr 镜像路径里的 `xinhuaat master`
--
-- 背景：V2.1 种子数据里 jsdelivr 镜像是 `gh/pwxcoo/chinese-xinhua/master`，
-- 其中 `at` 是 `at` 的转义写法。H2 的 MySQL 兼容模式会把它当参数标记处理，
-- 走 @  替换后在路径里留下 `xinhuaat master`，链接直接 404。
-- （用 raw.githubusercontent.com 的流程不受影响；只有走 jsdelivr 的这两条会中招。）
--
-- 幂等：带 LIKE 命中条件，重复执行结果一致。
-- =====================================================================

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'xinhuaat master', 'xinhua/master'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%xinhuaat master%';

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'at master', '/master'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%at master%';
