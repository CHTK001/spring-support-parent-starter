// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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

import java.util.Set;
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

    /**
     * 时间轮槽位数
    */
    private static final int WHEEL_SLOTS = 512;

    /**
     * 时间轮 tick 间隔
    */
    private static final long TICK_MILLIS = 500L;

    /**
     * 最小调度间隔（秒）
    */
    private static final int MIN_INTERVAL_SECONDS = 5;

    /**
     * 主机级检测/采集扫描粒度（秒）：每 5s 扫描一次所有启用主机，按各自间隔决定是否执行
     */
    private static final int HOST_SCAN_INTERVAL_SECONDS = 5;

    /**
     * Actuator 定时采集扫描粒度（秒）：各项目按自身开关/间隔决定是否采集
     */
    private static final int ACTUATOR_SCAN_INTERVAL_SECONDS = 5;

    /**
     * 采集扫描粒度（秒）：采集间隔可小至 {@code MIN_INTERVAL=5s}，若与在离线检测共用 5s 扫描，
     * 因扫描相位与采集完成相位错开，配置 5s/10s 会被量化成约 10s/15s。采集单独用 1s 细粒度扫描，
     * 把触发延迟压到 ≤1s（5s 配置实际约 5-6s，10s 配置约 10-11s；剩余约 0.5-1s 为采集本身耗时）。
     */
    private static final int COLLECT_SCAN_INTERVAL_SECONDS = 1;

    /**
     * 真正会改变本调度器节奏的系统配置键。
     *
     * <p>只有连接健康检查的间隔/空闲超时来自系统配置；在离线检测、采集、延迟检测、
     * Actuator 采集都是固定扫描粒度，是否执行由各主机自身字段决定，改系统配置不需要重排。</p>
     */
    private static final Set<String> CONNECTION_KEYS = Set.of(
            "connection.sweep.interval", "connection.idle.timeout");

    /**
     * 系统配置服务
    */
    private final ServerConfigService configService;

    /**
     * 在离线检测器
     */
    private final HostOnlineScheduler onlineScheduler;

    /**
     * 会话空闲清理器
     */
    private final SessionIdleSweeper sessionIdleSweeper;

    /**
     * 连接缓存管理器
    */
    private final ConnectionManager connectionManager;

    /**
     * 时间轮实例
    */
    /**
     * Actuator 定时采集调度器（项目级开关，全局时间轮统一驱动）
     */
    private final ActuatorMonitorScheduler actuatorMonitorScheduler;

    /**
     * 时间轮实例
     */
    private Timer timer;

    /**
     * 在离线检测定时任务
    */
    private volatile TimerTask onlineTask;

    /**
     * 采集定时任务
     */
    private volatile TimerTask collectTask;

    /**
     * 会话清理定时任务
     */
    private volatile TimerTask sessionTask;

    /**
     * 连接健康检查定时任务
    */
    private volatile TimerTask connectionTask;

    /**
     * 延迟检测定时任务
    */
    private volatile TimerTask delayTask;

    /**
     * Actuator 定时采集任务
     */
    private volatile TimerTask actuatorMonitorTask;

    /**
     * 启动：初始化时间轮并注册全部周期任务
     */
    @PostConstruct
    public void start() {
        timer = Timer.newTimer(WHEEL_SLOTS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        /**
         * rescheduleonline。
         */
        rescheduleOnline();
        /**
         * reschedulecollect。
         */
        rescheduleCollect();
        /**
         * reschedule延迟。
         */
        rescheduleDelay();
        rescheduleActuatorMonitor();
        rescheduleSession();
        rescheduleConnection();
        log.info("[task-scheduler] 时间轮调度器已启动: 在离线/采集/延迟检测/会话清理/连接健康检查(计划任务由 exec-cron 逐任务 CronTrigger 调度)");
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
     * 系统配置变更监听：只在连接相关配置变化时重排连接健康检查。
     *
     * <p>{@link ServerConfigChangedEvent} 是「任意系统配置保存」都会发的通用事件
     * （保存终端配色、邮件、SIP 等等都算），这里以前无条件把 5 类周期任务全部
     * cancel + 重新注册，等于把在离线探测/指标采集/连接扫描的倒计时一起清零：
     * 保存一个配色就能让采集相位抖一下，日志还会打一行「配置变更已重排周期任务」。
     * 现在按 key 过滤，只做真正相关的那一个。</p>
     *
     * @param event 配置变更事件
     */
    @EventListener
    public void onConfigChanged(ServerConfigChangedEvent event) {
        if (timer == null || !timer.isRunning()) {
            return;
        }
        Set<String> keys = event == null ? null : event.keys();
        if (keys != null && keys.stream().noneMatch(CONNECTION_KEYS::contains)) {
            return;
        }
        rescheduleConnection();
        log.info("[task-scheduler] connection 配置变更，连接健康检查已重排: {}", keys);
    }

    /**
     * 重排在离线检测任务：固定细粒度扫描，各主机按自身开关/间隔决定是否探测
     */
    private void rescheduleOnline() {
        /**
         * cancel任务。
         * @param onlineTask online任务
         */
        cancelTask(onlineTask);
        onlineTask = timer.scheduleAtFixedRate(onlineScheduler::tick,
                HOST_SCAN_INTERVAL_SECONDS, HOST_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 重排采集任务：固定细粒度扫描，各主机按自身开关/间隔/方式决定是否采集
     */
    private void rescheduleCollect() {
        /**
         * cancel任务。
         * @param collectTask collect任务
         */
        cancelTask(collectTask);
        collectTask = timer.scheduleAtFixedRate(onlineScheduler::collectTick,
                COLLECT_SCAN_INTERVAL_SECONDS, COLLECT_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 重排延迟检测任务：固定细粒度扫描，各主机按自身开关/间隔测 RTT 延迟
     */
    private void rescheduleDelay() {
        /**
         * cancel任务。
         * @param delayTask 延迟任务
         */
        cancelTask(delayTask);
        delayTask = timer.scheduleAtFixedRate(onlineScheduler::delayTick,
                HOST_SCAN_INTERVAL_SECONDS, HOST_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 重排 Actuator 定时采集：全局时间轮固定扫描，各项目按自身开关/间隔决定是否采集
     */
    private void rescheduleActuatorMonitor() {
        cancelTask(actuatorMonitorTask);
        actuatorMonitorTask = timer.scheduleAtFixedRate(actuatorMonitorScheduler::tick,
                ACTUATOR_SCAN_INTERVAL_SECONDS, ACTUATOR_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 重排会话清理
     */
    private void rescheduleSession() {
        /**
         * cancel任务。
         * @param sessionTask 会话任务
         */
        cancelTask(sessionTask);
        sessionTask = timer.scheduleAtFixedRate(sessionIdleSweeper::sweep,
                120, 60, TimeUnit.SECONDS);
    }

    /**
     * 重排连接健康检查
     */
    private void rescheduleConnection() {
        /**
         * cancel任务。
         * @param connectionTask connection任务
         */
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