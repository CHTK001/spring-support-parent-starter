package com.chua.starter.ai.support.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个知识点的大模型关系抽取结果。
 *
 * @param entities  抽出的实体，可能为空
 * @param relations 抽出的关系，可能为空
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphExtractionDTO(
        List<KnowledgeGraphEntityDTO> entities,
        List<KnowledgeGraphRelationDTO> relations
) {

    /**
     * 紧凑构造器：对列表做防御性拷贝，但保留 null 语义。
     *
     * <p>大模型偶尔省略 entities / relations 字段，或数组里带 null 元素，这都属于
     * 「该段落没抽出东西」的正常业务状态；用 {@code List.copyOf} 会把 null 元素变成
     * NPE，所以这里用允许 null 元素且不可变的包装，并且不加 requireNonNull。</p>
     */
    public KnowledgeGraphExtractionDTO {
        entities = entities == null ? null : Collections.unmodifiableList(new ArrayList<>(entities));
        relations = relations == null ? null : Collections.unmodifiableList(new ArrayList<>(relations));
    }

    /**
     * 是否没有任何抽取结果。
     *
     * @return 实体与关系都为空时返回 true
     */
    public boolean isEmpty() {
        return (entities == null || entities.isEmpty()) && (relations == null || relations.isEmpty());
    }

    /**
     * 空结果常量。
     *
     * @return 不含任何实体与关系的结果
     */
    public static KnowledgeGraphExtractionDTO empty() {
        return new KnowledgeGraphExtractionDTO(List.of(), List.of());
    }
}
