-- 错题来源任务：支持「撤销完成」精确回退。
--
-- 为什么需要这一列：错题行按 (student, question) 唯一，同一道题可能被多张卷、
-- 多次「完成」登记。撤销某一次完成时，只按 (student, paper) 删会误伤历史行，
-- 所以把「是哪次完成登记的」落到行上，撤销时只删 source_task_id = 该任务 的行。
ALTER TABLE `edu_wrong_question`
    ADD COLUMN IF NOT EXISTS `wrong_question_source_task_id` VARCHAR(64) NULL COMMENT '来源阅卷任务标识（哪次完成登记的，用于撤销）' AFTER `wrong_question_paper_id`;

CREATE INDEX IF NOT EXISTS `idx_wq_source_task`
    ON `edu_wrong_question` (`wrong_question_source_task_id`);
