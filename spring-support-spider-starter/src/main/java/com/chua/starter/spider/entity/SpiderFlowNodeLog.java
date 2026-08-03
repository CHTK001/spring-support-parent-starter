package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点执行日志（用于前端调试面板回放）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_flow_node_log")
@Schema(description = "流程节点执行日志")
public class SpiderFlowNodeLog {

    @TableId(value = "flow_node_log_id", type = IdType.AUTO)
    private Long flowNodeLogId;

    @TableField("flow_execution_no")
    private String flowExecutionNo;

    @TableField("flow_id")
    private Long flowId;

    @TableField("flow_node_key")
    private String flowNodeKey;

    @TableField("flow_node_type")
    private String flowNodeType;

    @TableField("flow_node_status")
    private String flowNodeStatus;

    @TableField("flow_node_message")
    private String flowNodeMessage;

    @TableField("flow_node_cost_ms")
    private Long flowNodeCostMs;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
