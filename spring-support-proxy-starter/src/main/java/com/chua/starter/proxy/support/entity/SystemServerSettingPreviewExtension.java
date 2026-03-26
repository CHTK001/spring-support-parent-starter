package com.chua.starter.proxy.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预览扩展名配置表
 * 用于配置 ViewServletFilter 的扩展名白名单/黑名单
 *
 * @author CH
 * @since 2024/12/08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("预览扩展名配置")
@Schema(name = "预览扩展名配置")
@TableName("proxy_server_setting_preview_extension")
public class SystemServerSettingPreviewExtension extends SysBase {

    /**
     * 主键ID
     */
    @TableId(value = "proxy_server_setting_preview_extension_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = {})
    private Integer systemServerSettingPreviewExtensionId;

    /**
     * 所属服务器ID
     */
    @TableField("preview_extension_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    @NotNull(message = "服务器ID不能为空")
    private Integer previewExtensionServerId;

    /**
     * 禁用预览的扩展名列表（黑名单，逗号分隔）
     */
    @TableField("preview_extension_disabled")
    @ApiModelProperty("禁用预览的扩展名列表（逗号分隔）")
    @Schema(description = "禁用预览的扩展名列表（逗号分隔）")
    @Size(max = 2000)
    private String previewExtensionDisabled;

    /**
     * 允许预览的扩展名列表（白名单，逗号分隔）
     */
    @TableField("preview_extension_allowed")
    @ApiModelProperty("允许预览的扩展名列表（逗号分隔）")
    @Schema(description = "允许预览的扩展名列表（逗号分隔）")
    @Size(max = 2000)
    private String previewExtensionAllowed;

    /**
     * 是否启用白名单模式
     * true: 白名单模式，只有在白名单中的才能预览
     * false: 黑名单模式，除黑名单外都能预览
     */
    @TableField("preview_extension_whitelist_mode")
    @ApiModelProperty("是否启用白名单模式")
    @Schema(description = "是否启用白名单模式")
    private Boolean previewExtensionWhitelistMode;
}




