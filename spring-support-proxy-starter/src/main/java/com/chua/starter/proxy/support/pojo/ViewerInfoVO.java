package com.chua.starter.proxy.support.pojo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 视图查看器信息VO
 * 用于返回视图查看器的详细信息
 *
 * @author CH
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "ViewerInfoVO", description = "视图查看器信息")
@Schema(description = "视图查看器信息")
public class ViewerInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 查看器名称
     */
    @ApiModelProperty(value = "查看器名称")
    @Schema(description = "查看器名称")
    private String name;

    /**
     * 查看器描述
     */
    @ApiModelProperty(value = "查看器描述")
    @Schema(description = "查看器描述")
    private String description;

    /**
     * 优先级（数值越小优先级越高）
     */
    @ApiModelProperty(value = "优先级")
    @Schema(description = "优先级，数值越小优先级越高")
    private Integer priority;

    /**
     * 是否启用
     */
    @ApiModelProperty(value = "是否启用")
    @Schema(description = "是否启用")
    private Boolean enabled;

    /**
     * 支持的内容类型数组
     */
    @ApiModelProperty(value = "支持的内容类型数组")
    @Schema(description = "支持的内容类型数组")
    private String[] supportedContentTypes;

    /**
     * 支持的文件扩展名数组
     */
    @ApiModelProperty(value = "支持的文件扩展名数组")
    @Schema(description = "支持的文件扩展名数组")
    private String[] supportedExtensions;

    /**
     * 目标格式
     */
    @ApiModelProperty(value = "目标格式")
    @Schema(description = "目标格式")
    private String targetFormat;
}




