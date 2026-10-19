package com.chua.starter.eduai.model.vo;

/**
 * 阅卷「完成」结果 VO。
 *
 * <p>老师点「完成」后返回本次收口的结果：错题是否已归入对应学生、
 * 归到哪个学生 / 哪张试卷、本次新增登记了多少条错题、有多少条登记失败。</p>
 *
 * @param taskId          任务标识
 * @param studentId       错题归属的学生标识（答卷上识别 / 人工纠正后的学号）
 * @param paperId         错题归属的试卷标识（可空：任意卷没有对应试卷）
 * @param wrongCount      本次按最终分判定为「错」并登记成功的题目数
 * @param failedCount     本次判定为错但登记失败的条数（如实回报，不静默吞掉）
 * @param alreadyFinished 是否此前已经完成过（重复点击时为 true，且不会重复累加错题次数）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingFinishResultVO(
        String taskId,
        String studentId,
        Long paperId,
        Integer wrongCount,
        Integer failedCount,
        Boolean alreadyFinished) {
}
