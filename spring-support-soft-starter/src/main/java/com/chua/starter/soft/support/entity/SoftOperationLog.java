package com.chua.starter.soft.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("软件操作日志")
@TableName("soft_operation_log")
public class SoftOperationLog extends SysBase {

    @TableId(value = "soft_operation_log_id", type = IdType.AUTO)
    @ApiModelProperty("操作日志ID")
    private Integer softOperationLogId;

    @ApiModelProperty("安装ID")
    private Integer softInstallationId;

    @ApiModelProperty("目标ID")
    private Integer softTargetId;

    @ApiModelProperty("版本ID")
    private Integer softPackageVersionId;

    @ApiModelProperty("操作类型")
    private String operationType;

    @ApiModelProperty("操作状态")
    private String operationStatus;

    @ApiModelProperty("执行命令")
    private String operationCommand;

    @ApiModelProperty("执行说明")
    private String operationMessage;

    @ApiModelProperty("执行阶段")
    private String operationStage;

    @ApiModelProperty("执行进度")
    private Integer progressPercent;

    @ApiModelProperty("详细说明")
    private String detailMessage;

    @ApiModelProperty("执行输出")
    private String operationOutput;

    @ApiModelProperty("模板摘要JSON")
    private String templateSummaryJson;

    @ApiModelProperty("参数摘要JSON")
    private String parameterSummaryJson;

    @ApiModelProperty("开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("结束时间")
    private LocalDateTime endTime;
}
