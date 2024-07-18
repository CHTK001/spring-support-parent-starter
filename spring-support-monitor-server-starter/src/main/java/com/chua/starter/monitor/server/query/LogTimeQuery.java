package com.chua.starter.monitor.server.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日志查询
 * @author CH
 * @since 2024/7/16
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(value = "时间查询")
public class LogTimeQuery extends TimeQuery{

    /**
     * 表名
     */
    @ApiModelProperty("表名")
    private String tableName;

    /**
     * 操作类型
     */
    @ApiModelProperty("操作类型")
    private String action;
    /**
     * 关键词
     */
    @ApiModelProperty("关键词")
    private String keyword;
}
