package com.chua.starter.ai.support.model.vo;

/**
 * 用量按「模型」聚合的 TopN 行。
 *
 * <p>用量页的「模型 TopN」榜单用它回答「这段时间哪些模型吃掉了最多用量」：
 * 服务端按模型 GROUP BY 后按指定口径（Token / 费用 / 次数）排序取前 N 名，
 * 并附上每个模型占筛选范围全量的百分比 —— 只看榜单内的占比会高估头部模型，
 * 占全量的比例才能直接读出「第一名吃掉了多少」。</p>
 *
 * @param model            模型名称（空值归一为「未知」）
 * @param calls            调用次数
 * @param promptTokens     输入令牌合计
 * @param completionTokens 输出令牌合计
 * @param totalTokens      令牌合计
 * @param totalCost        费用合计
 * @param sharePct         占筛选范围全量的百分比（按排序口径计，保留 1 位小数；全量为 0 时为 null）
 * @author CH
 * @since 4.0.0.53
 */
public record AiUsageModelTopVO(
        String model,
        Long calls,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Double totalCost,
        Double sharePct
) {
}
