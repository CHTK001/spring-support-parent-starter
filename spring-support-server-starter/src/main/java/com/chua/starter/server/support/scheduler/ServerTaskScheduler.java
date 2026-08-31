package com.chua.starter.server.support.scheduler;

import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.starter.server.support.event.ServerConfigChangedEvent;
import com.chua.starter.server.support.service.ServerConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 服务器周期任务时间轮调度器
 * <p>
 * 统一使用 common-starter 的哈希时间轮（{@link Timer}）调度全部周期任务：
 * <ul>
 *   <li>在离线检测（host.online.*）</li>
 *   <li>硬件定时采集（host.collect.*）</li>
 *   <li>计划任务扫描（ms_exec_job）</li>
 *   <li>终端会话空闲清理</li>
 * </ul>
 * 系统设置保存后通过 {@link ServerConfigChangedEvent} 触发动态重排，
 * 间隔/开关调整即时生效，无需重启。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerTaskScheduler {

    /** 时间轮槽位数 */
    private static final int WHEEL_SLOTS = 512;

    /** 时间轮 tick 间隔 */
    private static final long TICK_MILLIS = 500L;

    /** 最小调度间隔（秒） */
    private static final int MIN_INTERVAL_SECONDS = 5;

    /** 在离线检测配置前缀 */
    private static final String PREFIX_ONLINE = "host.online";

    /** 采集配置前缀 */
    private static final String PREFIX_COLLECT = "host.collect";

    /** 系统配置服务 */
    private final ServerConfigService configService;

    /** 在离线检测器 */
    private final HostOnlineScheduler onlineScheduler;

    /** 计划任务调度器 */
    private final ExecJobScheduler execJobScheduler;

    /** 会话空闲清理器 */
    private final SessionIdleSweeper sessionIdleSweeper;

    /** 时间轮实例 */
    private Timer timer;

    /** 在离线检测定时任务 */
    private volatile TimerTask onlineTask;

    /** 采集定时任务 */
    private volatile TimerTask collectTask;

    /** 计划任务扫描定时任务 */
    private volatile TimerTask execTask;

    /** 会话清理定时任务 */
    private volatile TimerTask sessionTask;

    /**
     * 启动：初始化时间轮并注册全部周期任务
     */
    @PostConstruct
    public void start() {
        timer = Timer.newTimer(WHEEL_SLOTS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        rescheduleOnline();
        rescheduleCollect();
        rescheduleExec();
        rescheduleSession();
        log.info("[task-scheduler] 时间轮调度器已启动: 在离线/采集/计划任务/会话清理");
    }

    /**
     * 关闭：停止时间轮
     */
    @PreDestroy
    public void stop() {
        if (timer != null) {
            timer.shutdown();
            timer = null;
        }
    }

    /**
     * 配置变更监听：按变更前缀动态重排对应周期任务
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onConfigChanged(ServerConfigChangedEvent event) {
        if (timer == null || !timer.isRunning()) {
            return;
        }
        if (event.containsPrefix(PREFIX_ONLINE)) {
            rescheduleOnline();
        }
        if (event.containsPrefix(PREFIX_COLLECT)) {
            rescheduleCollect();
        }
        log.info("[task-scheduler] 配置变更已重排周期任务: {}", event.getKeys());
    }

    /**
     * 重排在离线检测任务
     */
    private void rescheduleOnline() {
        cancelTask(onlineTask);
        if (!configService.getBoolean("host.online.enabled", true)) {
            onlineTask = null;
            return;
        }
        int interval = normalizeInterval(configService.getInt("host.online.interval", 60));
        onlineTask = timer.scheduleAtFixedRate(onlineScheduler::tick,
                interval, interval, TimeUnit.SECONDS);
    }

    /**
     * 重排采集任务
     */
    private void rescheduleCollect() {
        cancelTask(collectTask);
        if (!configService.getBoolean("host.collect.enabled", false)) {
            collectTask = null;
            return;
        }
        int interval = normalizeInterval(configService.getInt("host.collect.interval", 3600));
        collectTask = timer.scheduleAtFixedRate(onlineScheduler::collectTick,
                interval, interval, TimeUnit.SECONDS);
    }

    /**
     * 重排计划任务扫描
     */
    private void rescheduleExec() {
        cancelTask(execTask);
        execTask = timer.scheduleAtFixedRate(execJobScheduler::tick,
                60, 30, TimeUnit.SECONDS);
    }

    /**
     * 重排会话清理
     */
    private void rescheduleSession() {
        cancelTask(sessionTask);
        sessionTask = timer.scheduleAtFixedRate(sessionIdleSweeper::sweep,
                120, 60, TimeUnit.SECONDS);
    }

    /**
     * 取消并清空定时任务
     *
     * @param task 任务
     */
    private void cancelTask(TimerTask task) {
        if (task != null) {
            timer.cancel(task);
        }
    }

    /**
     * 归一化间隔（不小于最小间隔）
     *
     * @param interval 配置间隔（秒）
     * @return 归一化后的间隔
     */
    private int normalizeInterval(int interval) {
        return interval < MIN_INTERVAL_SECONDS ? MIN_INTERVAL_SECONDS : interval;
    }
}
