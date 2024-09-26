package com.chua.report.server.starter.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 分页查询
 * @author CH
 * @since 2024/7/16
 */
@Data
@ApiModel(value = "分页查询")
public class PageQuery {

    /**
     * 页码
     */
    @ApiModelProperty(value = "页码")
    private Integer page = 1;

    /**
     * 每页数量
     */
    @ApiModelProperty(value = "每页数量")
    private Integer size = 10;
}
