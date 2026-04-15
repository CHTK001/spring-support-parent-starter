package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("仓库软件检索结果")
public class SoftRepositoryPackageSearchItem {

    @ApiModelProperty("仓库ID")
    private Integer repositoryId;

    @ApiModelProperty("仓库名称")
    private String repositoryName;

    @ApiModelProperty("仓库编码")
    private String repositoryCode;

    @ApiModelProperty("软件ID")
    private Integer softPackageId;

    @ApiModelProperty("软件编码")
    private String packageCode;

    @ApiModelProperty("软件名称")
    private String packageName;

    @ApiModelProperty("版本列表")
    @Builder.Default
    private List<SoftRepositoryPackageSearchVersion> versions = new ArrayList<>();
}

