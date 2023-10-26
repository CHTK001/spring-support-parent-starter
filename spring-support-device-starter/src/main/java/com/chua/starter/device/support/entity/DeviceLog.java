package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */     
@Schema
@Data
@TableName(value = "device_log")
public class DeviceLog implements Serializable {
    @TableId(value = "device_log_id", type = IdType.AUTO)
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer deviceLogId;

    /**
     * 日志类型
     */
    @TableField(value = "device_log_type")
    @Schema(description="日志类型")
    @Size(max = 255,message = "日志类型最大长度要小于 255")
    private String deviceLogType;

    /**
     * 错误信息
     */
    @TableField(value = "device_log_error")
    @Schema(description="错误信息")
    @Size(max = 255,message = "错误信息最大长度要小于 255")
    private String deviceLogError;

    /**
     * 设备序列号
     */
    @TableField(value = "device_log_imsi")
    @Schema(description="设备序列号")
    @Size(max = 255,message = "设备序列号最大长度要小于 255")
    private String deviceLogImsi;

    /**
     * 来源
     */
    @TableField(value = "device_log_from")
    @Schema(description="来源")
    @Size(max = 255,message = "来源最大长度要小于 255")
    private String deviceLogFrom;

    @TableField(value = "create_time")
    @Schema(description="")
    private Date createTime;

    @TableField(value = "update_time")
    @Schema(description="")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}