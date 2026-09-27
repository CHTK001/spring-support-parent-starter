package com.chua.starter.ai.support.model.vo;

/**
 * 用量汇总统计
 *
 * @param totalCalls             调用次数
 * @param totalPromptTokens      输入令牌
 * @param totalCompletionTokens  输出令牌
 * @param totalTokens            令牌合计
 * @param totalCost              实际费用
 * @param totalCachedTokens      缓存读取令牌
 * @param avgLatency             平均耗时（毫秒）
 * @param avgFirstTokenLatency   平均首字延迟（毫秒）
 * @param standardCost           原价费用（未折扣）
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsageStatisticsVO(
        Long totalCalls,
        Long totalPromptTokens,
        Long totalCompletionTokens,
        Long totalTokens,
        Double totalCost,
        Long totalCachedTokens,
        Double avgLatency,
        Double avgFirstTokenLatency,
        Double standardCost
) {
}
