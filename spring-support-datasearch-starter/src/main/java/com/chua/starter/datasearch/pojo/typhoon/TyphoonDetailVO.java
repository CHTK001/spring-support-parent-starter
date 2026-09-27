package com.chua.starter.datasearch.pojo.typhoon;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个台风详情出参（顶栏台风弹窗的唯一契约）。
 *
 * <p>字段命名与前端 {@code TyphoonDetail} 一一对应：历史路径 + 预报路径。
 * 前端拿到后台数据时原样透传、不做字段补齐，故数据源给出的字段必须全部透出
 * （含顶层 {@code warnlevel} 与路径点的三级风圈 {@code r12}）。
 * 风圈半径统一为四方位数组 {@code [东北, 东南, 西南, 西北]}（公里），
 * 由后端把数据源的 "250|150|250|150" 报文解析成数组，前端只做绘制。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "台风详情")
@Data
public class TyphoonDetailVO {

    /**
     * 台风编号
    */
    @Schema(description = "台风编号")
    private String id;

    /**
     * 中文名
    */
    @Schema(description = "中文名")
    private String name;

    /**
     * 起始强度等级中文名
     */
    @Schema(description = "起始强度等级中文名")
    private String strong;

    /**
     * 预警级别（white/blue/yellow/orange/red 或中文；未发布预警时缺省）
     */
    @Schema(description = "预警级别")
    private String warnlevel;

    /**
     * 历史路径点
     */
    @Schema(description = "历史路径点")
    private List<Track> path = new ArrayList<>();

    /**
     * 预报路径（按机构分通道）
    */
    @Schema(description = "预报路径")
    private List<Forecast> forecast = new ArrayList<>();

    /**
     * 数据来源（zhejiang-typhoon 等）
    */
    @Schema(description = "数据来源")
    private String source;

    /**
     * 路径点（历史与预报通用）。
     */
    @Schema(description = "台风路径点")
    @Data
    public static class Track {

        /**
         * 时间
        */
        @Schema(description = "时间")
        private String time;

        /**
         * 经度
        */
        @Schema(description = "经度")
        private Double lng;

        /**
         * 纬度
        */
        @Schema(description = "纬度")
        private Double lat;

        /**
         * 强度等级中文名
        */
        @Schema(description = "强度等级中文名")
        private String strong;

        /**
         * 风速（米/秒）
        */
        @Schema(description = "风速（米/秒）")
        private Double speed;

        /**
         * 中心气压（hPa）
        */
        @Schema(description = "中心气压（hPa）")
        private Double pressure;

        /**
         * 7 级风圈四方位半径（公里）[东北, 东南, 西南, 西北]
        */
        @Schema(description = "7 级风圈四方位半径（公里）")
        private List<Double> r7;

        /**
         * 10 级风圈四方位半径（公里）[东北, 东南, 西南, 西北]
         */
        @Schema(description = "10 级风圈四方位半径（公里）")
        private List<Double> r10;

        /**
         * 12 级风圈四方位半径（公里）[东北, 东南, 西南, 西北]
         */
        @Schema(description = "12 级风圈四方位半径（公里）")
        private List<Double> r12;
    }

    /**
     * 单条预报通道。
     */
    @Schema(description = "预报通道")
    @Data
    public static class Forecast {

        /**
         * 预报机构名（如 中央气象台）
        */
        @Schema(description = "预报机构名")
        private String name;

        /**
         * 预报路径点
        */
        @Schema(description = "预报路径点")
        private List<Track> points = new ArrayList<>();
    }
}
