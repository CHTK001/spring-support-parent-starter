package com.chua.starter.ai.support.model;

/**
 * 关系抽取产出的实体。
 *
 * <p><b>刻意不加固（VoAudit 报 {@code NO_COMPACT_CTOR} 属预期）</b>：本 record 是
 * LLM 抽取结果的 JSON 反序列化目标，{@code name} / {@code type} / {@code description} 缺失或为
 * {@code null} 都是模型输出的真实形态，由服务层在落库前做过滤与兜底。若在紧凑构造器里加
 * {@code requireNonNull}，一条脏数据会从「该实体被跳过」变成整次抽取抛
 * {@code NullPointerException}，把可降级的部分失败升级成整批失败。</p>
 *
 * @param name        实体名称，如「关羽」
 * @param type        实体类型：person 人物 / location 地点 / event 事件 / organization 组织 / other 其它
 * @param description 实体在该知识点内的简要说明，可为空
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphEntityDTO(
        String name,
        String type,
        String description
) {
}
