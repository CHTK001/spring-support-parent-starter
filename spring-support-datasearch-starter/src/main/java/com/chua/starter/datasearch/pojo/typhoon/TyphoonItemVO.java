package com.chua.starter.datasearch.pojo.typhoon;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 活跃台风列表项出参（顶栏台风的唯一契约）。
 *
 * <p>字段命名与前端 {@code TyphoonItem} 一一对应。前端拿到后台数据时原样透传、不做
 * 字段补齐，数据源给出的字段必须在这里全部透出，否则顶栏弹窗对应位置直接显示
 * 「-」。数值字段用包装类型，数据源缺该字段时返回 null，前端按「未知」处理而不是 0。</p>
 *
 * <p>{@code impact}（预计影响时段）数据源未提供，因此不在契约里：前端取不到时会
 * 回落到「移动方向 + 移动速度」的组合文案，不需要后端补空字段。</p>
 *
 * @param id            台风编号（如 202618）
 * @param name          中文名
 * @param enname        英文名（如 SURIGAE）
 * @param strong        强度等级中文名（如台风、强台风）
 * @param lat           中心纬度
 * @param lng           中心经度
 * @param time          观测时间（本系统口径 yyyy-MM-ddTHH:mm）
 * @param timeformate   报文时间文本（如 9月24日8时）
 * @param speed         风速（米/秒）
 * @param pressure      中心气压（hPa）
 * @param power         中心风力等级（如 8）
 * @param warnlevel     预警级别（white/blue/yellow/orange/red 或中文；未发布预警时缺省）
 * @param movedirection 移动方向（如 西北偏西）
 * @param movespeed     移动速度（公里/小时）
 * @param source        数据来源（zhejiang-typhoon 等）
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "活跃台风列表项")
public record TyphoonItemVO(
        @Schema(description = "台风编号") String id,
        @Schema(description = "中文名") String name,
        @Schema(description = "英文名") String enname,
        @Schema(description = "强度等级中文名") String strong,
        @Schema(description = "中心纬度") Double lat,
        @Schema(description = "中心经度") Double lng,
        @Schema(description = "观测时间") String time,
        @Schema(description = "报文时间文本") String timeformate,
        @Schema(description = "风速（米/秒）") Double speed,
        @Schema(description = "中心气压（hPa）") Double pressure,
        @Schema(description = "中心风力等级") Double power,
        @Schema(description = "预警级别") String warnlevel,
        @Schema(description = "移动方向") String movedirection,
        @Schema(description = "移动速度（公里/小时）") Double movespeed,
        @Schema(description = "数据来源") String source) {
}
