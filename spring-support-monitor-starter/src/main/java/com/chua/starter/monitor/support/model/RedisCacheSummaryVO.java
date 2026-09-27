package com.chua.starter.monitor.support.model;

/**
 * Redis 缓存前缀汇总条目
 *
 * @param prefix 前缀模式，含冒号分隔的首段以 * 结尾
 * @param count  该前缀下的键数量
 * @author CH
 * @since 4.0.0.42
 */
public record RedisCacheSummaryVO(
        String prefix,
        int count
) {
}
