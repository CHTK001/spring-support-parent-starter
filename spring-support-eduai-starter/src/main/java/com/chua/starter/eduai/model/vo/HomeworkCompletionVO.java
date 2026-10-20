package com.chua.starter.eduai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 作业完成率看板。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Builder
@Schema(description = "作业完成率看板")
public class HomeworkCompletionVO {

    @Schema(description = "试卷/考试标识")
    private Long paperId;

    @Schema(description = "试卷名称")
    private String paperName;

    @Schema(description = "应交人数（班级学生总数）")
    private int totalStudents;

    @Schema(description = "已交人数（提交了答题卡的人数）")
    private int submittedCount;

    @Schema(description = "已完成阅卷人数")
    private int markedCount;

    @Schema(description = "完成率（0.0 ~ 1.0）")
    private double completionRate;

    @Schema(description = "班级维度统计")
    private List<ClassStats> classStats;

    @Schema(description = "班级完成情况")
    @Data
    @Builder
    public static class ClassStats {
        @Schema(description = "班级ID")
        private Long classId;
        @Schema(description = "班级名称")
        private String className;
        @Schema(description = "班级学生数")
        private int totalStudents;
        @Schema(description = "已交人数")
        private int submittedCount;
        @Schema(description = "完成率")
        private double completionRate;
    }
}
