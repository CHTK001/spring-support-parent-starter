package com.chua.starter.project.support.service;

import com.chua.starter.project.support.entity.MsProject;

import java.util.List;
import java.util.Map;

/**
 * 项目指标采集服务（SSH 轮询，零 Agent）
 * <p>
 * 由时间轮统一调度，按各项目 {@code msProjectMetricInterval} 周期
 * 经 SSH 采集进程 CPU/内存、端口存活与 HTTP 健康码，结果写
 * {@code ms_project_metric} 时间序列表并经 socket 推送
 * {@code project-metric} 事件；同时喂给告警引擎评估规则。
 * 采集窗口内单主机失败不影响其它主机，全部异常静默降级。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ProjectMetricService {

    /**
     * 全量启用指标采集的项目（启动时批量入轮用）
     *
     * @return 项目列表
     */
    List<MsProject> enabledProjects();

    /**
     * 重新加载项目并校验指标采集启用状态
     *
     * @param projectId 项目 标识
     * @return 启用采集的项目，未启用/不存在返回 空
     */
    MsProject reloadEnabled(Long projectId);

    /**
     * 项目指标采集是否启用
     *
     * @param projectId 项目 标识
     * @return 是否启用
     */
    boolean isEnabled(Long projectId);

    /**
     * 项目指标采集间隔（秒，归一化后：空/小于 5 按 60）
     *
     * @param projectId 项目 标识
     * @return 间隔秒数
     */
    int metricIntervalOf(Long projectId);

    /**
     * 对单个项目执行一轮指标采集（时间轮到期回调）
     *
     * @param project 项目
     */
    void collectOne(MsProject project);

    /**
     * 查询项目指标时间序列（供前端图表拉历史）
     *
     * @param projectId  项目 标识
     * @param metricName 指标名，可为空查全部
     * @param limit      上限，默认 200
     * @return 指标点列表（新→旧）
     */
    List<Map<String, Object>> queryHistory(Long projectId, String metricName, int limit);
}
