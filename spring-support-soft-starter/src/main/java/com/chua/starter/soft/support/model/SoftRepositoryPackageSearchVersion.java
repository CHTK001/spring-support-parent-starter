package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("仓库软件检索版本项")
public class SoftRepositoryPackageSearchVersion {

    @ApiModelProperty("版本ID")
    private Integer softPackageVersionId;

    @ApiModelProperty("版本编码")
    private String versionCode;

    @ApiModelProperty("版本名称")
    private String versionName;

    @ApiModelProperty("版本软件名称")
    private String packageName;

    @ApiModelProperty("操作系统")
    private String osType;

    @ApiModelProperty("架构")
    private String architecture;

    @ApiModelProperty("来源分类")
    private String sourceKind;

    @ApiModelProperty("安装模式")
    private String installMode;
}

