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
 * @since 2024/7/22 
 * @author CH
 */

/**
 * 文件存储日志
 */
@ApiModel(description = "文件存储日志")
@Schema(description = "文件存储日志")
@Data
@TableName(value = "file_storage_log")
public class FileStorageLog implements Serializable {
    @TableId(value = "file_storage_log_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer fileStorageLogId;

    @TableField(value = "file_storage_id")
    @ApiModelProperty(value = "")
    @Schema(description = "")
    private Integer fileStorageId;

    /**
     * 请求客户端地址
     */
    @TableField(value = "file_storage_log_address")
    @ApiModelProperty(value = "请求客户端地址")
    @Schema(description = "请求客户端地址")
    @Size(max = 255, message = "请求客户端地址最大长度要小于 255")
    private String fileStorageLogAddress;

    /**
     * 请求文件地址
     */
    @TableField(value = "file_storage_log_url")
    @ApiModelProperty(value = "请求文件地址")
    @Schema(description = "请求文件地址")
    @Size(max = 255, message = "请求文件地址最大长度要小于 255")
    private String fileStorageLogUrl;

    private static final long serialVersionUID = 1L;
}