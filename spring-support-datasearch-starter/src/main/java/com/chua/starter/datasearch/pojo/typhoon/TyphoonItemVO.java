package com.chua.starter.datasearch.pojo.typhoon;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "活跃台风列表项")
@Data
public class TyphoonItemVO {

    /**
     * 台风编号（如 202618）
     */
    @Schema(description = "台风编号")
    private String id;

    /**
     * 中文名
     */
    @Schema(description = "中文名")
    private String name;

    /**
     * 英文名（如 SURIGAE）
     */
    @Schema(description = "英文名")
    private String enname;

    /**
     * 强度等级中文名（如台风、强台风）
     */
    @Schema(description = "强度等级中文名")
    private String strong;

    /**
     * 中心纬度
     */
    @Schema(description = "中心纬度")
    private Double lat;

    /**
     * 中心经度
     */
    @Schema(description = "中心经度")
    private Double lng;

    /**
     * 观测时间（本系统口径 yyyy-MM-ddTHH:mm）
     */
    @Schema(description = "观测时间")
    private String time;

    /**
     * 报文时间文本（如 9月24日8时）
     */
    @Schema(description = "报文时间文本")
    private String timeformate;

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
     * 中心风力等级（如 8）
     */
    @Schema(description = "中心风力等级")
    private Double power;

    /**
     * 预警级别（white/blue/yellow/orange/red 或中文；未发布预警时缺省）
     */
    @Schema(description = "预警级别")
    private String warnlevel;

    /**
     * 移动方向（如 西北偏西）
     */
    @Schema(description = "移动方向")
    private String movedirection;

    /**
     * 移动速度（公里/小时）
     */
    @Schema(description = "移动速度（公里/小时）")
    private Double movespeed;

    /**
     * 数据来源（zhejiang-typhoon 等）
     */
    @Schema(description = "数据来源")
    private String source;
}
