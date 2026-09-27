package com.chua.starter.monitor.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Agent 配置视图：采集间隔与该资产下的采集任务
 *
 * @param collectIntervalMs 采集间隔（毫秒）
 * @param tasks             采集任务列表，永不为 空
 * @author CH
 * @since 4.0.0.42
 */
public record AgentConfigVO(Long collectIntervalMs, List<AgentTaskVO> tasks) {

    /**
     * 规范构造器：对采集任务列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。任务列表由服务端逐条装配，
     * 恒非空且元素不会为 null，故用 {@link List#copyOf}。</p>
     *
     * @param collectIntervalMs 采集间隔（毫秒）
     * @param tasks             采集任务列表，永不为空
     */
    public AgentConfigVO {
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks 不能为 null"));
    }
}
