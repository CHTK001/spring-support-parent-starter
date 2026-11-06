package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 执行历史统计。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "执行历史统计")
public class CollectStatsVO {

    @Schema(description = "流程ID")
    private Integer flowId;

    @Schema(description = "流程名称")
    private String flowName;

    @Schema(description = "统计天数")
    private int days;

    @Schema(description = "总执行次数")
    private int totalRuns;

    @Schema(description = "成功次数")
    private int successCount;

    @Schema(description = "失败次数")
    private int failureCount;

    @Schema(description = "跳过次数")
    private int skippedCount;

    @Schema(description = "成功率，0-100")
    private BigDecimal successRate;

    @Schema(description = "累计采集记录数")
    private long totalRecords;

    @Schema(description = "平均每次采集记录数")
    private BigDecimal avgRecordsPerRun;

    @Schema(description = "总耗时 ms")
    private Long totalCostMs;

    @Schema(description = "平均耗时 ms")
    private BigDecimal avgCostMs;

    @Schema(description = "最长耗时 ms")
    private Long maxCostMs;

    @Schema(description = "最短耗时 ms")
    private Long minCostMs;

    @Schema(description = "每日趋势数据")
    private List<DailyTrend> daily;

    @Schema(description = "节点耗时 TopN")
    private List<NodeCostStat> nodeCosts;

    @Data
    @Schema(description = "每日趋势")
    public static class DailyTrend {
        /** 日期，yyyy-MM-dd */
        private String date;
        private int runCount;
        private int successCount;
        private int failureCount;
        private long recordCount;
        private Long avgCostMs;
    }

    @Data
    @Schema(description = "节点耗时统计")
    public static class NodeCostStat {
        private String nodeId;
        private String nodeName;
        private String nodeShape;
        /** 该节点在所有任务中被执行的总次数 */
        private int execCount;
        /** 平均耗时 ms */
        private BigDecimal avgCostMs;
        /** 总耗时 ms */
        private Long totalCostMs;
        /** 成功率，0-100 */
        private BigDecimal successRate;
    }
}
