package com.chua.starter.soft.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("软件日志返回")
public class SoftLogResponse {

    @ApiModelProperty("日志路径")
    private String logPath;

    @ApiModelProperty("日志内容")
    private List<String> lines;
}
