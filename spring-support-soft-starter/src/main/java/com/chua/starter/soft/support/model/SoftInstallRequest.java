package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import lombok.Data;

@Data
@ApiModel("软件安装请求")
public class SoftInstallRequest {

    @ApiModelProperty("软件ID")
    private Integer softPackageId;

    @ApiModelProperty("软件版本ID")
    private Integer softPackageVersionId;

    @ApiModelProperty("目标ID")
    private Integer softTargetId;

    @ApiModelProperty("实例名称")
    private String installationName;

    @ApiModelProperty("安装路径")
    private String installPath;

    @ApiModelProperty("服务名称")
    private String serviceName;

    @ApiModelProperty("安装引导参数")
    private Map<String, Object> installOptions;

    @ApiModelProperty("服务引导参数")
    private Map<String, Object> serviceOptions;

    @ApiModelProperty("配置引导参数")
    private Map<String, Object> configOptions;
}
