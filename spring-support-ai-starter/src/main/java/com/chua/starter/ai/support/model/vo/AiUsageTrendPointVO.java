package com.chua.starter.ai.support.model.vo;

/**
 * 用量按天趋势点
 *
 * @param date             统计日期（yyyy-MM-dd）
 * @param totalCalls       当日调用次数
 * @param promptTokens     当日输入令牌
 * @param completionTokens 当日输出令牌
 * @param totalTokens      当日令牌合计
 * @param cachedTokens     当日缓存读取令牌
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsageTrendPointVO(
        String date,
        Long totalCalls,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Long cachedTokens
) {
}
