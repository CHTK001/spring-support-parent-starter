package com.chua.starter.datasearch.pojo.typhoon;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * @param id        台风编号
 * @param name      中文名
 * @param strong    起始强度等级中文名
 * @param warnlevel 预警级别（white/blue/yellow/orange/red 或中文；未发布预警时缺省）
 * @param path      历史路径点
 * @param forecast  预报路径（按机构分通道）
 * @param source    数据来源（zhejiang-typhoon 等）
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "台风详情")
public record TyphoonDetailVO(
        @Schema(description = "台风编号") String id,
        @Schema(description = "中文名") String name,
        @Schema(description = "起始强度等级中文名") String strong,
        @Schema(description = "预警级别") String warnlevel,
        @Schema(description = "历史路径点") List<Track> path,
        @Schema(description = "预报路径") List<Forecast> forecast,
        @Schema(description = "数据来源") String source) {

    /**
     * 路径点（历史与预报通用）。
     *
     * @param time     时间
     * @param lng      经度
     * @param lat      纬度
     * @param strong   强度等级中文名
     * @param speed    风速（米/秒）
     * @param pressure 中心气压（hPa）
     * @param r7       7 级风圈四方位半径（公里）[东北, 东南, 西南, 西北]
     * @param r10      10 级风圈四方位半径（公里）[东北, 东南, 西南, 西北]
     * @param r12      12 级风圈四方位半径（公里）[东北, 东南, 西南, 西北]
     */
    @Schema(description = "台风路径点")
    public record Track(
            @Schema(description = "时间") String time,
            @Schema(description = "经度") Double lng,
            @Schema(description = "纬度") Double lat,
            @Schema(description = "强度等级中文名") String strong,
            @Schema(description = "风速（米/秒）") Double speed,
            @Schema(description = "中心气压（hPa）") Double pressure,
            @Schema(description = "7 级风圈四方位半径（公里）") List<Double> r7,
            @Schema(description = "10 级风圈四方位半径（公里）") List<Double> r10,
            @Schema(description = "12 级风圈四方位半径（公里）") List<Double> r12) {
    }

    /**
     * 单条预报通道。
     *
     * @param name   预报机构名（如 中央气象台）
     * @param points 预报路径点
     */
    @Schema(description = "预报通道")
    public record Forecast(
            @Schema(description = "预报机构名") String name,
            @Schema(description = "预报路径点") List<Track> points) {
    }
}
