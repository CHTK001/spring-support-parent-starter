package com.chua.starter.proxy.support.pojo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 预览扩展名配置VO
 * 用于前后端数据传输
 *
 * @author CH
 * @since 2024/12/08
 */
@Data
@ApiModel("预览扩展名配置VO")
@Schema(description = "预览扩展名配置VO")
public class PreviewExtensionConfigVo {

    /**
     * 服务器ID
     */
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer serverId;

    /**
     * 过滤器设置ID（可选）
     */
    @ApiModelProperty("过滤器设置ID")
    @Schema(description = "过滤器设置ID")
    private Integer filterSettingId;

    /**
     * 禁用预览的扩展名列表（黑名单）
     */
    @ApiModelProperty("禁用预览的扩展名列表")
    @Schema(description = "禁用预览的扩展名列表")
    private List<String> disabledExtensions;

    /**
     * 允许预览的扩展名列表（白名单）
     */
    @ApiModelProperty("允许预览的扩展名列表")
    @Schema(description = "允许预览的扩展名列表")
    private List<String> allowedExtensions;

    /**
     * 是否启用白名单模式
     */
    @ApiModelProperty("是否启用白名单模式")
    @Schema(description = "是否启用白名单模式")
    private Boolean whitelistMode;
}




