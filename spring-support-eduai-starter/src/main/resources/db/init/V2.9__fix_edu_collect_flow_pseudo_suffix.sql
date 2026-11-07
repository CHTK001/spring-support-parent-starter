-- =====================================================================
-- V2.9__fix_edu_collect_flow_pseudo_suffix.sql - 修正流程 DSL 里被方言转换器改坏的
--                                              种子 URL / 来源字段伪后缀
--
-- 事故根因（已在 utils-support 侧修复）：
--   DefaultScriptConverter 的类型映射用整条语句级正则
--   `(?i)\bJSON\b -> CLOB`、`(?i)\b(?:MEDIUMTEXT|LONGTEXT|TINYTEXT|TEXT)\b -> CLOB`，
--   没有跳过字符串字面量。于是 V1.x/V2.x 种子数据里所有 `.json` 都被改写成 `.CLOB`，
--   落库后表现为：
--     · 种子 URL 尾部变成 `.../data/ci.CLOB`、`.../poet.tang.0.CLOB`
--     · 来源字段变成 `chinese-xinhua/idiom.CLOB`
--     · 脚本注释里出现「word.CLOB radicals -> word.CLOB more」
--     · jsdelivr 镜像里的 `at master` 被当成参数标记处理，变成 `xinhuaat master/`
--   这些都是**数据污染**，迁移器本身已修，本文件只负责把库里的存量脏数据改回来。
--
-- 处理范围（只改 collect_flow_dsl，且只改 edu_* 流程）：
--   1. `.CLOB`    -> `.json`（仅当它紧跟在 URL 或来源字段之后；本库实测全部符合）
--   2. `at master/` -> `@master/`（jsdelivr 路径里的 at 是 @ 的转义残留）
--   3. `xinhuaat master` -> `xinhua@master`
--
-- 幂等：全部 UPDATE 都带 LIKE 命中条件，重复执行结果一致。
-- 注意：只动 edu_* 流程；其它业务流程的 DSL 不属于本轮范围。
-- =====================================================================

-- 1) 种子 URL / 来源字段里的 `.CLOB` 还原成 `.json`
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '.CLOB', '.json'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%.CLOB%';

-- 2) jsdelivr 镜像路径里的 `xinhuaat master` -> `xinhua@master`
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'xinhuaat master', 'xinhua@master'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%xinhuaat master%';

-- 3) 其余 `at master/` -> `@master/`
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'at master/', '@master/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%at master/%';
