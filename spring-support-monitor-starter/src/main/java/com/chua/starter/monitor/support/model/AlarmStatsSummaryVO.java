package com.chua.starter.monitor.support.model;

import java.util.Map;
import java.util.Objects;

/**
 * 告警概览统计
 *
 * @param todayTotal        今日触发总数
 * @param unprocessed       未处理数（NEW）
 * @param processed         已处理数
 * @param falseAlarm        误报数
 * @param levelDistribution 级别分布，键为告警级别（P0~P4），值为该级别事件数
 * @author CH
 * @since 4.0.0.42
 */
public record AlarmStatsSummaryVO(
        long todayTotal,
        long unprocessed,
        long processed,
        long falseAlarm,
        Map<String, Long> levelDistribution
) {
    /**
     * 规范构造器：对级别分布做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。级别分布的键来自告警级别文本、
     * 值来自计数聚合，两者均不为 null，因此可使用拒绝 null 的 {@link Map#copyOf}。</p>
     *
     * @param levelDistribution 级别分布，键为告警级别（P0~P4），值为该级别事件数
     */
    public AlarmStatsSummaryVO {
        levelDistribution = Map.copyOf(
                Objects.requireNonNull(levelDistribution, "levelDistribution 不能为 null"));
    }
}
