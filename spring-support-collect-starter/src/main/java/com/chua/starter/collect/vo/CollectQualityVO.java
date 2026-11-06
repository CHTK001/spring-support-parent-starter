package com.chua.starter.collect.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务质量报告。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "任务质量报告")
public class CollectQualityVO {

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "总节点执行次数")
    private int totalNodes;

    @Schema(description = "成功节点数")
    private int successNodes;

    @Schema(description = "失败节点数")
    private int failedNodes;

    @Schema(description = "跳过节点数")
    private int skippedNodes;

    @Schema(description = "HTTP 请求总数")
    private int requestCount;

    @Schema(description = "HTTP 成功次数")
    private int requestSuccess;

    @Schema(description = "HTTP 失败次数（网络层）")
    private int requestFailed;

    @Schema(description = "采集记录总数（入库）")
    private int recordCount;

    @Schema(description = "新增记录数（本次任务写入的新行）")
    private int newRecords;

    @Schema(description = "重复记录数（唯一键冲突被跳过）")
    private int duplicateRecords;

    @Schema(description = "数据入库率（入库数/请求成功数），0-100")
    private BigDecimal insertRate;

    @Schema(description = "节点成功率（成功节点/总节点），0-100")
    private BigDecimal nodeSuccessRate;

    @Schema(description = "总耗时 ms")
    private Long totalCostMs;

    @Schema(description = "最慢节点名称")
    private String slowestNode;

    @Schema(description = "最慢节点耗时 ms")
    private Long slowestNodeCostMs;

    @Schema(description = "质量评分 0-100")
    private int qualityScore;

    @Schema(description = "质量等级：EXCELLENT / GOOD / FAIR / POOR")
    private String qualityLevel;

    @Schema(description = "错误摘要")
    private String errorSummary;

    @Schema(description = "质量详情列表")
    private java.util.List<QualityNode> nodes;

    @Data
    @Schema(description = "节点质量")
    public static class QualityNode {
        @Schema(description = "节点ID")
        private String nodeId;

        @Schema(description = "节点名称")
        private String nodeName;

        @Schema(description = "节点形状")
        private String nodeShape;

        @Schema(description = "执行次数")
        private int execCount;

        @Schema(description = "成功次数")
        private int successCount;

        @Schema(description = "失败次数")
        private int failedCount;

        @Schema(description = "跳过次数")
        private int skippedCount;

        @Schema(description = "平均耗时 ms")
        private Long avgCostMs;

        @Schema(description = "总耗时 ms")
        private Long totalCostMs;

        @Schema(description = "产出记录数")
        private int recordCount;

        @Schema(description = "成功率，0-100")
        private BigDecimal successRate;
    }
}
