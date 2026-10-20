package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 错题来源任务溯源 VO。
 *
 * <p>错题按 (学生, 题目) 唯一聚合，同一题可能被多张卷、多次「完成」登记。
 * 列表页只展示主来源（首次登记的 source_task_id），详情溯源需要返回该题
 * 关联到的所有来源任务及完成时间。</p>
 *
 * @param sourceTaskId        来源阅卷任务标识
 * @param sourceTaskTime      该任务的老师完成时间（溯源展示用，可空）
 * @param paperId             来源任务关联的试卷（可空）
 * @param paperName           试卷名（可空，未联查到时为 null）
 * @param studentId           来源任务归属学生（可空）
 * @since 4.0.0.42
 * @author CH
 */
public record WrongSourceVO(
        String sourceTaskId,
        LocalDateTime sourceTaskTime,
        Long paperId,
        String paperName,
        String studentId) {
}
