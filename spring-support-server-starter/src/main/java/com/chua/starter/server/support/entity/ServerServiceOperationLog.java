package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@ApiModel("服务器服务操作日志")
@TableName("server_service_operation_log")
public class ServerServiceOperationLog extends SysBase {

    @TableId(value = "server_service_operation_log_id", type = IdType.AUTO)
    @ApiModelProperty("服务器服务操作日志ID")
    private Integer serverServiceOperationLogId;

    @TableField("server_service_id")
    @ApiModelProperty("服务器服务ID")
    private Integer serverServiceId;

    @TableField("server_id")
    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @TableField("server_operation_type")
    @ApiModelProperty("操作类型")
    private String operationType;

    @TableField("server_operation_success")
    @ApiModelProperty("是否成功")
    private Boolean success;

    @TableField("server_exit_code")
    @ApiModelProperty("退出码")
    private Integer exitCode;

    @TableField("server_runtime_status")
    @ApiModelProperty("运行状态")
    private String runtimeStatus;

    @TableField("server_operation_message")
    @ApiModelProperty("操作说明")
    private String operationMessage;

    @TableField("server_operation_output")
    @ApiModelProperty("操作输出")
    private String operationOutput;

    @TableField("server_ai_reason")
    @ApiModelProperty("AI失败原因")
    private String aiReason;

    @TableField("server_ai_solution")
    @ApiModelProperty("AI处理方案")
    private String aiSolution;

    @TableField("server_ai_fix_script")
    @ApiModelProperty("AI修复脚本")
    private String aiFixScript;

    @TableField("server_ai_provider")
    @ApiModelProperty("AI提供商")
    private String aiProvider;

    @TableField("server_ai_model")
    @ApiModelProperty("AI模型")
    private String aiModel;

    @TableField("server_knowledge_id")
    @ApiModelProperty("知识库ID")
    private Integer knowledgeId;

    @TableField("server_expire_at")
    @ApiModelProperty("过期时间")
    private LocalDateTime expireAt;
}
