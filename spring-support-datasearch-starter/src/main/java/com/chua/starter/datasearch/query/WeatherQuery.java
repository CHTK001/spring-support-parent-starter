package com.chua.starter.datasearch.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 天气查询入参。
 *
 * <p>坐标优先；缺坐标时按城市名解析。全部可选，顶栏探测（HEAD）与默认查询都无需传参。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Data
@Schema(description = "天气查询")
public class WeatherQuery {

    /**
     * 纬度
    */
    @Schema(description = "纬度")
    private Double latitude;

    /**
     * 经度
    */
    @Schema(description = "经度")
    private Double longitude;

    /**
     * 城市名（无坐标时使用）
    */
    @Schema(description = "城市名（无坐标时使用）")
    private String city;

    /**
     * 预报天数，默认 7
    */
    @Schema(description = "预报天数，默认 7")
    private Integer days;

    /**
     * 逐小时点数，默认 24
    */
    @Schema(description = "逐小时点数，默认 24")
    private Integer hours;
}
