-- =====================================================================
-- V2.13__widen_edu_word_structure.sql - 放宽 edu_word.word_structure
--
-- 背景：`word_structure` 原设计存「结构/词性」，宽 32。歇后语流程
-- （edu_xiehouyu_xinhua）沿用它存谜面，实测 14032 条里最长 41 字，
-- 6 条超过 32，整批写入报 `Value too long for column "word_structure
-- CHARACTER VARYING(32)"`，任务成功抓取但一条都没入库。
--
-- 修法：放宽到大文本。MySQL 走 MODIFY COLUMN，H2 认同样的写法（MySQL 兼容模式）。
-- 幂等：只在仍是短文本时改，重复执行结果一致。
-- =====================================================================

ALTER TABLE `edu_word` MODIFY COLUMN `word_structure` LONGTEXT;
