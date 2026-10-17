package com.chua.starter.datasearch.pojo.weather;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 天气总览出参（顶栏天气的唯一契约）。
 *
 * <p>字段命名与前端 {@code WeatherOverview} 一一对应：实况 + 逐小时 + 逐日。
 * 天气现象一律用 WMO 4677 数字码表达，文案与图标由消费端按码推导；
 * 数值字段用基本类型，缺数据时为 0，不出现在 JSON 里为 null 的情况。</p>
 *
 * @param location      展示地名
 * @param latitude      查询纬度
 * @param longitude     查询经度
 * @param weatherCode   当前天气现象码（WMO 4677）
 * @param temperature   当前气温（摄氏度）
 * @param feelsLike     当前体感温度（摄氏度）
 * @param humidity      相对湿度（%）
 * @param windSpeed     风速（公里/小时）
 * @param windDirection 风向（角度，0=北顺时针）
 * @param pressure      气压（hPa）
 * @param observedAt    观测时间（当地时间 yyyy-MM-ddTHH:mm）
 * @param hourly        逐小时预报（自观测时刻起，最多请求点数）
 * @param daily         逐日预报
 * @param source        数据来源（open-meteo、wttr-in 等）
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "天气总览")
public record WeatherOverviewVO(
        @Schema(description = "展示地名") String location,
        @Schema(description = "查询纬度") double latitude,
        @Schema(description = "查询经度") double longitude,
        @Schema(description = "当前天气现象码（WMO 4677）") int weatherCode,
        @Schema(description = "当前气温（摄氏度）") double temperature,
        @Schema(description = "当前体感温度（摄氏度）") double feelsLike,
        @Schema(description = "相对湿度（%）") int humidity,
        @Schema(description = "风速（公里/小时）") double windSpeed,
        @Schema(description = "风向（角度，0=北顺时针）") double windDirection,
        @Schema(description = "气压（hPa）") int pressure,
        @Schema(description = "观测时间（当地时间 yyyy-MM-ddTHH:mm）") String observedAt,
        @Schema(description = "逐小时预报") List<Hour> hourly,
        @Schema(description = "逐日预报") List<Day> daily,
        @Schema(description = "数据来源") String source) {

    /**
     * 逐小时天气点。
     *
     * @param time                    时间点（当地时间 yyyy-MM-ddTHH:mm）
     * @param weatherCode             天气现象码（WMO 4677）
     * @param temperature             气温（摄氏度）
     * @param feelsLike               体感温度（摄氏度）
     * @param humidity                相对湿度（%）
     * @param windSpeed               风速（公里/小时）
     * @param windDirection           风向（角度，0=北顺时针）
     * @param precipitation           降水量（毫米）
     * @param precipitationProbability 降水概率（%）
     */
    @Schema(description = "逐小时天气点")
    public record Hour(
            @Schema(description = "时间点（当地时间 yyyy-MM-ddTHH:mm）") String time,
            @Schema(description = "天气现象码（WMO 4677）") int weatherCode,
            @Schema(description = "气温（摄氏度）") double temperature,
            @Schema(description = "体感温度（摄氏度）") double feelsLike,
            @Schema(description = "相对湿度（%）") int humidity,
            @Schema(description = "风速（公里/小时）") double windSpeed,
            @Schema(description = "风向（角度，0=北顺时针）") double windDirection,
            @Schema(description = "降水量（毫米）") double precipitation,
            @Schema(description = "降水概率（%）") int precipitationProbability) {
    }

    /**
     * 逐日预报。
     *
     * @param date                    日期（yyyy-MM-dd）
     * @param weatherCode             天气现象码（WMO 4677）
     * @param tempMax                 最高气温（摄氏度）
     * @param tempMin                 最低气温（摄氏度）
     * @param feelsMax                最高体感温度（摄氏度）
     * @param feelsMin                最低体感温度（摄氏度）
     * @param precipitation           降水量（毫米）
     * @param precipitationProbability 降水概率（%）
     * @param windSpeed               最大风速（公里/小时）
     * @param windDirection           主导风向（角度，0=北顺时针）
     * @param uvIndex                 紫外线指数
     * @param sunrise                 日出时间（HH:mm）
     * @param sunset                  日落时间（HH:mm）
     */
    @Schema(description = "逐日预报")
    public record Day(
            @Schema(description = "日期（yyyy-MM-dd）") String date,
            @Schema(description = "天气现象码（WMO 4677）") int weatherCode,
            @Schema(description = "最高气温（摄氏度）") double tempMax,
            @Schema(description = "最低气温（摄氏度）") double tempMin,
            @Schema(description = "最高体感温度（摄氏度）") double feelsMax,
            @Schema(description = "最低体感温度（摄氏度）") double feelsMin,
            @Schema(description = "降水量（毫米）") double precipitation,
            @Schema(description = "降水概率（%）") int precipitationProbability,
            @Schema(description = "最大风速（公里/小时）") double windSpeed,
            @Schema(description = "主导风向（角度，0=北顺时针）") double windDirection,
            @Schema(description = "紫外线指数") double uvIndex,
            @Schema(description = "日出时间（HH:mm）") String sunrise,
            @Schema(description = "日落时间（HH:mm）") String sunset) {
    }
}
