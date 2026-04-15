package com.chua.starter.spider.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重试策略值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy {

    /** 最大重试次数 */
    private int maxRetries;

    /** 重试间隔（毫秒） */
    private long retryIntervalMs;
}
