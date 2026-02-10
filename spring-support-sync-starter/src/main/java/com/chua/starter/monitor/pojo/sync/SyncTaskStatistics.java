package com.chua.starter.sync.pojo.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 同步任务执行统计数据
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Schema(description = "同步任务执行统计数据")
public class SyncTaskStatistics {

    /**
     * 汇总信息
     */
    @Schema(description = "汇总信息")
    private Summary summary;

    /**
     * 趋势数据(按天/小时)
     */
    @Schema(description = "趋势数据")
    private TrendData trend;

    /**
     * 状态分布
     */
    @Schema(description = "状态分布")
    private List<StatusDistribution> statusDistribution;

    /**
     * 触发类型分布
     */
    @Schema(description = "触发类型分布")
    private List<TriggerTypeDistribution> triggerTypeDistribution;

    /**
     * 任务排行
     */
    @Schema(description = "任务排行")
    private List<TaskRanking> taskRanking;

    /**
     * 汇总信息
     */
    @Data
    @Schema(description = "汇总信息")
    public static class Summary {
        @Schema(description = "总执行次数")
        private Long totalExecutions;

        @Schema(description = "成功次数")
        private Long successCount;

        @Schema(description = "失败次数")
        private Long failCount;

        @Schema(description = "运行中次数")
        private Long runningCount;

        @Schema(description = "成功率")
        private Double successRate;

        @Schema(description = "平均耗时(毫秒)")
        private Double avgCost;

        @Schema(description = "总读取数量")
        private Long totalReadCount;

        @Schema(description = "总写入数量")
        private Long totalWriteCount;

        @Schema(description = "平均吞吐量")
        private Double avgThroughput;
    }

    /**
     * 趋势数据
     */
    @Data
    @Schema(description = "趋势数据")
    public static class TrendData {
        @Schema(description = "时间轴标签")
        private List<String> labels;

        @Schema(description = "执行次数")
        private List<Long> executions;

        @Schema(description = "成功次数")
        private List<Long> successCounts;

        @Schema(description = "失败次数")
        private List<Long> failCounts;

        @Schema(description = "平均耗时")
        private List<Double> avgCosts;

        @Schema(description = "数据量")
        private List<Long> dataCounts;
    }

    /**
     * 状态分布
     */
    @Data
    @Schema(description = "状态分布")
    public static class StatusDistribution {
        @Schema(description = "状态")
        private String status;

        @Schema(description = "状态名称")
        private String statusName;

        @Schema(description = "数量")
        private Long count;

        @Schema(description = "占比")
        private Double percentage;
    }

    /**
     * 触发类型分布
     */
    @Data
    @Schema(description = "触发类型分布")
    public static class TriggerTypeDistribution {
        @Schema(description = "触发类型")
        private String triggerType;

        @Schema(description = "触发类型名称")
        private String triggerTypeName;

        @Schema(description = "数量")
        private Long count;

        @Schema(description = "占比")
        private Double percentage;
    }

    /**
     * 任务排行
     */
    @Data
    @Schema(description = "任务排行")
    public static class TaskRanking {
        @Schema(description = "任务ID")
        private Long taskId;

        @Schema(description = "任务名称")
        private String taskName;

        @Schema(description = "执行次数")
        private Long executions;

        @Schema(description = "成功率")
        private Double successRate;

        @Schema(description = "平均耗时")
        private Double avgCost;

        @Schema(description = "总数据量")
        private Long totalDataCount;
    }
}
