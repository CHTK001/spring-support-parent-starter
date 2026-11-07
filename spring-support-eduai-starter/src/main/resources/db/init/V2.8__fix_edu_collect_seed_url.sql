-- =====================================================================
-- V2.8__fix_edu_collect_seed_url.sql - 修正种子 URL 被写成「...<文件名>.CLOB」
--
-- 背景（实测踩到的第二个坑，比 V2.7 更底层）：
--   早期往 collect_flow 里写 DSL 时，种子 URL 的取值被读成了字符类型的
--   列类型名而不是值本身，落库后变成 `.../小学统编语文教材词语表.CLOB`。
--   于是即使 request 节点已改成正确的 `${data[0]}`，取到的第一条仍是坏 URL，
--   执行结果必然是 HTTP 404 / 502。
--
--   实测受影响范围：全部 14 条 edu_* 流程的 collect_flow_dsl 都带 `.CLOB`，
--   位置都在种子 URL 尾部（LOCATE 命中 247~547，恰是每条的 url 参数处）。
--
-- 做法：只把种子 URL 尾部的 `.CLOB` 伪后缀还原成 `.json`，
--   并限定在 collect_flow 表、edu_* 流程、且确实含 `.CLOB` 的行。
--   不碰其他字段，幂等（重复执行结果一致）。
-- =====================================================================

UPDATE `collect_flow`
SET `collect_flow_dsl` = REPLACE(`collect_flow_dsl`, '.CLOB', '.json'),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_dsl` LIKE '%.CLOB%';
