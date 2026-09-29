package com.chua.starter.ai.support.model;

/**
 * 关系抽取产出的关系。
 *
 * <p>源/目标用实体名称而非 uid：抽取阶段实体 uid 还没分配，落库时由
 * 服务层按名称匹配（或先建）实体后再换成 uid。</p>
 *
 * <p><b>刻意不加固（VoAudit 报 {@code NO_COMPACT_CTOR} 属预期）</b>：与
 * {@link KnowledgeGraphEntityDTO} 同理，这是 LLM 抽取结果的 JSON 反序列化目标。
 * 模型漏给 {@code type}、或把 {@code source} 写成空串都是真实存在的输出形态，
 * 由服务层过滤；此处加 {@code requireNonNull} 只会让一条脏关系炸掉整次抽取。</p>
 *
 * @param source 源实体名称
 * @param target 目标实体名称
 * @param type   关系类型，如「镇守」「结义」「被封为」
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphRelationDTO(
        String source,
        String target,
        String type
) {
}
