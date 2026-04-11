package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("配置写入请求")
public class SoftConfigWriteRequest {

    @ApiModelProperty("配置路径")
    private String configPath;

    @ApiModelProperty("配置内容")
    private String configContent;

    @ApiModelProperty("快照名称")
    private String snapshotName;

    @ApiModelProperty("备注")
    private String operationRemark;
}
