-- 错题订正状态：支持「订正跟踪」业务。
--
-- 为什么需要这一列：错题行按 (student, question) 唯一，掌握度（mastery_level）
-- 是学生复习后的主观自评；「订正」是老师在讲解后要求学生在错题卡上重新作答
-- 的客观动作。二者语义不同，不能混用：订正状态用 0=未订正 / 1=已订正 记录，
-- 供学生端一键标记、老师端统计订正率（已订正 / 全部错题）。
ALTER TABLE `edu_wrong_question`
    ADD COLUMN IF NOT EXISTS `wrong_question_corrected` TINYINT NOT NULL DEFAULT 0 COMMENT '订正状态：0 未订正 / 1 已订正' AFTER `wrong_question_source_task_id`;

-- 订正时间（老师/学生点「订正」时落时间，统计最近订正活跃度用）
ALTER TABLE `edu_wrong_question`
    ADD COLUMN IF NOT EXISTS `wrong_question_correct_time` DATETIME NULL COMMENT '最近订正时间' AFTER `wrong_question_corrected`;
