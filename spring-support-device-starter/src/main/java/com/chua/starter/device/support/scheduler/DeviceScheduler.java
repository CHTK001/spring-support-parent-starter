package com.chua.starter.device.support.scheduler;

import com.chua.common.support.task.scheduler.ScheduledTask;
import com.chua.common.support.task.scheduler.TimeScheduler;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.service.DeviceAccessRecordService;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.service.DeviceStatusService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.device.support.service.DeviceTaskStateService;
import com.chua.starter.device.spi.DeviceSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 设备定时任务调度器
 * <p>复用 {@link TimeScheduler} 提供每秒固定频率的触发，内部通过 Trigger 模式判断是否真正执行业务逻辑。</p>
 *
 * <h3>Trigger 模式说明</h3>
 * <ul>
 *   <li>{@link DeviceTaskStateService#shouldRun} 返回 true 才执行</li>
 *   <li>{@link DeviceTaskStateService#markRunning} 抢锁，避免重复执行</li>
 *   <li>执行完毕调用 {@link DeviceTaskStateService#markFinished} 释放锁</li>
 * </ul>
 *
 * <h3>在线检测的两条路径</h3>
 * <ol>
 *   <li>有会话：直接探测；在线刷新心跳，离线则注销会话并落库；</li>
 *   <li>无会话：按退避间隔尝试重连，成功后设备恢复在线——否则设备一旦掉线就永远无法回到在线状态。</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceScheduler {

    /**
     * 离线检测任务名
     */
    private static final String TASK_OFFLINE_CHECK = "deviceOfflineCheck";

    /**
     * 事件轮询任务名
     */
    private static final String TASK_EVENT_POLL = "deviceEventPoll";

    /**
     * 设备目录同步任务名
     */
    private static final String TASK_DEVICE_SYNC = "deviceSync";

    /**
     * 门禁记录抽取任务名
     */
    private static final String TASK_ACCESS_EXTRACT = "deviceAccessExtract";

    /**
     * 调度触发间隔（秒）
     */
    /**
     * 调度 tick 间隔（秒）。
     *
     * <p>任务是否到期由 device_task_state.interval 控制，tick 只负责“检查是否到期”。
     * 原实现每 1 秒查询一次任务状态；改为 5 秒后，最坏离线判定延迟仅增加 4 秒，
     * 同时把状态查询频率降低 80%。</p>
     */
    private static final long SCHEDULE_INTERVAL_SEC = 5;

    /**
     * 离线设备重连尝试间隔（秒），避免对真正离线的设备持续建连
     */
    private static final long RECOVERY_INTERVAL_SEC = 300;

    /**
     * 单轮最多尝试恢复的设备数，避免一次性建连风暴
     */
    private static final int MAX_RECOVERY_PER_CYCLE = 20;

    /**
     * 任务状态服务
     */
    private final DeviceTaskStateService taskStateService;

    /**
     * 提供商配置服务
     */
    private final DeviceProviderConfigService providerConfigService;

    /**
     * 设备服务（恢复连接）
     */
    private final DeviceService deviceService;

    /**
     * 在线状态服务
     */
    private final DeviceStatusService statusService;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 设备事件服务（事件轮询）
     */
    private final DeviceEventService eventService;

    /**
     * 设备目录同步服务（厂商同步）
     */
    private final DeviceSyncService syncService;

    /**
     * 门禁记录服务（抽取落库）
     */
    private final DeviceAccessRecordService accessRecordService;

    /**
     * 时间调度器（复用公共模块）
     */
    private TimeScheduler timeScheduler;

    /**
     * 离线检测定时任务
     */
    private ScheduledTask offlineCheckTask;

    /**
     * 事件轮询定时任务
     */
    private ScheduledTask eventPollTask;

    /**
     * 设备目录同步定时任务
     */
    private ScheduledTask deviceSyncTask;

    /**
     * 门禁记录抽取定时任务
     */
    private ScheduledTask accessExtractTask;

    /**
     * 启动调度器：每秒触发一次各任务的 Trigger 判断。
     * <p>事件轮询（{@code deviceEventPoll}）、设备同步（{@code deviceSync}）、
     * 门禁抽取（{@code deviceAccessExtract}）全部注册进 {@link TimeScheduler}，
     * 统一由 {@link DeviceTaskStateService} 的 interval 控制到期。</p>
     */
    @PostConstruct
    public void start() {
        timeScheduler = TimeScheduler.of();
        offlineCheckTask = timeScheduler.scheduleFixedRate(
                TASK_OFFLINE_CHECK, this::executeOfflineCheck, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        eventPollTask = timeScheduler.scheduleFixedRate(
                TASK_EVENT_POLL, this::executeEventPoll, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        deviceSyncTask = timeScheduler.scheduleFixedRate(
                TASK_DEVICE_SYNC, this::executeDeviceSync, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        accessExtractTask = timeScheduler.scheduleFixedRate(
                TASK_ACCESS_EXTRACT, this::executeAccessExtract, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("设备定时调度器已启动，触发间隔 {} 秒（offlineCheck / eventPoll / sync / accessExtract 统一由时间轮调度）",
                SCHEDULE_INTERVAL_SEC);
    }

    /**
     * 停止调度器
     */
    @PreDestroy
    public void stop() {
        if (timeScheduler != null) {
            timeScheduler.shutdown();
        }
        log.info("设备定时调度器已停止");
    }

    /**
     * 离线检测 Trigger 判断
     */
    private void executeOfflineCheck() {
        if (!taskStateService.shouldRun(TASK_OFFLINE_CHECK)) {
            return;
        }
        if (!taskStateService.markRunning(TASK_OFFLINE_CHECK)) {
            return;
        }
        try {
            doOfflineCheck();
        } finally {
            taskStateService.markFinished(TASK_OFFLINE_CHECK);
        }
    }

    /**
     * 执行在线检测与离线恢复
     */
    private void doOfflineCheck() {
        List<DeviceInfo> devices = providerConfigService.listCheckableDevices();
        int recoveries = 0;
        for (DeviceInfo device : devices) {
            DeviceSession session = sessionManager.getSession(device.getDeviceInfoId());
            if (session == null) {
                if (recoveries < MAX_RECOVERY_PER_CYCLE && isRecoveryDue(device)) {
                    recoveries++;
                    tryRecover(device);
                }
                continue;
            }
            boolean online;
            try {
                online = session.isOnline() && session.testConnection();
            } catch (Exception e) {
                log.debug("设备探测异常，按离线处理: id={}, message={}",
                        device.getDeviceInfoId(), e.getMessage());
                online = false;
            }
            statusService.updateOnline(device, online);
            if (!online) {
                sessionManager.unregister(device.getDeviceInfoId());
                log.warn("设备离线: id={}, ip={}", device.getDeviceInfoId(), device.getDeviceInfoIp());
            }
        }
    }

    /**
     * 事件轮询 Trigger 判断
     */
    private void executeEventPoll() {
        if (!taskStateService.shouldRun(TASK_EVENT_POLL)) {
            return;
        }
        if (!taskStateService.markRunning(TASK_EVENT_POLL)) {
            return;
        }
        try {
            eventService.pollAllDevices();
        } finally {
            taskStateService.markFinished(TASK_EVENT_POLL);
        }
    }

    /**
     * 设备目录同步 Trigger 判断
     */
    private void executeDeviceSync() {
        if (!taskStateService.shouldRun(TASK_DEVICE_SYNC)) {
            return;
        }
        if (!taskStateService.markRunning(TASK_DEVICE_SYNC)) {
            return;
        }
        try {
            syncService.syncDevices(null);
        } finally {
            taskStateService.markFinished(TASK_DEVICE_SYNC);
        }
    }

    /**
     * 门禁记录抽取 Trigger 判断
     */
    private void executeAccessExtract() {
        if (!taskStateService.shouldRun(TASK_ACCESS_EXTRACT)) {
            return;
        }
        if (!taskStateService.markRunning(TASK_ACCESS_EXTRACT)) {
            return;
        }
        try {
            LocalDateTime end = LocalDateTime.now();
            accessRecordService.extractRecent(end.minusMinutes(10), end);
        } finally {
            taskStateService.markFinished(TASK_ACCESS_EXTRACT);
        }
    }

    /**
     * 是否到了下一次恢复尝试时间
     *
     * @param device 设备
     * @return true 表示可以尝试
     */
    private boolean isRecoveryDue(DeviceInfo device) {
        LocalDateTime lastOfflineAt = device.getDeviceInfoLastOfflineAt();
        if (lastOfflineAt == null) {
            return true;
        }
        return Duration.between(lastOfflineAt, LocalDateTime.now()).getSeconds() >= RECOVERY_INTERVAL_SEC;
    }

    /**
     * 尝试恢复离线设备
     * <p>重连成功由 {@link DeviceService#reconnect(String)} 落库并发布事件；
     * 失败时写入离线时间，使下一轮按退避间隔重试。</p>
     *
     * @param device 设备
     */
    private void tryRecover(DeviceInfo device) {
        try {
            deviceService.reconnect(device.getDeviceInfoId());
            log.info("设备恢复在线: id={}, ip={}", device.getDeviceInfoId(), device.getDeviceInfoIp());
        } catch (Exception e) {
            // 失败也要刷新 last_offline_at，避免每秒重连
            statusService.updateOnline(device, false, true);
            log.debug("设备恢复失败: id={}, message={}", device.getDeviceInfoId(), e.getMessage());
        }
    }
}
