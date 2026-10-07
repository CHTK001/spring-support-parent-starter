package com.chua.starter.ai.support.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 聚合行组件类型守卫。
 *
 * <p>SUM() 在 MySQL 里返回 DECIMAL，行组件必须吃得下超过 {@code Integer.MAX}（2147483647）的量：
 * {@code UsageStatsRow} 曾把调用数/令牌列映射成 Integer，累计量到 13,160,402,810 时驱动报
 * {@code NumberOutOfRange}，把 {@code /v2/usage/statistics} 打成 500。所有聚合行的计数/令牌
 * 列统一为 Long，本用例锁住该约束，防止改回 Integer 复发。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class SysAiUsageMapperRowTypeTest {

    /**
     * 统计行（/v2/usage/statistics 普通用户口径）：计数与令牌列必须为 Long。
     */
    @Test
    @DisplayName("统计行计数/令牌列为 Long")
    void usageStatsRowCountsAreLong() {
        assertComponents(SysAiUsageMapper.UsageStatsRow.class,
                "totalCalls", "totalPromptTokens", "totalCompletionTokens",
                "totalTokens", "totalCachedTokens");
    }

    /**
     * 日/时/分组汇总行：同为 SUM 出数，保持 Long 不回退。
     */
    @Test
    @DisplayName("日/时/汇总聚合行计数列保持 Long")
    void otherAggregateRowsStayLong() {
        assertComponents(SysAiUsageMapper.DailyAggregateRow.class,
                "totalCalls", "totalPromptTokens", "totalCompletionTokens",
                "totalTokens", "totalCachedTokens", "totalLatency", "totalFirstTokenLatency");
        assertComponents(SysAiUsageMapper.HourlyAggregateRow.class,
                "totalCalls", "totalPromptTokens", "totalCompletionTokens",
                "totalTokens", "totalCachedTokens");
        assertComponents(SysAiUsageMapper.UsageSummaryRow.class,
                "totalCalls", "totalPromptTokens", "totalCompletionTokens", "totalTokens");
    }

    /**
     * 断言指定组件均为 Long。
     *
     * @param row   聚合行记录类型
     * @param names 必须为 Long 的组件名
     */
    private void assertComponents(Class<?> row, String... names) {
        RecordComponent[] components = row.getRecordComponents();
        for (String name : names) {
            RecordComponent match = Arrays.stream(components)
                    .filter(c -> c.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(row.getSimpleName() + " 缺少组件 " + name));
            assertEquals(Long.class, match.getType(),
                    row.getSimpleName() + "." + name + " 必须是 Long，SUM 结果会超过 Integer.MAX");
        }
    }
}
