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
 * 视图查看器配置VO
 * 用于保存和返回视图查看器的配置信息
 *
 * @author CH
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "ViewerConfigVO", description = "视图查看器配置")
@Schema(description = "视图查看器配置")
public class ViewerConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 禁用的查看器名称列表
     */
    @ApiModelProperty(value = "禁用的查看器名称列表")
    @Schema(description = "禁用的查看器名称列表")
    private List<String> disabledViewers;

    /**
     * 查看器优先级配置
     * key: 查看器名称, value: 优先级
     */
    @ApiModelProperty(value = "查看器优先级配置")
    @Schema(description = "查看器优先级配置，key为查看器名称，value为优先级")
    private java.util.Map<String, Integer> viewerPriorities;

    /**
     * 是否启用查看器功能
     */
    @ApiModelProperty(value = "是否启用查看器功能")
    @Schema(description = "是否启用查看器功能")
    private Boolean enabled;

    /**
     * 其他扩展配置
     */
    @ApiModelProperty(value = "其他扩展配置")
    @Schema(description = "其他扩展配置")
    private java.util.Map<String, Object> extra;
}




