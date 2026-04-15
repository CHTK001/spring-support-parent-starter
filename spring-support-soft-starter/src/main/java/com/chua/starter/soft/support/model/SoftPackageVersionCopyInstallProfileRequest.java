package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("复制版本安装方式请求")
public class SoftPackageVersionCopyInstallProfileRequest {

    @ApiModelProperty("来源版本ID")
    private Integer sourceVersionId;

    @ApiModelProperty("是否复制下载地址")
    private Boolean copyDownloadUrls;
}

