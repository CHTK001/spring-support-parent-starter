package com.chua.starter.server.support.scheduler;

import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.starter.server.support.connection.ConnectionManager;
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
 *   <li>在离线检测 / 硬件定时采集——主机级配置（各主机开关/间隔存于 ms_server_host 字段）</li>
 *   <li>计划任务扫描（ms_exec_job）</li>
 *   <li>终端会话空闲清理</li>
 *   <li>连接缓存健康检查（connection.*）</li>
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

    /** 主机级检测/采集扫描粒度（秒）：每 5s 扫描一次所有启用主机，按各自间隔决定是否执行 */
    private static final int HOST_SCAN_INTERVAL_SECONDS = 5;

    /** 系统配置服务 */
    private final ServerConfigService configService;

    /** 在离线检测器 */
    private final HostOnlineScheduler onlineScheduler;

    /** 计划任务调度器 */
    private final ExecJobScheduler execJobScheduler;

    /** 会话空闲清理器 */
    private final SessionIdleSweeper sessionIdleSweeper;

    /** 连接缓存管理器 */
    private final ConnectionManager connectionManager;

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

    /** 连接健康检查定时任务 */
    private volatile TimerTask connectionTask;

    /** 延迟检测定时任务 */
    private volatile TimerTask delayTask;

    /**
     * 启动：初始化时间轮并注册全部周期任务
     */
    @PostConstruct
    public void start() {
        timer = Timer.newTimer(WHEEL_SLOTS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        rescheduleOnline();
        rescheduleCollect();
        rescheduleDelay();
        rescheduleExec();
        rescheduleSession();
        rescheduleConnection();
        log.info("[task-scheduler] 时间轮调度器已启动: 在离线/采集/延迟检测/计划任务/会话清理/连接健康检查");
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
     * 主机级配置变更监听：重新注册周期任务
     * <p>主机配置变更（新增/编辑/删除）或全局 connection 配置变更时触发。</p>
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onConfigChanged(ServerConfigChangedEvent event) {
        if (timer == null || !timer.isRunning()) {
            return;
        }
        rescheduleOnline();
        rescheduleCollect();
        rescheduleDelay();
        log.info("[task-scheduler] 配置变更已重排周期任务: {}", event.getKeys());
    }

    /**
     * 重排在离线检测任务：固定细粒度扫描，各主机按自身开关/间隔决定是否探测
     */
    private void rescheduleOnline() {
        cancelTask(onlineTask);
        onlineTask = timer.scheduleAtFixedRate(onlineScheduler::tick,
                HOST_SCAN_INTERVAL_SECONDS, HOST_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 重排采集任务：固定细粒度扫描，各主机按自身开关/间隔/方式决定是否采集
     */
    private void rescheduleCollect() {
        cancelTask(collectTask);
        collectTask = timer.scheduleAtFixedRate(onlineScheduler::collectTick,
                HOST_SCAN_INTERVAL_SECONDS, HOST_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 重排延迟检测任务：固定细粒度扫描，各主机按自身开关/间隔测 RTT 延迟
     */
    private void rescheduleDelay() {
        cancelTask(delayTask);
        delayTask = timer.scheduleAtFixedRate(onlineScheduler::delayTick,
                HOST_SCAN_INTERVAL_SECONDS, HOST_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
     * 重排连接健康检查
     */
    private void rescheduleConnection() {
        cancelTask(connectionTask);
        int interval = normalizeInterval(configService.getInt("connection.sweep.interval", 60));
        connectionTask = timer.scheduleAtFixedRate(() -> {
            long idle = configService.getInt("connection.idle.timeout", 300);
            try {
                connectionManager.sweep(idle);
            } catch (Exception e) {
                log.warn("[task-scheduler] 连接健康检查异常: {}", e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);
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
