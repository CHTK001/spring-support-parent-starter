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
 * 流程节点。
 *
 * <p>把 RelationGraph 的节点拆出来持久化，便于：</p>
 * <ul>
 *   <li>执行时按节点类型路由到对应的 {@code NodeHandler}</li>
 *   <li>执行日志可按节点关联</li>
 *   <li>后续扩展：节点级别的启停、版本对比</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_flow_node")
@Schema(description = "流程节点")
public class SpiderFlowNode {

    @TableId(value = "flow_node_id", type = IdType.AUTO)
    private Long flowNodeId;

    @TableField("flow_id")
    private Long flowId;

    /**
     * 节点 key（与 RelationGraph 的 node.id 同源，全流程内唯一）
     */
    @TableField("flow_node_key")
    private String flowNodeKey;

    /**
     * 节点类型：start / request / extract / loop / output / script / db / sql / function / stop
     */
    @TableField("flow_node_type")
    private String flowNodeType;

    /**
     * 节点显示名
     */
    @TableField("flow_node_name")
    private String flowNodeName;

    @TableField("flow_node_x")
    private Integer flowNodeX;

    @TableField("flow_node_y")
    private Integer flowNodeY;

    /**
     * 节点配置 JSON（结构因节点类型而异，详见 NodeType 常量定义）
     */
    @TableField("flow_node_config")
    private String flowNodeConfig;

    @TableField("flow_node_status")
    private Integer flowNodeStatus;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
