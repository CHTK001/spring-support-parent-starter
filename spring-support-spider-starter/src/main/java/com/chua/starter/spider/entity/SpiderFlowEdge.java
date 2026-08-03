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
 * 流程边（节点之间的连线 + 流转条件）。
 *
 * <p>条件流转：起始节点可能有多条出边，按 {@code flow_edge_priority} 升序求值，
 * 第一个条件成立的边即为下一跳；无条件（condField 为空）的边 = 必走。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_flow_edge")
@Schema(description = "流程边")
public class SpiderFlowEdge {

    @TableId(value = "flow_edge_id", type = IdType.AUTO)
    private Long flowEdgeId;

    @TableField("flow_id")
    private Long flowId;

    @TableField("flow_edge_from")
    private String flowEdgeFrom;

    @TableField("flow_edge_to")
    private String flowEdgeTo;

    @TableField("flow_edge_text")
    private String flowEdgeText;

    @TableField("flow_edge_priority")
    private Integer flowEdgePriority;

    @TableField("flow_edge_cond_field")
    private String flowEdgeCondField;

    @TableField("flow_edge_cond_op")
    private String flowEdgeCondOp;

    @TableField("flow_edge_cond_value")
    private String flowEdgeCondValue;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
