package com.chua.starter.ai.support.model.vo;

/**
 * 用量「按天 × 按模型」趋势点。
 *
 * <p>用途：用量管理的「每日模型用量」堆叠柱状图。一天一个柱，柱内按模型分段，
 * 每个模型一种颜色、分段高度按当日该模型的 Token / 费用占比确定。
 * 之所以要单开一个出口而不能复用 {@link AiUsageTrendPointVO}：
 * 后者按天把全部模型合并成一行，拿不到「某天各模型分别多少」这层维度。</p>
 *
 * @param date             统计日期（yyyy-MM-dd）
 * @param model            模型标识（为空时前端按「未知模型」归并）
 * @param provider         服务商标识
 * @param totalCalls       当日该模型调用次数
 * @param totalTokens      当日该模型令牌合计
 * @param totalCost        当日该模型费用
 * @param avgFirstTokenLatency 当日该模型平均首字延迟（毫秒；无实测样本时为 null）
 * @param avgTotalLatency  当日该模型平均总耗时（毫秒；无实测样本时为 null）
 * @author CH
 * @since 4.0.0.44
 */
public record AiUsageModelTrendPointVO(
        String date,
        String model,
        String provider,
        Long totalCalls,
        Long totalTokens,
        Double totalCost,
        Double avgFirstTokenLatency,
        Double avgTotalLatency
) {
}
