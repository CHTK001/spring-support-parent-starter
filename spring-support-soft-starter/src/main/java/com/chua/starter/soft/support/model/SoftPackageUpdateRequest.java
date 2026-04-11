package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("软件主档轻量整理请求")
public class SoftPackageUpdateRequest {

    @ApiModelProperty("软件名称")
    private String packageName;

    @ApiModelProperty("分类")
    private String packageCategory;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("图标")
    private String iconUrl;

    @ApiModelProperty("画像编码")
    private String profileCode;
}
