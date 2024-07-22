package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.oss.setting.BucketSetting;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * @since 2024/7/22 
 * @author CH
 */

/**
 * 文件存储
 */
@ApiModel(description = "文件存储")
@Schema(description = "文件存储")
@Data
@TableName(value = "file_storage")
public class FileStorage implements Serializable {
    @TableId(value = "file_storage_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer fileStorageId;

    /**
     * 文件存储名称
     */
    @TableField(value = "file_storage_name")
    @ApiModelProperty(value = "文件存储名称")
    @Schema(description = "文件存储名称")
    @Size(max = 255, message = "文件存储名称最大长度要小于 255")
    private String fileStorageName;

    /**
     * 实现类型
     */
    @TableField(value = "file_storage_type")
    @ApiModelProperty(value = "实现类型")
    @Schema(description = "实现类型")
    @Size(max = 255, message = "实现类型最大长度要小于 255")
    private String fileStorageType;

    /**
     * 账号
     */
    @TableField(value = "file_storage_user")
    @ApiModelProperty(value = "账号")
    @Schema(description = "账号")
    @Size(max = 255, message = "账号最大长度要小于 255")
    private String fileStorageUser;

    /**
     * 密码
     */
    @TableField(value = "file_storage_password")
    @ApiModelProperty(value = "密码")
    @Schema(description = "密码")
    @Size(max = 255, message = "密码最大长度要小于 255")
    private String fileStoragePassword;

    /**
     * 远程访问端点
     */
    @TableField(value = "file_storage_endpoint")
    @ApiModelProperty(value = "远程访问端点")
    @Schema(description = "远程访问端点")
    @Size(max = 255, message = "远程访问端点最大长度要小于 255")
    private String fileStorageEndpoint;

    /**
     * bucket
     */
    @TableField(value = "file_storage_bucket")
    @ApiModelProperty(value = "bucket")
    @Schema(description = "bucket")
    @Size(max = 255, message = "bucket最大长度要小于 255")
    private String fileStorageBucket;

    /**
     * 服务器协议; HTTP
     */
    @TableField(value = "file_storage_protocol_id")
    @ApiModelProperty(value = "服务器协议; HTTP")
    @Schema(description = "服务器协议; HTTP")
    private Integer fileStorageProtocolId;

    /**
     * 状态;0:停用
     */
    @TableField(value = "file_storage_status")
    @ApiModelProperty(value = "状态;0:停用")
    @Schema(description = "状态;0:停用")
    private Integer fileStorageStatus;

    private static final long serialVersionUID = 1L;

    @JsonIgnore
    public BucketSetting createBucketSetting() {
        return BucketSetting.builder()
                .accessKeyId(fileStorageUser)
                .accessKeySecret(fileStoragePassword)
                .bucket(fileStorageBucket)
                .endpoint(fileStorageEndpoint)
                .region(fileStorageEndpoint)
                .build();
    }
}