package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.oss.setting.BucketSetting;
import com.chua.common.support.oss.setting.WebdavCookieSetting;
import com.chua.common.support.utils.StringUtils;
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
 * @since 2024/7/23 
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
     * 文件存储路径
     */
    @TableField(value = "file_storage_name")
    @ApiModelProperty(value = "文件存储路径")
    @Schema(description = "文件存储路径")
    @Size(max = 255, message = "文件存储路径最大长度要小于 255")
    private String fileStorageName;

    /**
     * 存储名称
     */
    @TableField(value = "file_storage_desc")
    @ApiModelProperty(value = "存储名称")
    @Schema(description = "存储名称")
    @Size(max = 255, message = "存储名称最大长度要小于 255")
    private String fileStorageDesc;

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
     * 图表
     */
    @TableField(value = "file_storage_icon")
    @ApiModelProperty(value = "图表")
    @Schema(description = "图表")
    @Size(max = 255, message = "图表最大长度要小于 255")
    private String fileStorageIcon;

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
     * 服务器协议;
     */
    @TableField(value = "file_storage_protocol_id")
    @ApiModelProperty(value = "服务器协议; ")
    @Schema(description = "服务器协议; ")
    private Integer fileStorageProtocolId;

    /**
     * 状态;0:停用
     */
    @TableField(value = "file_storage_status")
    @ApiModelProperty(value = "状态;0:停用")
    @Schema(description = "状态;0:停用")
    private Integer fileStorageStatus;

    /**
     * cookie
     */
    @TableField(value = "file_storage_cookie")
    @ApiModelProperty(value = "cookie")
    @Schema(description = "cookie")
    private String fileStorageCookie;

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
    @JsonIgnore
    public WebdavCookieSetting createBucketCookieSetting() {
        if(StringUtils.isEmpty(fileStorageCookie)) {
            throw new IllegalArgumentException("cookie 不能为空");
        }
        return WebdavCookieSetting.builder()
                .accessKeyId(fileStorageUser)
                .cookie(fileStorageCookie)
                .accessKeySecret(fileStoragePassword)
                .bucket(fileStorageBucket)
                .endpoint(fileStorageEndpoint)
                .region(fileStorageEndpoint)
                .build();
    }
}