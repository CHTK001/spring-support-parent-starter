package com.chua.starter.ai.support.model;

import java.io.Serializable;

/**
 * 熔断状态快照（只读视图）。
 *
 * <p>供管理接口/前端展示「哪个供应商的哪个模型正处于熔断中、还要等多久」。
 * 由 {@code ChannelCircuitService.snapshots()} 生成，不参与路由判定。</p>
 *
 * @param providerId       供应商标识
 * @param model            模型名
 * @param state            熔断状态（CLOSED / OPEN / HALF_OPEN）
 * @param consecutiveFailures 当前连续失败次数
 * @param openUntil        熔断冷却截止时间戳（毫秒），仅 OPEN 时有意义
 * @param remainingMillis  剩余冷却毫秒数（0 表示已可试探）
 * @param lastError        最近一次失败原因
 * @param lastUpdatedAt    最近更新时间戳（毫秒）
 * @author CH
 * @since 4.0.0.58
 */
public record ChannelCircuitSnapshot(
        String providerId,
        String model,
        String state,
        int consecutiveFailures,
        long openUntil,
        long remainingMillis,
        String lastError,
        long lastUpdatedAt) implements Serializable {

    /**
     * 熔断条目键（{@code providerId::model}）。
     *
     * @return 键
     */
    public String key() {
        return (providerId == null ? "_" : providerId) + "::" + (model == null ? "_" : model);
    }
}
