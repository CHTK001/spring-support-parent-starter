package com.chua.starter.collect.store;

import com.chua.starter.collect.engine.CollectRecordEntry;
import com.chua.starter.collect.engine.CollectSink;
import com.chua.starter.collect.engine.model.CollectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 只跑流程、不落结果的空通道。
 *
 * <p>{@code plugin.collect.persistence.type=none} 时由默认工厂启用：
 * 节点日志与采集结果全部丢弃，仅保留调试 trace 的日志输出，
 * 便于在共用环境试跑流程而不产生脏数据。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class CollectNoopSink implements CollectSink {

    @Override
    public void nodeLog(CollectNode node, int iteration, String status, long costMs, String message, String preview) {
        // 持久化关闭：丢弃节点日志
    }

    @Override
    public void record(CollectRecordEntry entry) {
        // 持久化关闭：丢弃采集结果
    }

    @Override
    public void trace(String message) {
        log.info("[collect] {}", message);
    }
}
