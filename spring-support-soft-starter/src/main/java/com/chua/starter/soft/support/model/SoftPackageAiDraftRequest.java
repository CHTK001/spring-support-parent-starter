package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("软件新增 AI 草稿请求")
public class SoftPackageAiDraftRequest {

    @ApiModelProperty("自然语言描述")
    private String prompt;

    @ApiModelProperty("软件名称提示")
    private String packageName;

    @ApiModelProperty("软件编码提示")
    private String packageCode;

    @ApiModelProperty("软件分类提示")
    private String packageCategory;

    @ApiModelProperty("操作系统提示")
    private String osType;

    @ApiModelProperty("架构提示")
    private String architecture;

    @ApiModelProperty("版本提示")
    private String versionCode;

    @ApiModelProperty("是否需要服务化接入")
    private Boolean integrateServerService;
}
