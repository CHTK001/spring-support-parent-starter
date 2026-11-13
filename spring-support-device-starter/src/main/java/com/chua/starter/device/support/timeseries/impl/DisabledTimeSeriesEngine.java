package com.chua.starter.device.support.timeseries.impl;

import com.chua.starter.device.support.timeseries.DeviceSeriesPoint;
import com.chua.starter.device.support.timeseries.DeviceSeriesQuery;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 不可用时序引擎的占位实现。
 *
 * <p>为什么不是「不注册 Bean」：业务侧注入时序引擎的位置很多，缺 Bean 会让整个模块
 * 启动失败；而 {@code engine=none} 或宿主缺 redis 都是**合法配置**，只是没有时序存储。
 * 用一个明确说「不可用 + 原因」的实现兜底，读写都走空语义，页面能如实展示原因。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
public class DisabledTimeSeriesEngine implements DeviceTimeSeriesEngine {

    /**
     * 引擎标识
     */
    private final String engine;

    /**
     * 不可用原因
     */
    private final String reason;

    /**
     * 构造占位引擎。
     *
     * @param engine 引擎标识（{@code none} / {@code redis}）
     * @param reason 不可用原因
     */
    public DisabledTimeSeriesEngine(String engine, String reason) {
        this.engine = engine;
        this.reason = reason;
        log.info("[Device][Series] 时序引擎 {} 未启用: {}", engine, reason);
    }

    @Override
    public String engine() {
        return engine;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return reason;
    }

    @Override
    public void write(DeviceSeriesPoint point) {
        throw new IllegalStateException("时序引擎不可用：" + reason);
    }

    @Override
    public int writeBatch(List<DeviceSeriesPoint> points) {
        throw new IllegalStateException("时序引擎不可用：" + reason);
    }

    @Override
    public List<DeviceSeriesPoint> query(DeviceSeriesQuery query) {
        return List.of();
    }

    @Override
    public long count(DeviceSeriesQuery query) {
        return 0L;
    }

    @Override
    public int purge(String category, LocalDateTime before) {
        return 0;
    }
}
