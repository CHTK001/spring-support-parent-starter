package com.chua.starter.eduai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 阅卷任务成绩分布统计。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Builder
@Schema(description = "成绩分布统计")
public class MarkingStatsVO {

    @Schema(description = "任务业务标识")
    private String taskId;

    @Schema(description = "总人数")
    private int totalCount;

    @Schema(description = "已完成判分人数")
    private int scoredCount;

    @Schema(description = "平均分")
    private double avgScore;

    @Schema(description = "最高分")
    private Integer maxScore;

    @Schema(description = "最低分")
    private Integer minScore;

    @Schema(description = "分段人数分布，区间左闭右开，示例：[{\"range\":\"0-60\",\"count\":5},...]")
    private List<ScoreRange> distribution;

    @Schema(description = "分段人数")
    @Data
    @Builder
    public static class ScoreRange {
        @Schema(description = "分数字段，如 '0-60'")
        private String range;
        @Schema(description = "该分段人数")
        private int count;
    }
}
