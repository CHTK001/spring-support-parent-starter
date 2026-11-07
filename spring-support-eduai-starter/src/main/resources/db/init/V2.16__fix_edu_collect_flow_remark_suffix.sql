-- =====================================================================
-- V2.16__fix_edu_collect_flow_remark_suffix.sql - 修正流程备注里的伪后缀
--
-- 背景：V2.9 修好了 DSL 里的 `.CLOB` 伪后缀，但同一批流程的
-- `collect_flow_remark`（备注文案）是照着旧的伪后缀写的，页面上仍显示
-- 「…edu_word_textbook.CLOB…」这类错误说明，容易误导使用者。
--
-- 修法：只替换备注文案里的 `.CLOB` 片段；DSL 不动。
-- 幂等：带命中条件，重复执行结果一致。
-- =====================================================================

UPDATE `collect_flow`
SET `collect_flow_remark` = REPLACE(`collect_flow_remark`, '.CLOB', ''),
    `update_time` = CURRENT_TIMESTAMP
WHERE `collect_flow_code` LIKE 'edu\_%' ESCAPE '\'
  AND `collect_flow_remark` LIKE '%.CLOB%';
