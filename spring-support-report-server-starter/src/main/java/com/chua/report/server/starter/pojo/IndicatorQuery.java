package com.chua.report.server.starter.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 指标
 * @author CH
 * @since 2024/7/5
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class IndicatorQuery extends IdQuery {
    /**
     * TIME, SIMPLE
     */
    @Schema(description = "TIME, SIMPLE")
    private String type;
    /**
     * 指标名称
     */
    @Schema(description = "指标名称")
    private String name;
    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private long fromTimestamp;

    /**
     * 截止时间
     */
    @Schema(description = "截止时间")
    private long toTimestamp;

    /**
     * 数量
     */
    @Schema(description = "数量")
    private int count = 1000;
    /**
     * 偏移量
     */
    @Schema(description = "偏移量; 只针对Search")
    private int offset = 0;
    /**
     * 关键字
     */
    @Schema(description = "关键字; 只针对Search")
    private String keyword;

    /**
     * 最新
     */
    @Schema(description = "最新; 只针对Time")
    private boolean latest;

    /**
     * 排序
     */
    @Schema(description = "排序")
    private String sort;
}
