package com.chua.starter.gen.support.query;

import com.chua.starter.gen.support.entity.SysGenColumn;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * @author CH
 */
@Data
@ApiModel("表字段更新")
public class SysGenColumnUpdate {
    /**
     * 字段
     */
    @ApiModelProperty("字段信息")
    private List<SysGenColumn> columns;
    /**
     * 表ID
     */
    @ApiModelProperty("表ID")
    private String tabId;
}
