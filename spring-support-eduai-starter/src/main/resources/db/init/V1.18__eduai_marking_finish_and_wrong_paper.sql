-- V1.18 阅卷「完成」与错题记录试卷归因
--
-- 背景：老师端阅卷要求「阅卷完成 → 老师点完成 → 错题自动归到对应学生的错题库」，
--       并且错题要能回答「来自哪张试卷」。
--
-- 三处增量：
--   1) edu_wrong_question 增加 wrong_question_paper_id —— 错题来自哪张试卷。
--      原先只存 exam_id（考试），而「试卷」才是老师与家长认知里的卷子单位；
--      一份试卷可能有多场考试，缺了试卷号就答不出「哪张卷的错题」。
--   2) edu_marking_task 增加 marking_task_teacher_finished —— 老师是否已点「完成」。
--      「完成」是幂等动作：重复点击不得把同一批错题的错误次数反复累加，
--      故用该标记做一次性判定（与 marking_task_status 的「处理完成」语义区分开）。
--   3) edu_marking_task 增加 marking_task_teacher_finish_time —— 老师点「完成」的时间（审计）。
--
-- 注意：marking_task_finish_time 早于本版存在，语义是「后台处理完成时间」，
--       与本次新增的「老师确认完成」不是一回事，故新增独立列，不复用。

ALTER TABLE edu_wrong_question
    ADD COLUMN IF NOT EXISTS wrong_question_paper_id BIGINT COMMENT '试卷ID（错题来自哪张卷）';

ALTER TABLE edu_marking_task
    ADD COLUMN IF NOT EXISTS marking_task_teacher_finished TINYINT DEFAULT 0 COMMENT '老师是否已点「阅卷完成」: 0-未完成 1-已完成';

ALTER TABLE edu_marking_task
    ADD COLUMN IF NOT EXISTS marking_task_teacher_finish_time DATETIME COMMENT '老师点「阅卷完成」的时间';
