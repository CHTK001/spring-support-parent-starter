package com.chua.starter.project.support.service;

import com.chua.starter.project.support.entity.MsProject;

import java.util.List;
import java.util.Map;

/**
 * 守护进程服务（持续存活检测 + 自动拉起）
 * <p>
 * 由时间轮 {@code ProjectWatchdogWheel} 按各项目
 * {@code msProjectWatchdogInterval} 周期触发；
 * 检测优先执行自定义 {@code msProjectCheckScript}（exit 0=健康），
 * 未配置时回退项目健康检查类型（PORT/HTTP/PROCESS）探测。
 * 连续失败达到阈值时自动执行重启脚本拉起，全程写
 * {@code ms_project_deploy_log}（action=WATCHDOG）留痕并经 socket 推送。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface WatchdogService {

    /**
     * 对单个项目执行一轮守护检测（时间轮到期回调）
     *
     * @param project 项目
     */
    void checkOne(MsProject project);

    /**
     * 全量启用守护的项目（启动时批量入轮用）
     *
     * @return 项目列表
     */
    List<MsProject> enabledProjects();

    /**
     * 重新加载项目并校验守护启用状态（入轮后失效检测用）
     *
     * @param projectId 项目 标识
     * @return 启用守护的项目，未启用/不存在返回 空
     */
    MsProject reloadEnabled(Long projectId);

    /**
     * 项目守护是否启用
     *
     * @param projectId 项目 标识
     * @return 是否启用
     */
    boolean isEnabled(Long projectId);

    /**
     * 项目守护检测间隔（秒，归一化后：空/小于 5 按 30）
     *
     * @param projectId 项目 标识
     * @return 间隔秒数
     */
    int watchdogIntervalOf(Long projectId);

    /**
     * 守护运行状态快照（内存计数，供前端/接口查询）
     *
     * @param projectId 项目 标识
     * @return 状态：consecutiveFail / lastCheckTime / lastResult / restartedCount
     */
    Map<String, Object> statusOf(Long projectId);
}
