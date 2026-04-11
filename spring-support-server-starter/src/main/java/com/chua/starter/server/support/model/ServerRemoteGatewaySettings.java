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
@ApiModel("服务器远程代理配置")
public class ServerRemoteGatewaySettings {

    @ApiModelProperty("是否继承全局配置，仅服务器级配置使用")
    private Boolean inheritGlobal;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("代理实现编码")
    private String provider;

    @ApiModelProperty("网关地址")
    private String gatewayUrl;

    @ApiModelProperty("远控协议")
    private String protocol;

    @ApiModelProperty("入口路径")
    private String launchPath;

    @ApiModelProperty("WebSocket 路径")
    private String websocketPath;

    @ApiModelProperty("连接编号")
    private String connectionId;
}
