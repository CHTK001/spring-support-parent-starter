package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("软件配置返回")
public class SoftConfigResponse {

    @ApiModelProperty("配置路径")
    private String configPath;

    @ApiModelProperty("配置内容")
    private String configContent;

    @ApiModelProperty("可用配置路径")
    private List<String> availableConfigPaths;
}
