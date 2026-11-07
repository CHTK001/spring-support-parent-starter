-- =====================================================================
-- V2.15__fix_edu_collect_seeds_via_proxy.sql - 采集种子统一改走可用代理
--
-- 背景（2026-11-07 实测）：
--   1. raw.githubusercontent.com 全站 502 / 长时间挂起，含此前成功过的
--      chinese-poetry 路径，直接拿不到语料；
--   2. jsdelivr 对单文件超过 20MB 直接 403，body 明写
--      "File size exceeded the configured limit of 20 MB"，chinese-xinhua
--      的 word.json / ci.json 都超限；
--   3. ghfast.top 代理 raw 全部可用，14.9MB 的文件约 28 秒拿完。
--
-- 另外，早期迁移把 jsdelivr 路径里的 at 分隔符吞成了「仓库名at 分支」的粘连形态
-- （git 仓库名后面直接跟着 at），这里一并还原成正常的仓库/分支分隔。
--
-- 幂等：带命中条件，重复执行结果一致。
-- =====================================================================

-- 1) 还原被吞损的 at 分隔符（仓库名 + at → 仓库名 /）
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'pwxcoo/chinese-xinhuaat ', 'pwxcoo/chinese-xinhua/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%pwxcoo/chinese-xinhuaat %';

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'chinese-poetry/chinese-poetry-zhCNat ', 'chinese-poetry/chinese-poetry-zhCN/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%chinese-poetry/chinese-poetry-zhCNat %';

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'chinese-poetry/chinese-poetryat ', 'chinese-poetry/chinese-poetry/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%chinese-poetry/chinese-poetryat %';

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '7jul/yuwen_wordsat ', '7jul/yuwen_words/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%7jul/yuwen_wordsat %';

-- 2) CDN 形态统一成 raw 形态（后续再加代理前缀，避免叠两层）
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'https://fastly.jsdelivr.net/gh/', 'https://raw.githubusercontent.com/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%fastly.jsdelivr.net/gh/%';

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'https://cdn.jsdelivr.net/gh/', 'https://raw.githubusercontent.com/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%cdn.jsdelivr.net/gh/%';

-- 3) raw 统一加 ghfast.top 代理前缀（先拆再加，避免重复叠前缀）
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'https://ghfast.top/https://raw.githubusercontent.com/', 'https://raw.githubusercontent.com/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%ghfast.top/https://raw.githubusercontent.com/%';

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, 'https://raw.githubusercontent.com/', 'https://ghfast.top/https://raw.githubusercontent.com/'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%https://raw.githubusercontent.com/%';

-- 4) 大文件（词典/成语题库）脚本节点超时抬到 900 秒：十万条记录的过滤远超 30 秒默认值
UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '"name":"timeout","value":"300000"', '"name":"timeout","value":"900000"'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%"name":"timeout","value":"300000"%';
