package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 阅卷「完成」结果 VO。
 *
 * <p>老师点「完成」后返回本次收口的结果：错题是否已归入对应学生、
 * 归到哪个学生 / 哪张试卷、本次新增登记了多少条错题。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class MarkingFinishResultVO {

    /**
     * 任务标识
     */
    private String taskId;

    /**
     * 错题归属的学生标识（答卷上识别 / 人工纠正后的学号）
     */
    private String studentId;

    /**
     * 错题归属的试卷标识（可空：任意卷没有对应试卷）
     */
    private Long paperId;

    /**
     * 本次按最终分判定为「错」并登记的题目数
     */
    private Integer wrongCount;

    /**
     * 是否此前已经完成过（重复点击时为 true，且不会重复累加错题次数）
     */
    private Boolean alreadyFinished;
}
