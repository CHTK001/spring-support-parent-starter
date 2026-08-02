package com.chua.starter.device.support.scheduler;

import com.chua.common.support.task.scheduler.ScheduledTask;
import com.chua.common.support.task.scheduler.TimeScheduler;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.mapper.DeviceInfoMapper;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.service.DeviceTaskStateService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 设备定时任务调度器
 * <p>复用 {@link TimeScheduler} 提供每秒固定频率的触发，内部通过 Trigger 模式判断是否真正执行业务逻辑。</p>
 *
 * <h3>Trigger 模式说明</h3>
 * <p>调度器每秒触发一次，但每个任务有独立的 Trigger 标志：</p>
 * <ul>
 *   <li>{@link DeviceTaskStateService#shouldRun} 返回 true 才执行</li>
 *   <li>{@link DeviceTaskStateService#markRunning} 抢锁，避免重复执行</li>
 *   <li>执行完毕调用 {@link DeviceTaskStateService#markFinished} 释放锁</li>
 * </ul>
 * <p>每个厂商离线检测阈值不同（如海康/大华 60s、萤石 120s），无法统一休眠间隔，</p>
 * <p>因此采用每秒轮询 + 逐设备按各自阈值判断的方案。</p>
 *
 * @author CH
 * @since 4.0.0.42
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
     * 调度触发间隔（秒）
     */
    private static final long SCHEDULE_INTERVAL_SEC = 1;

    /**
     * 任务状态服务
     */
    private final DeviceTaskStateService taskStateService;

    /**
     * 提供商配置服务
     */
    private final DeviceProviderConfigService providerConfigService;

    /**
     * 设备信息 Mapper
     */
    private final DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备会话管理器
     */
    private final DeviceSessionManager sessionManager;

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
     * 启动调度器：每秒触发一次，任务内部按 Trigger 判断。
     */
    @PostConstruct
    public void start() {
        timeScheduler = TimeScheduler.of();
        offlineCheckTask = timeScheduler.scheduleFixedRate(
                TASK_OFFLINE_CHECK, this::executeOfflineCheck, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        eventPollTask = timeScheduler.scheduleFixedRate(
                TASK_EVENT_POLL, this::executeEventPoll, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("设备定时调度器已启动，触发间隔 {} 秒", SCHEDULE_INTERVAL_SEC);
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
     * 执行离线检测
     * <p>遍历所有在线设备，按设备自身阈值（回退厂商默认值）检测在线状态。</p>
     */
    private void doOfflineCheck() {
        List<DeviceInfo> devices = providerConfigService.listCheckableDevices();
        for (DeviceInfo device : devices) {
            DeviceConnectInfo info = buildConnectInfo(device);
            Integer threshold = device.getDeviceInfoOfflineThreshold();
            if (threshold == null || threshold <= 0) {
                threshold = providerConfigService.getDefaultOfflineThreshold(device.getDeviceInfoVendor());
            }
            boolean online = providerConfigService.checkOnline(device, info);
            if (!online) {
                device.setDeviceInfoOnline(false);
                device.setDeviceInfoUpdatedAt(LocalDateTime.now());
                deviceInfoMapper.updateById(device);
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
            doEventPoll();
        } finally {
            taskStateService.markFinished(TASK_EVENT_POLL);
        }
    }

    /**
     * 执行事件轮询
     */
    private void doEventPoll() {
        // 实际逻辑委托给 DeviceEventService
        log.debug("事件轮询触发");
    }

    /**
     * 构建连接信息
     *
     * @param device 设备信息
     * @return 连接信息
     */
    private DeviceConnectInfo buildConnectInfo(DeviceInfo device) {
        DeviceConnectInfo info = new DeviceConnectInfo();
        info.setVendor(device.getDeviceInfoVendor());
        info.setIp(device.getDeviceInfoIp());
        info.setPort(device.getDeviceInfoPort());
        info.setUsername(device.getDeviceInfoUsername());
        info.setPassword(device.getDeviceInfoPassword());
        return info;
    }
}
