package com.chua.starter.soft.support.model;

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
@ApiModel("软件仓库源")
public class SoftRepositorySource {

    @ApiModelProperty("源名称")
    private String sourceName;

    @ApiModelProperty("源类型")
    private String sourceType;

    @ApiModelProperty("源地址")
    private String sourceUrl;

    @ApiModelProperty("本地目录")
    private String localDirectory;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("源配置JSON")
    private String sourceConfig;
}
