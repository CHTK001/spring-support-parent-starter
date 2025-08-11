package com.chua.starter.monitor.entity;

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
@TableName("system_server_setting_file_storage")
public class SystemServerSettingFileStorage extends SysBase {

    @TableId(value = "system_server_setting_file_storage_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "ID不能为空", groups = { })
    private Integer systemServerSettingFileStorageId;

    @TableField("file_storage_server_id")
    @ApiModelProperty("服务器ID")
    @Schema(description = "服务器ID")
    private Integer fileStorageServerId;

    @TableField("file_storage_type")
    @ApiModelProperty("存储类型: LOCAL/S3/MINIO/OSS 等")
    @Schema(description = "存储类型: LOCAL/S3/MINIO/OSS 等")
    @Size(max = 50)
    private String fileStorageType;

    @TableField("file_storage_base_path")
    @ApiModelProperty("本地存储根路径(LOCAL时)")
    @Schema(description = "本地存储根路径(LOCAL时)")
    @Size(max = 300)
    private String fileStorageBasePath;

    @TableField("file_storage_endpoint")
    @ApiModelProperty("对象存储Endpoint (S3/OSS/MINIO)")
    @Schema(description = "对象存储Endpoint (S3/OSS/MINIO)")
    @Size(max = 300)
    private String fileStorageEndpoint;

    @TableField("file_storage_bucket")
    @ApiModelProperty("Bucket名称")
    @Schema(description = "Bucket名称")
    @Size(max = 100)
    private String fileStorageBucket;

    @TableField("file_storage_access_key")
    @ApiModelProperty("访问Key")
    @Schema(description = "访问Key")
    @Size(max = 200)
    private String fileStorageAccessKey;

    @TableField("file_storage_secret_key")
    @ApiModelProperty("访问密钥")
    @Schema(description = "访问密钥")
    @Size(max = 200)
    private String fileStorageSecretKey;

    @TableField("file_storage_region")
    @ApiModelProperty("区域/Region")
    @Schema(description = "区域/Region")
    @Size(max = 100)
    private String fileStorageRegion;

    @TableField("file_storage_enabled")
    @ApiModelProperty("是否启用")
    @Schema(description = "是否启用")
    private Boolean fileStorageEnabled;
}

