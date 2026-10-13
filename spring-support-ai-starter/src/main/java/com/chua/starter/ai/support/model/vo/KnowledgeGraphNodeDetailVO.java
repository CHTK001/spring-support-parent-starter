package com.chua.starter.ai.support.model.vo;

import java.util.Map;

/**
 * 知识图谱实体详情，供右侧详情面板懒加载。
 *
 * <p>{@code properties} 是实体扩展属性的 JSON 对象（由
 * {@code sys_ai_knowledge_graph_entity_properties} 解析而来），缺省返回空对象；
 * {@code relationCount} 表示该实体在当前知识库中的关系条数，与视图中的 degree 口径一致。</p>
 *
 * @param id          实体 uid
 * @param text        实体名称
 * @param type        实体类型（person / location / event / organization / other）
 * @param color       实体颜色
 * @param weight      实体权重
 * @param source      来源（manual / llm_extracted / imported）
 * @param image       实体图片地址，可为 null
 * @param description 实体说明，可为 null
 * @param properties  扩展属性，可为空 Map
 * @param relationCount 关系条数
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphNodeDetailVO(
        String id,
        String text,
        String type,
        String color,
        Double weight,
        String source,
        String image,
        String description,
        Map<String, Object> properties,
        int relationCount
) {
}
