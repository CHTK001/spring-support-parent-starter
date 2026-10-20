package com.chua.starter.eduai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 教师端阅卷总览统计报表。
 *
 * <p>给老师端「阅卷」页提供一屏可读的教学数据：任务状态分布、老师完成状态、
 * 得分率分布、错题 TOP 榜、订正率。所有指标都限定在<b>当前账号本人</b>
 * 归属范围内的阅卷任务与错题。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Builder
@Schema(description = "教师阅卷总览统计")
public class MarkingDashboardStatsVO {

    @Schema(description = "阅卷任务总数")
    private int taskTotal;

    @Schema(description = "任务状态分布（排队/处理中/完成/失败）")
    private List<NamedCount> taskStatus;

    @Schema(description = "老师完成状态（待完成/已完成）")
    private List<NamedCount> teacherFinished;

    @Schema(description = "得分率分段分布（如 0-60 / 60-80 / 80-100）")
    private List<NamedCount> scoreRateDistribution;

    @Schema(description = "错题 TOP 榜（按题聚合错误次数，最多 10）")
    private List<WrongTopRow> wrongTop;

    @Schema(description = "错题总数")
    private int wrongTotal;

    @Schema(description = "已订正错题数")
    private int correctedCount;

    @Schema(description = "订正率（0.0 ~ 1.0）")
    private double correctedRate;

    /**
     * 带名称的计数项。
     */
    @Data
    @Builder
    @Schema(description = "命名计数项")
    public static class NamedCount {
        @Schema(description = "名称")
        private String name;
        @Schema(description = "数量")
        private long count;
    }

    /**
     * 错题 TOP 榜单行。
     */
    @Data
    @Builder
    @Schema(description = "错题 TOP 榜行")
    public static class WrongTopRow {
        @Schema(description = "题目标识")
        private Long questionId;
        @Schema(description = "题干摘要")
        private String stem;
        @Schema(description = "学科")
        private String subject;
        @Schema(description = "错误次数合计")
        private int wrongCount;
    }
}
