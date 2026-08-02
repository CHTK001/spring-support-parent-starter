package com.chua.starter.device.support.scheduler;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceTaskState;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 设备定时任务调度器
 * <p>每秒执行一次的死循环，通过 Trigger 模式判断是否真正执行业务逻辑。</p>
 *
 * <h3>Trigger 模式说明</h3>
 * <p>后台使用单一线程循环遍历所有任务，每个任务有独立的 Trigger 标志：</p>
 * <ul>
 *   <li>{@link DeviceTaskStateService#shouldRun} 返回 true 才执行</li>
 *   <li>{@link DeviceTaskStateService#markRunning} 抢锁，避免并发</li>
 *   <li>执行完毕调用 {@link DeviceTaskStateService#markFinished} 释放锁</li>
 * </ul>
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
     * 调度器运行标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 后台调度线程
     */
    private Thread schedulerThread;

    /**
     * 启动调度器
     */
    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            schedulerThread = new Thread(this::loop, "device-scheduler");
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            log.info("设备定时调度器已启动");
        }
    }

    /**
     * 停止调度器
     */
    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (schedulerThread != null) {
                schedulerThread.interrupt();
            }
            log.info("设备定时调度器已停止");
        }
    }

    /**
     * 主循环：每秒执行一次
     */
    private void loop() {
        while (running.get()) {
            try {
                executeTriggers();
            } catch (Exception e) {
                log.error("调度器执行异常", e);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 执行 Trigger 判断
     */
    private void executeTriggers() {
        executeOfflineCheck();
        executeEventPoll();
    }

    /**
     * 离线检测 Trigger
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
     */
    private void doOfflineCheck() {
        List<DeviceInfo> devices = providerConfigService.listCheckableDevices();
        for (DeviceInfo device : devices) {
            DeviceConnectInfo info = buildConnectInfo(device);
            int threshold = device.getDeviceInfoOfflineThreshold();
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
     * 事件轮询 Trigger
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