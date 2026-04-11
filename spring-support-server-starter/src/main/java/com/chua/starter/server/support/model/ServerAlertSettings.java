package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("服务器预警配置")
public class ServerAlertSettings {

    @ApiModelProperty("服务器ID，空表示全局")
    private Integer serverId;

    @ApiModelProperty("是否继承全局配置")
    private Boolean inheritGlobal;

    @ApiModelProperty("是否启用预警")
    private Boolean enabled;

    @ApiModelProperty("是否同步消息中心")
    private Boolean messageEnabled;

    @ApiModelProperty("CPU 预警阈值")
    private Double cpuWarningPercent;

    @ApiModelProperty("CPU 危险阈值")
    private Double cpuDangerPercent;

    @ApiModelProperty("内存预警阈值")
    private Double memoryWarningPercent;

    @ApiModelProperty("内存危险阈值")
    private Double memoryDangerPercent;

    @ApiModelProperty("磁盘预警阈值")
    private Double diskWarningPercent;

    @ApiModelProperty("磁盘危险阈值")
    private Double diskDangerPercent;

    @ApiModelProperty("IO 预警阈值")
    private Double ioWarningBytesPerSecond;

    @ApiModelProperty("IO 危险阈值")
    private Double ioDangerBytesPerSecond;

    @ApiModelProperty("延迟预警阈值")
    private Integer latencyWarningMs;

    @ApiModelProperty("延迟危险阈值")
    private Integer latencyDangerMs;
}
