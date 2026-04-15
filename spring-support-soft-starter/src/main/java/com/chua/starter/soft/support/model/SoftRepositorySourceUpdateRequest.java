package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@ApiModel("软件下载源更新请求")
public class SoftRepositorySourceUpdateRequest {

    @ApiModelProperty("主源地址")
    private String repositoryUrl;

    @ApiModelProperty("主源本地目录")
    private String localDirectory;

    @ApiModelProperty("检索源列表")
    private List<SoftRepositorySource> sourceConfigs = new ArrayList<>();
}
