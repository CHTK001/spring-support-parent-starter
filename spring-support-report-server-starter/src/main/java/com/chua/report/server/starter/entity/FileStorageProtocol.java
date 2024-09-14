package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 *
 *
 * @since 2024/7/23 
 * @author CH
 */

/**
 * 文件存储服务器协议
 */
@ApiModel(description = "文件存储服务器协议")
@Schema(description = "文件存储服务器协议")
@Data
@TableName(value = "file_storage_protocol")
public class FileStorageProtocol implements Serializable {
    @TableId(value = "file_storage_protocol_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer fileStorageProtocolId;

    /**
     * 开启远程服务器端口
     */
    @TableField(value = "file_storage_protocol_port")
    @ApiModelProperty(value = "开启远程服务器端口")
    @Schema(description = "开启远程服务器端口")
    private Integer fileStorageProtocolPort;

    /**
     * 服务器说明
     */
    @TableField(value = "file_storage_protocol_desc")
    @ApiModelProperty(value = "服务器说明")
    @Schema(description = "服务器说明")
    @Size(max = 255, message = "服务器说明最大长度要小于 255")
    private String fileStorageProtocolDesc;

    /**
     * 服务器协议; HTTP
     */
    @TableField(value = "file_storage_protocol_name")
    @ApiModelProperty(value = "服务器协议; HTTP")
    @Schema(description = "服务器协议; HTTP")
    @Size(max = 255, message = "服务器协议; HTTP最大长度要小于 255")
    private String fileStorageProtocolName;

    /**
     * 开启远程服务器主机
     */
    @TableField(value = "file_storage_protocol_host")
    @ApiModelProperty(value = "开启远程服务器主机")
    @Schema(description = "开启远程服务器主机")
    @Size(max = 255, message = "开启远程服务器主机最大长度要小于 255")
    private String fileStorageProtocolHost;
    /**
     * 状态;0:停用
     */
    @TableField(value = "file_storage_protocol_preview_or_download")
    @ApiModelProperty(value = "0: 全部支持;1:预览; 2:下载")
    @Schema(description = "0: 全部支持;1:预览; 2:下载")
    private Integer fileStorageProtocolPreviewOrDownload;
    /**
     * 远程服务器状态; 0:未开启
     */
    @TableField(value = "file_storage_protocol_status")
    @ApiModelProperty(value = "远程服务器状态; 0:未开启")
    @Schema(description = "远程服务器状态; 0:未开启")
    private Integer fileStorageProtocolStatus;

    /**
     * 插件列表，多个逗号分隔
     */
    @TableField(value = "file_storage_protocol_plugins")
    @ApiModelProperty(value = "插件列表，多个逗号分隔")
    @Schema(description = "插件列表，多个逗号分隔")
    @Size(max = 255, message = "插件列表，多个逗号分隔最大长度要小于 255")
    private String fileStorageProtocolPlugins;

    /**
     * 配置列表，多个逗号分隔
     */
    @TableField(value = "file_storage_protocol_setting")
    @ApiModelProperty(value = "配置列表，多个逗号分隔")
    @Schema(description = "配置列表，多个逗号分隔")
    @Size(max = 255, message = "配置列表，多个逗号分隔最大长度要小于 255")
    private String fileStorageProtocolSetting;

    /**
     * 是否开启插件；0:不开启
     */
    @TableField(value = "file_storage_protocol_plugin_open")
    @ApiModelProperty(value = "是否开启插件；0:不开启")
    @Schema(description = "是否开启插件；0:不开启")
    private Integer fileStorageProtocolPluginOpen;

    /**
     * 是否开启配置；0:不开启
     */
    @TableField(value = "file_storage_protocol_setting_open")
    @ApiModelProperty(value = "是否开启配置；0:不开启")
    @Schema(description = "是否开启配置；0:不开启")
    private Integer fileStorageProtocolSettingOpen;

    /**
     * 是否开启UA；0:不开启
     */
    @TableField(value = "file_storage_protocol_ua_open")
    @ApiModelProperty(value = "是否开启UA；0:不开启")
    @Schema(description = "是否开启UA；0:不开启")
    private Integer fileStorageProtocolUaOpen;

    /**
     * 是否开启range；0:不开启
     */
    @TableField(value = "file_storage_protocol_rang_open")
    @ApiModelProperty(value = "是否开启Range；0:不开启")
    @Schema(description = "是否开启Range；0:不开启")
    private Integer fileStorageProtocolRangeOpen;

    /**
     * UA
     */
    @TableField(value = "file_storage_protocol_ua")
    @ApiModelProperty(value = "UA")
    @Schema(description = "UA")
    @Size(max = 255, message = "UA最大长度要小于 255")
    private String fileStorageProtocolUa;

    /**
     * UA
     */
    @TableField(value = "file_storage_protocol_download_ua")
    @ApiModelProperty(value = "UA")
    @Schema(description = "UA")
    @Size(max = 255, message = "UA最大长度要小于 255")
    private String fileStorageProtocolDownloadUa;

    private static final long serialVersionUID = 1L;

}