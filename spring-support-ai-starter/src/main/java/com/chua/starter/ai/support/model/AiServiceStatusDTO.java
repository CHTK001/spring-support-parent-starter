package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 服务运行状态 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("服务运行状态 DTO")
public class AiServiceStatusDTO implements Serializable {

    /**
     * 服务名称
     */
    @ApiModelProperty("服务名称")
    private String name;

    /**
     * 是否运行中
     */
    @ApiModelProperty("是否运行中")
    private Boolean running;

    /**
     * 进程 PID
     */
    @ApiModelProperty("进程 PID")
    private Long pid;

    /**
     * 运行时长（秒）
     */
    @ApiModelProperty("运行时长(秒)")
    private Long uptime;
}
