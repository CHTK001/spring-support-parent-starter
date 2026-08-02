package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聚合服务运行状态表，记录服务名称、运行标志、PID、启动时间与运行时长。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_service_status")
@ApiModel("服务运行状态")
public class AiServiceStatus implements Serializable {

    /**
     * 状态主键 ID
     */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty("状态主键 ID")
    private Long statusId;

    /**
     * 服务名称
     */
    @TableField("status_service_name")
    @ApiModelProperty("服务名称")
    private String statusServiceName;

    /**
     * 是否运行中
     */
    @TableField("status_running")
    @ApiModelProperty("是否运行中")
    private Boolean statusRunning;

    /**
     * 进程 PID
     */
    @TableField("status_pid")
    @ApiModelProperty("进程 PID")
    private Long statusPid;

    /**
     * 启动时间
     */
    @TableField("status_start_time")
    @ApiModelProperty("启动时间")
    private LocalDateTime statusStartTime;

    /**
     * 运行时长（秒）
     */
    @TableField("status_uptime_sec")
    @ApiModelProperty("运行时长(秒)")
    private Long statusUptimeSec;

    /**
     * 更新时间
     */
    @TableField(value = "status_update_time", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty("更新时间")
    private LocalDateTime statusUpdateTime;
}