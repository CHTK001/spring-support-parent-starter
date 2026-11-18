package com.chua.starter.ai.support.model.vo;

/**
 * 用量按「调用类型」聚合点。
 *
 * <p>一次会话里的模型调用并不只有对话：工具执行（{@code local-tool}）、
 * 记忆抽取（{@code memory-extract}）、定时同步进来的外部工具用量（{@code external}）
 * 都各自落成明细行。把它们分开看，才能回答「这一段时间的 token 到底花在哪」——
 * 只看总量时工具与记忆的消耗会被当成对话消耗，用户会以为模型「自己变贵了」。</p>
 *
 * @param callType         调用类型（{@code chat}/{@code external}/{@code local-tool}/{@code memory-extract}）
 * @param calls            调用次数
 * @param promptTokens     输入令牌合计
 * @param completionTokens 输出令牌合计
 * @param totalTokens      令牌合计
 * @param totalCost        费用合计
 * @author CH
 * @since 4.0.0.53
 */
public record AiUsageCallTypePointVO(
        String callType,
        Long calls,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Double totalCost
) {
}
