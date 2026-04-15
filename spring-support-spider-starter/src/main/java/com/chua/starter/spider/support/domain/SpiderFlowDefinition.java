package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 爬虫任务编排定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_flow")
public class SpiderFlowDefinition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的任务 ID */
    private Long taskId;

    /** 节点列表（不映射数据库列，由 nodesJson 序列化） */
    @TableField(exist = false)
    private List<SpiderFlowNode> nodes;

    /** 有向边列表（不映射数据库列，由 edgesJson 序列化） */
    @TableField(exist = false)
    private List<SpiderFlowEdge> edges;

    /** 节点列表 JSON 字符串（数据库实际存储列） */
    @TableField("nodes_json")
    private String nodesJson;

    /** 有向边列表 JSON 字符串（数据库实际存储列） */
    @TableField("edges_json")
    private String edgesJson;

    /** 编排版本号 */
    private Integer version;
}
