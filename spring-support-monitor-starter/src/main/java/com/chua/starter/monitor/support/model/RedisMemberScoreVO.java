package com.chua.starter.monitor.support.model;

/**
 * Redis 有序集合成员
 *
 * @param member 成员值
 * @param score  排序分值
 * @author CH
 * @since 4.0.0.42
 */
public record RedisMemberScoreVO(
        String member,
        double score
) {
}
