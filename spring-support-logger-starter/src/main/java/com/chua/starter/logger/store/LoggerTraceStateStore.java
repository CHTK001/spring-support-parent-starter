package com.chua.starter.logger.store;

import java.util.List;

/**
 * SDK trace 生命周期状态的持久化契约。
 *
 * <p>该状态不应保存在服务端 Java 堆中：浏览器客户端和并发请求数量增长时，内存
 * Map 会持续膨胀。实现必须把 trace 登记、阶段位和频控状态写入持久化介质，并以
 * 有界批次完成管理查询。</p>
 */
public interface LoggerTraceStateStore {

    /**
     * 读取已登记的 trace。
     *
     * @param traceId trace 标识
     * @param now 当前时间
     * @return 状态，不存在或过期时返回 null
     */
    TraceState getTrace(String traceId, long now);

    /**
     * 仅在 trace 尚不存在时创建登记。
     *
     * @param state trace 状态
     * @return 成功创建返回 true，已存在返回 false
     */
    boolean createTrace(TraceState state);

    /**
     * 更新 trace 阶段位。
     *
     * @param traceId trace 标识
     * @param expectedPhases 期望的旧阶段位
     * @param phases 新阶段位
     * @return 更新成功返回 true
     */
    boolean updateTracePhases(String traceId, int expectedPhases, int phases);

    /**
     * 读取用户+应用组合的频控状态。
     *
     * @param userId 用户标识
     * @param app 应用标识
     * @param now 当前时间
     * @return 状态，不存在或过期时返回 null
     */
    RateState getRate(String userId, String app, long now);

    /**
     * 保存用户+应用组合的频控状态。
     *
     * @param state 频控状态
     */
    void saveRate(RateState state);

    /**
     * 分批读取当前仍被封锁的身份，返回数量有界。
     *
     * @param now 当前时间
     * @param limit 最大返回数
     * @return 封锁状态
     */
    List<RateState> findBlocked(long now, int limit);

    /**
     * 按应用或用户清除封锁状态；实现必须流式扫描，不能把所有客户端载入内存。
     *
     * @param scope app 或 user
     * @param value 应用名或用户标识
     * @param now 当前时间
     * @return 清除数量
     */
    int clearBlocked(String scope, String value, long now);

    /** 请求级 trace 状态。 */
    record TraceState(String traceId, String userId, String app, long createdAt, int phases) {
    }

    /** 用户+应用维度的频控状态。 */
    record RateState(String userId, String app, long windowStart, int count,
                     long blockedUntil, long updatedAt) {
    }
}
