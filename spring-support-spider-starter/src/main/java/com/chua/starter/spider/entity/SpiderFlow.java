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
 * 爬虫流程定义（RelationGraph 编排的根）。
 *
 * <p>一个流程 = 一张图 = 多版本 + 历史快照。{@code flow_graph_json} 字段保存
 * RelationGraph 完整 JSON（节点 + 边），既用于前端回填画布，也用于后端拓扑执行。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_flow")
@Schema(description = "爬虫流程定义")
public class SpiderFlow {

    /**
     * 主键，自增
     */
    @TableId(value = "flow_id", type = IdType.AUTO)
    private Long flowId;

    /**
     * 业务编码，全局唯一
     */
    @TableField("flow_code")
    private String flowCode;

    /**
     * 流程名称
     */
    @TableField("flow_name")
    private String flowName;

    /**
     * 当前版本号（每次保存 +1，初始为 1）
     */
    @TableField("flow_version")
    private Integer flowVersion;

    /**
     * RelationGraph 完整 JSON（前端画布的 node/line 全部数据 + 节点 config JSON 序列化）
     */
    @TableField("flow_graph_json")
    private String flowGraphJson;

    /**
     * 节点数（冗余，便于列表展示）
     */
    @TableField("flow_node_count")
    private Integer flowNodeCount;

    /**
     * 状态：0 禁用 / 1 启用
     */
    @TableField("flow_status")
    private Integer flowStatus;

    /**
     * 备注
     */
    @TableField("flow_remark")
    private String flowRemark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
