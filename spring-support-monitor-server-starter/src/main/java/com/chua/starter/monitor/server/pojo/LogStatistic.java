package com.chua.starter.monitor.server.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author CH
 * @since 2024/7/31
 */
@Data
@ApiModel("日志统计")
public class LogStatistic {
    /**
     * x轴数据数组，用于存储图表的横轴数据。
     * 这里的x轴数据代表了某个特定指标的取值范围或者时间序列。
     */
    @JsonProperty("xAxis")
    @ApiModelProperty("x轴数据")
    private String[] xAxis;

    /**
     * y轴数据数组，用于存储图表的纵轴数据。
     * y轴数据代表了与x轴对应的数据值，用于在图表中呈现数据的变化或分布情况。
     */
    @ApiModelProperty("y轴数据")
    private Integer[] allowAxis;
    /**
     * y轴数据数组，用于存储图表的纵轴数据。
     * y轴数据代表了与x轴对应的数据值，用于在图表中呈现数据的变化或分布情况。
     */
    @ApiModelProperty("y轴数据")
    private Integer[] denyAxis;
    /**
     * y轴数据数组，用于存储图表的纵轴数据。
     * y轴数据代表了与x轴对应的数据值，用于在图表中呈现数据的变化或分布情况。
     */
    @ApiModelProperty("y轴数据")
    private Integer[] warnAxis;
}
