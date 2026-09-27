package com.chua.starter.datasearch.pojo.weather;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气总览出参（顶栏天气的唯一契约）。
 *
 * <p>字段命名与前端 {@code WeatherOverview} 一一对应：实况 + 逐小时 + 逐日。
 * 天气现象一律用 WMO 4677 数字码表达，文案与图标由消费端按码推导；
 * 数值字段用基本类型，缺数据时为 0，不出现在 JSON 里为 null 的情况。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "天气总览")
@Data
public class WeatherOverviewVO {

    /**
     * 展示地名
    */
    @Schema(description = "展示地名")
    private String location;

    /**
     * 查询纬度
    */
    @Schema(description = "查询纬度")
    private double latitude;

    /**
     * 查询经度
    */
    @Schema(description = "查询经度")
    private double longitude;

    /**
     * 当前天气现象码（WMO 4677）
    */
    @Schema(description = "当前天气现象码（WMO 4677）")
    private int weatherCode;

    /**
     * 当前气温（摄氏度）
    */
    @Schema(description = "当前气温（摄氏度）")
    private double temperature;

    /**
     * 当前体感温度（摄氏度）
    */
    @Schema(description = "当前体感温度（摄氏度）")
    private double feelsLike;

    /**
     * 相对湿度（%）
    */
    @Schema(description = "相对湿度（%）")
    private int humidity;

    /**
     * 风速（公里/小时）
    */
    @Schema(description = "风速（公里/小时）")
    private double windSpeed;

    /**
     * 风向（角度，0=北顺时针）
    */
    @Schema(description = "风向（角度，0=北顺时针）")
    private double windDirection;

    /**
     * 气压（hPa）
    */
    @Schema(description = "气压（hPa）")
    private int pressure;

    /**
     * 观测时间（当地时间 yyyy-MM-ddTHH:mm）
    */
    @Schema(description = "观测时间（当地时间 yyyy-MM-ddTHH:mm）")
    private String observedAt;

    /**
     * 逐小时预报（自观测时刻起，最多请求点数）
    */
    @Schema(description = "逐小时预报")
    private List<Hour> hourly = new ArrayList<>();

    /**
     * 逐日预报
    */
    @Schema(description = "逐日预报")
    private List<Day> daily = new ArrayList<>();

    /**
     * 数据来源（open-meteo、wttr-in 等）
    */
    @Schema(description = "数据来源")
    private String source;

    /**
     * 逐小时天气点。
     */
    @Schema(description = "逐小时天气点")
    @Data
    public static class Hour {

        /**
         * 时间点（当地时间 yyyy-MM-ddTHH:mm）
        */
        @Schema(description = "时间点（当地时间 yyyy-MM-ddTHH:mm）")
        private String time;

        /**
         * 天气现象码（WMO 4677）
        */
        @Schema(description = "天气现象码（WMO 4677）")
        private int weatherCode;

        /**
         * 气温（摄氏度）
        */
        @Schema(description = "气温（摄氏度）")
        private double temperature;

        /**
         * 体感温度（摄氏度）
        */
        @Schema(description = "体感温度（摄氏度）")
        private double feelsLike;

        /**
         * 相对湿度（%）
        */
        @Schema(description = "相对湿度（%）")
        private int humidity;

        /**
         * 风速（公里/小时）
        */
        @Schema(description = "风速（公里/小时）")
        private double windSpeed;

        /**
         * 风向（角度，0=北顺时针）
        */
        @Schema(description = "风向（角度，0=北顺时针）")
        private double windDirection;

        /**
         * 降水量（毫米）
        */
        @Schema(description = "降水量（毫米）")
        private double precipitation;

        /**
         * 降水概率（%）
        */
        @Schema(description = "降水概率（%）")
        private int precipitationProbability;
    }

    /**
     * 逐日预报。
     */
    @Schema(description = "逐日预报")
    @Data
    public static class Day {

        /**
         * 日期（yyyy-MM-dd）
        */
        @Schema(description = "日期（yyyy-MM-dd）")
        private String date;

        /**
         * 天气现象码（WMO 4677）
        */
        @Schema(description = "天气现象码（WMO 4677）")
        private int weatherCode;

        /**
         * 最高气温（摄氏度）
        */
        @Schema(description = "最高气温（摄氏度）")
        private double tempMax;

        /**
         * 最低气温（摄氏度）
        */
        @Schema(description = "最低气温（摄氏度）")
        private double tempMin;

        /**
         * 最高体感温度（摄氏度）
        */
        @Schema(description = "最高体感温度（摄氏度）")
        private double feelsMax;

        /**
         * 最低体感温度（摄氏度）
        */
        @Schema(description = "最低体感温度（摄氏度）")
        private double feelsMin;

        /**
         * 降水量（毫米）
        */
        @Schema(description = "降水量（毫米）")
        private double precipitation;

        /**
         * 降水概率（%）
        */
        @Schema(description = "降水概率（%）")
        private int precipitationProbability;

        /**
         * 最大风速（公里/小时）
        */
        @Schema(description = "最大风速（公里/小时）")
        private double windSpeed;

        /**
         * 主导风向（角度，0=北顺时针）
        */
        @Schema(description = "主导风向（角度，0=北顺时针）")
        private double windDirection;

        /**
         * 紫外线指数
        */
        @Schema(description = "紫外线指数")
        private double uvIndex;

        /**
         * 日出时间（HH:mm）
        */
        @Schema(description = "日出时间（HH:mm）")
        private String sunrise;

        /**
         * 日落时间（HH:mm）
        */
        @Schema(description = "日落时间（HH:mm）")
        private String sunset;
    }
}
