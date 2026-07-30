package com.chua.starter.server.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("服务器服务配置写入请求")
public class ServerServiceConfigWriteRequest {

    @ApiModelProperty("目标配置路径，未传时默认使用服务配置路径中的第一个有效项")
    private String path;

    @ApiModelProperty("配置内容，未传时默认使用服务主档中的配置模板")
    private String content;
}
