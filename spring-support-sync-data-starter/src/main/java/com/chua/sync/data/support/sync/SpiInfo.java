package com.chua.sync.data.support.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * SPI 信息
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Schema(description = "SPI信息")
public class SpiInfo {

    /**
     * SPI 名称
     */
    @Schema(description = "SPI名称")
    private String name;

    /**
     * SPI 显示名称
     */
    @Schema(description = "SPI显示名称")
    private String displayName;

    /**
     * SPI 描述
     */
    @Schema(description = "SPI描述")
    private String description;

    /**
     * SPI 类型: INPUT/OUTPUT/DATA_CENTER/FILTER
     */
    @Schema(description = "SPI类型")
    private String type;

    /**
     * SPI 实现类
     */
    @Schema(description = "SPI实现类")
    private String className;

    /**
     * 图标
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 颜色
     */
    @Schema(description = "颜色")
    private String color;

    /**
     * 配置参数列表
     */
    @Schema(description = "配置参数列表")
    private List<SpiParameter> parameters;

    /**
     * 排序
     */
    @Schema(description = "排序")
    private Integer order;

    /**
     * 是否可用
     */
    @Schema(description = "是否可用")
    private Boolean available = true;
}
