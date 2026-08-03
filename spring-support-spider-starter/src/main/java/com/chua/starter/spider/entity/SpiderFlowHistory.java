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
 * 流程历史快照（每次保存生成一条）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_flow_history")
@Schema(description = "流程历史版本")
public class SpiderFlowHistory {

    @TableId(value = "flow_history_id", type = IdType.AUTO)
    private Long flowHistoryId;

    @TableField("flow_id")
    private Long flowId;

    @TableField("flow_version")
    private Integer flowVersion;

    @TableField("flow_graph_json")
    private String flowGraphJson;

    @TableField("flow_change_note")
    private String flowChangeNote;

    @TableField("flow_change_user")
    private String flowChangeUser;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
