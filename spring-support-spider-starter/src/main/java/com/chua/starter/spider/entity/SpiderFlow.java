package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程编排实体。
 *
 * <p>对应 {@code spider_flow} 表，保存前端 ReFlow 可视化画布导出的
 * 节点 JSON（nodes_json）与连线 JSON（edges_json），一个爬虫定义对应一条流程。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("spider_flow")
public class SpiderFlow extends SpiderBase {

    /**
     * 关联爬虫定义 ID
     */
    private Long definitionId;

    /**
     * 流程名称
     */
    private String name;

    /**
     * 节点 JSON（ReFlow 导出）
     */
    private String nodesJson;

    /**
     * 连线 JSON（ReFlow 导出）
     */
    private String edgesJson;
}