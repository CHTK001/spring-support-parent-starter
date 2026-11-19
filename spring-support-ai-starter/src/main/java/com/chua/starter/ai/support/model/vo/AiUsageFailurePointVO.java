package com.chua.starter.ai.support.model.vo;

/**
 * 用量失败分析聚合点（按天 × 调用状态）
 *
 * <p>失败分析卡的数据源：把明细按「天 + 状态」聚合，前端据此画失败率走势、
 * 圈出失败集中日，并与成功行对比定位异常时段。状态取值与明细表一致：
 * {@code success} / {@code failed}，历史空值归一为 {@code success}。</p>
 *
 * @param statDate    统计日期（yyyy-MM-dd）
 * @param status      调用状态（success/failed）
 * @param calls       该日该状态调用次数
 * @param totalTokens 该日该状态令牌合计
 * @param totalCost   该日该状态费用合计
 * @author CH
 * @since 4.0.0.42
 */
public record AiUsageFailurePointVO(
        String statDate,
        String status,
        Long calls,
        Long totalTokens,
        Double totalCost
) {
}
