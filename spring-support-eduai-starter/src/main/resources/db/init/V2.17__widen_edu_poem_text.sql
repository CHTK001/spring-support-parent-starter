-- =====================================================================
-- V2.17__widen_edu_poem_text.sql - 放宽 edu_poem 的译文/注释/赏析列
--
-- 背景：三列原为 TEXT（65535 字节）。古文岛详情页里长文的「译文及注释」
-- 整块可以远超这个上限（2026-11-09 实测：poem_id 213020 的译文纯文本
-- 116011 字节），补全任务在这一行报
-- `Data truncation: Data too long for column 'poem_translation'`，
-- 同一批 50 行里其余 49 行都写进去了，只有这一行被整条回滚。
--
-- 修法：放宽到 MEDIUMTEXT（16MB）。用 MODIFY COLUMN 改写类型，
-- 重复执行结果一致；正文 poem_content 保持 TEXT 不动（整首诗不会到这个量级）。
-- =====================================================================

ALTER TABLE `edu_poem`
    MODIFY COLUMN `poem_translation` MEDIUMTEXT COMMENT '白话译文',
    MODIFY COLUMN `poem_appreciation` MEDIUMTEXT COMMENT '赏析',
    MODIFY COLUMN `poem_notes` MEDIUMTEXT COMMENT '注释';
