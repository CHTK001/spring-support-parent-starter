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
@ApiModel("软件下载源检索结果")
public class SoftRepositorySourceSearchItem {

    @ApiModelProperty("源ID")
    private Integer softRepositorySourceId;

    @ApiModelProperty("仓库ID")
    private Integer repositoryId;

    @ApiModelProperty("仓库名称")
    private String repositoryName;

    @ApiModelProperty("仓库编码")
    private String repositoryCode;

    @ApiModelProperty("仓库类型")
    private String repositoryType;

    @ApiModelProperty("是否主源")
    private Boolean primarySource;

    @ApiModelProperty("源名称")
    private String sourceName;

    @ApiModelProperty("来源分类")
    private String sourceKind;

    @ApiModelProperty("源类型")
    private String sourceType;

    @ApiModelProperty("源地址")
    private String sourceUrl;

    @ApiModelProperty("本地目录")
    private String localDirectory;

    @ApiModelProperty("显示地址")
    private String sourceAddress;

    @ApiModelProperty("是否启用")
    private Boolean enabled;
}
