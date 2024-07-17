package com.chua.starter.monitor.server.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 时间查询
 * @author CH
 * @since 2024/7/16
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(value = "时间查询")
public class TimeQuery extends PageQuery{

    /**
     * 开始时间
     */
    @ApiModelProperty(value = "开始时间")
    private Date startDate;

    /**
     * 结束时间
     */
    @ApiModelProperty(value = "结束时间")
    private Date endDate;
}
