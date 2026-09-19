package com.chua.starter.project.support.service;

import com.chua.starter.project.support.entity.MsProject;

/**
 * 项目告警出口桥接
 * <p>
 * 把项目守护/指标结果喂给 monitor 告警引擎（反射查找，宿主未装配
 * monitor-starter 时静默跳过）。项目指标名约定：
 * {@code project.watchdog.fail}（连续失败次数）、
 * {@code project.watchdog.restart.fail}（自动拉起失败次数）。
 * 告警规则/静默/通知渠道在 monitor 侧按指标名配置。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ProjectAlarmHook {

    /**
     * 指标：守护连续失败次数
    */
    String METRIC_WATCHDOG_FAIL = "project.watchdog.fail";

    /**
     * 指标：守护自动拉起失败次数
    */
    String METRIC_WATCHDOG_RESTART_FAIL = "project.watchdog.restart.fail";

    /**
     * 指标：进程内存占用（MB）
    */
    String METRIC_PROCESS_MEM_MB = "project.process.mem.mb";

    /**
     * 指标：进程 CPU 占用（%）
    */
    String METRIC_PROCESS_CPU = "project.process.cpu";

    /**
     * 指标：端口存活（0/1）
    */
    String METRIC_PORT_ALIVE = "project.port.alive";

    /**
     * 指标：采集失败占位（SSH/凭据异常时=1，保证前端面板有数据可展示）
    */
    String METRIC_COLLECT_FAIL = "project.collect.fail";

    /**
     * 评估项目指标（命中告警规则时由 monitor 引擎产出事件并通知）
     *
     * @param project    项目
     * @param metricName 指标名
     * @param value      指标值
     */
    void evaluateProjectMetric(MsProject project, String metricName, double value);
}
