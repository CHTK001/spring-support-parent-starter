package com.chua.starter.ai.support.model;

/**
 * 单次关系抽取的带状态结果。
 *
 * <p>{@link KnowledgeGraphExtractionDTO#empty()} 与「抽取失败」此前无法区分：
 * 没有可用的大模型、调用抛异常、模型返回空数组，三者在调用方看来都是「0 实体 0 关系」，
 * 页面于是把「模型没配」报成「生成成功，0 实体」。这里把状态与原因显式带出来。</p>
 *
 * @param status      抽取状态：ok（有结果）/ empty（模型确实没抽到）/ failed（链路失败）
 * @param errorMessage 失败原因；status 为 failed 时非空
 * @param extraction  抽取结果，永不为 null
 * @author CH
 * @since 4.0.0.51
 */
public record KnowledgeGraphExtractionOutcome(
        String status,
        String errorMessage,
        KnowledgeGraphExtractionDTO extraction
) {

    /**
     * 抽取成功且有结果。
     */
    public static final String STATUS_OK = "ok";
    /**
     * 抽取链路可用，但模型没抽到实体关系。
     */
    public static final String STATUS_EMPTY = "empty";
    /**
     * 抽取链路不可用（无模型、调用异常、返回不可解析）。
     */
    public static final String STATUS_FAILED = "failed";

    /**
     * 紧凑构造器：抽取结果缺失时降级为空结果，避免调用方到处判空。
     */
    public KnowledgeGraphExtractionOutcome {
        extraction = extraction == null ? KnowledgeGraphExtractionDTO.empty() : extraction;
    }

    /**
     * 构造有结果的成功响应。
     *
     * @param extraction 抽取结果
     * @return 状态为 ok 的结果
     */
    public static KnowledgeGraphExtractionOutcome ok(KnowledgeGraphExtractionDTO extraction) {
        return new KnowledgeGraphExtractionOutcome(STATUS_OK, null, extraction);
    }

    /**
     * 构造「模型可用但没抽到内容」的响应。
     *
     * @param extraction 空结果
     * @return 状态为 empty 的结果
     */
    public static KnowledgeGraphExtractionOutcome empty(KnowledgeGraphExtractionDTO extraction) {
        return new KnowledgeGraphExtractionOutcome(STATUS_EMPTY, null, extraction);
    }

    /**
     * 构造失败响应。
     *
     * @param errorMessage 失败原因，必须能指导用户下一步动作
     * @return 状态为 failed 的结果
     */
    public static KnowledgeGraphExtractionOutcome failed(String errorMessage) {
        return new KnowledgeGraphExtractionOutcome(STATUS_FAILED, errorMessage, null);
    }

    /**
     * 是否为链路失败。
     *
     * @return 失败返回 true
     */
    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }
}
