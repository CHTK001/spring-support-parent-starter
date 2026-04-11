package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("软件服务控制请求")
public class SoftServiceCommandRequest {

    @ApiModelProperty("自定义服务名称")
    private String serviceName;
}
