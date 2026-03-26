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
 * FileStorageServletFilter 专用配置表
 * 字段前缀：fileStorage
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("文件存储过滤器配置")
@Schema(name = "文件存储配置")
@TableName("proxy_server_setting_file_storage")
public class SystemServerSettingFileStorage extends SysBase {

    /** 主键ID */
    @TableId(value = "proxy_server_setting_file_storage_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = { })
    private Integer systemServerSettingFileStorageId;

    /** 所属服务器ID */
    @TableField("file_storage_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer fileStorageServerId;

    /** 存储类型: LOCAL/S3/MINIO/OSS */
    @TableField("file_storage_type")
    @ApiModelProperty("存储类型: LOCAL/S3/MINIO/OSS")
    @Schema(description = "存储类型: LOCAL/S3/MINIO/OSS")
    @Size(max = 50)
    private String fileStorageType;

    /** 本地根路径(仅 LOCAL 使用) */
    @TableField("file_storage_base_path")
    @ApiModelProperty("本地根路径")
    @Schema(description = "本地根路径")
    @Size(max = 500)
    private String fileStorageBasePath;

    /** 对象存储 Endpoint */
    @TableField("file_storage_endpoint")
    @ApiModelProperty("对象存储 Endpoint")
    @Schema(description = "对象存储 Endpoint")
    @Size(max = 255)
    private String fileStorageEndpoint;

    /** Bucket */
    @TableField("file_storage_bucket")
    @ApiModelProperty("Bucket")
    @Schema(description = "Bucket")
    @Size(max = 128)
    private String fileStorageBucket;

    /** AccessKey */
    @TableField("file_storage_access_key")
    @ApiModelProperty("AccessKey")
    @Schema(description = "AccessKey")
    @Size(max = 200)
    private String fileStorageAccessKey;

    /** SecretKey */
    @TableField("file_storage_secret_key")
    @ApiModelProperty("SecretKey")
    @Schema(description = "SecretKey")
    @Size(max = 200)
    private String fileStorageSecretKey;

    /** Region */
    @TableField("file_storage_region")
    @ApiModelProperty("Region")
    @Schema(description = "Region")
    @Size(max = 100)
    private String fileStorageRegion;

    /** 是否启用 */
    @TableField("file_storage_enabled")
    @ApiModelProperty("是否启用")
    @Schema(description = "是否启用")
    private Boolean fileStorageEnabled;

    /**
     * 连接超时
     */
    @TableField("file_storage_connection_timeout")
    @ApiModelProperty("连接超时")
    @Schema(description = "连接超时")
    private Long fileStorageConnectionTimeout;
}






