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
 * @param totalCost        当日费用合计；按天趋势读日汇总表，小时趋势无费用恒 0.0
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsageTrendPointVO(
        String date,
        Long totalCalls,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Long cachedTokens,
        Double totalCost
) {
}
