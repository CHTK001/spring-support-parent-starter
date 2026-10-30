package com.chua.starter.device.support.scheduler;

import com.chua.common.support.task.scheduler.ScheduledTask;
import com.chua.common.support.task.scheduler.TimeScheduler;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.entity.DeviceTaskState;
import com.chua.starter.device.support.service.DeviceAccessRecordService;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.support.service.DeviceProviderConfigService;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.service.DeviceStatusService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.device.support.service.DeviceTaskStateService;
import com.chua.starter.device.support.spi.DeviceSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 设备定时任务调度器（时间轮时钟）
 * <p>复用 {@link TimeScheduler}（common-starter）提供固定频率的 tick；
 * 每个 tick 触发后由 {@link DeviceTaskStateService} 的 Trigger 模式判断
 * 是否真正执行业务逻辑。</p>
 *
 * <h3>任务分类</h3>
 * <ul>
 *   <li>全局任务：<code>deviceOfflineCheck</code>（在离线检测）/ <code>deviceEventPoll</code>（事件轮询）/
 *   <code>deviceSync</code>（厂商目录同步）/ <code>deviceAccessExtract</code>（门禁记录抽取）；</li>
 *   <li>凭据级任务：每套提供商凭据稀释出 3 个任务，名称为
 *   <code>offlineDetect_{credId}</code>/<code>deviceSync_{credId}</code>/<code>orgSync_{credId}</code>，
 *   行为由 {@link DeviceProviderCredential} 的高级配置驱动（开关 + 间隔秒），默认全部关闭。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceScheduleManager {

    /**
     * 全局任务名
     */
    private static final String TASK_OFFLINE_CHECK = "deviceOfflineCheck";
    private static final String TASK_EVENT_POLL = "deviceEventPoll";
    private static final String TASK_DEVICE_SYNC = "deviceSync";
    private static final String TASK_ACCESS_EXTRACT = "deviceAccessExtract";

    /**
     * 凭据级任务前缀（任务名 = 前缀 + '_' + 凭据 ID）
     */
    private static final String PREFIX_OFFLINE_DETECT = "offlineDetect";
    private static final String PREFIX_DEVICE_SYNC = "deviceSync";
    private static final String PREFIX_ORG_SYNC = "orgSync";

    /**
     * 调度 tick 间隔（秒）。任务是否到期由 {@code device_task_state.interval} 控制，
     * tick 只负责"检查每个任务是否到期待执行"。
     */
    private static final long SCHEDULE_INTERVAL_SEC = 5;
    /** 默认首次间隔（秒） */
    private static final int DEFAULT_INTERVAL_SEC = 30;
    /** 离线设备重连尝试间隔（秒），避免对真正离线的设备持续建连 */
    private static final long RECOVERY_INTERVAL_SEC = 300;
    /** 单轮最多尝试恢复的设备数，避免一次性建连风暴 */
    private static final int MAX_RECOVERY_PER_CYCLE = 20;

    private final DeviceTaskStateService taskStateService;
    private final DeviceProviderConfigService providerConfigService;
    private final DeviceService deviceService;
    private final DeviceStatusService statusService;
    private final DeviceSessionManager sessionManager;
    private final DeviceEventService eventService;
    private final DeviceSyncService syncService;
    private final DeviceAccessRecordService accessRecordService;
    private final DeviceProviderCredentialService credentialService;

    private TimeScheduler timeScheduler;
    /** 全局 Tick 注册表（任务名 → 注册的定时任务） */
    private final Map<String, ScheduledTask> tickTasks = new ConcurrentHashMap<>();
    /** 凭据级任务注册表（任务名 → 注册的定时任务） */
    private final Map<String, ScheduledTask> credentialTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        try {
            timeScheduler = TimeScheduler.of();
            // 4 个全局 tick：间隔 SCHEDULE_INTERVAL_SEC 秒
            tick(TASK_OFFLINE_CHECK, this::executeOfflineCheck);
            tick(TASK_EVENT_POLL, this::executeEventPoll);
            tick(TASK_DEVICE_SYNC, this::executeDeviceSync);
            tick(TASK_ACCESS_EXTRACT, this::executeAccessExtract);
            // 全部凭据的 Tick（从 `device_provider_credential` 的高级配置取开关与间隔）
            refresh();
            log.info("设备定时调度器已启动，tick 间隔 {} 秒（{} / {} / {} / {}）",
                    SCHEDULE_INTERVAL_SEC, TASK_OFFLINE_CHECK, TASK_EVENT_POLL, TASK_DEVICE_SYNC, TASK_ACCESS_EXTRACT);
        } catch (Exception e) {
            log.error("设备定时调度器启动失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        cancelAll(credentialTasks);
        cancelAll(tickTasks);
        if (timeScheduler != null) {
            timeScheduler.shutdown();
        }
        log.info("设备定时调度器已停止");
    }

    /**
     * 更新已注册的任务间隔秒（任务编号有变化的时间轮任务时调用，公开脚以便任务页面修改间隔）。
     *
     * @param taskName 任务名
     * @param interval 新间隔（秒）
     * @return 该任务是否在时间轮上（不在则不实质修改）
     */
    public boolean rescheduleInterval(String taskName, int interval) {
        if (interval <= 0) {
            return false;
        }
        ScheduledTask old = registered(taskName);
        if (old == null) {
            return false;
        }
        try {
            ScheduledTask task = timeScheduler.rescheduleFixedRate(taskName, interval, TimeUnit.SECONDS);
            if (task != null) {
                taskStateService.updateInterval(taskName, interval);
                return true;
            }
        } catch (Exception e) {
            log.warn("时间轮重新调度失败: name={}, interval={}秒", taskName, interval, e);
        }
        return false;
    }

    /**
     * 刷新时间轮上的凭据级任务（清除旧任务 + 按最新配置重建）。
     * <p>凭据的高级配置（开启/关闭/间隔）变更后，由 {@code ProviderCredentialController#update}
     * 或 {@code #refresh(String)} 调用。</p>
     */
    public void refresh() {
        try {
            log.info("重扫凭据级定时任务");
            // 卸载全部现有凭据级任务（取消未执行的 Future）
            for (ScheduledTask task : credentialTasks.values()) {
                try {
                    task.cancel();
                } catch (Exception e) {
                    log.debug("取消定时任务失败: {}", task.getId(), e);
                }
            }
            credentialTasks.clear();
            // 按新配置重建
            for (DeviceProviderCredential credential : credentialService.list(null)) {
                try {
                    refreshCredential(credential);
                } catch (Exception e) {
                    log.error("凭据级定时任务注册失败: {}", credential.getDeviceCredentialId(), e);
                }
            }
        } catch (Exception e) {
            log.error("凭据级定时任务刷新失败", e);
        }
    }

    /**
     * 刷新单套凭据的 Tick（按开关/间隔），可单独调用于热更新。
     *
     * @param credentialId 凭据 ID
     */
    public void refresh(String credentialId) {
        DeviceProviderCredential credential = credentialService.get(credentialId);
        if (credential == null) {
            return;
        }
        refreshCredential(credential);
    }

    /**
     * 按凭据重注册 3 个 Tick。
     * <p>开关（enabled）关闭时不注册任务；间隔（interval）为空时不注册任务。</p>
     *
     * @param credential 凭据
     */
    private void refreshCredential(DeviceProviderCredential credential) {
        String credId = credential.getDeviceCredentialId();
        if (credId == null) {
            return;
        }
        for (CredentialTask source : CredentialTask.values()) {
            if (!source.acts(credential)) {
                continue;
            }
            String taskName = source.name(credId);
            // 间隔优先取任务状态行（用户可能调整过），分录取在中的 interval 字段
            Integer interval = taskStateInterval(taskName, source.intervalOf(credential));
            if (interval == null || interval <= 0) {
                continue;
            }
            cancel(taskName, credentialTasks);
            ScheduledTask task = timeScheduler.scheduleFixedRate(taskName,
                    () -> tickCredential(source, credId),
                    interval, TimeUnit.SECONDS);
            // 补种状态行，让任务出现在任务列表里
            taskStateService.seed(taskName, interval, source.description(credential));
            credentialTasks.put(taskName, task);
            log.info("凭据级定时任务已注册: name={}, type={}, interval={}秒", taskName, source, interval);
        }
    }

    /**
     * 凭据级任务的 Tick 门：read config → shouldRun → markRunning → body → markFinished
     */
    private void tickCredential(CredentialTask source, String credId) {
        DeviceProviderCredential credential = credentialService.get(credId);
        if (credential == null || !source.acts(credential)) {
            return;
        }
        String taskName = source.name(credId);
        if (!taskStateService.shouldRun(taskName)) {
            return;
        }
        if (!taskStateService.markRunning(taskName)) {
            return;
        }
        try {
            source.body(credId).run();
        } finally {
            taskStateService.markFinished(taskName);
        }
    }

    /**
     * 立即执行（绕过时间轮）
     *
     * @param taskName 任务名（全局任务或凭据级任务）
     * @return 是否已有对应的执行入口
     */
    public boolean runNow(String taskName) {
        CredentialTask source = sourceOf(taskName);
        String credId = source == null ? null : credentialIdOf(taskName);
        if (source != null && credId != null) {
            try {
                source.body(credId).run();
                return true;
            } catch (Exception e) {
                log.warn("立即执行失败: name={}, message={}", taskName, e.getMessage(), e);
                return false;
            }
        }
        switch (taskName) {
            case TASK_OFFLINE_CHECK -> executeOfflineCheck();
            case TASK_EVENT_POLL -> executeEventPoll();
            case TASK_DEVICE_SYNC -> {
                try {
                    syncService.syncDevices(null);
                    taskStateService.markFinished(TASK_DEVICE_SYNC);
                } catch (Exception e) {
                    log.error("立即执行设备同步失败", e);
                }
            }
            case TASK_ACCESS_EXTRACT -> executeAccessExtract();
            default -> log.warn("未实现的任务名: {}", taskName);
        }
        return true;
    }

    /** 注册全局 Tick（从 `device_task_state` 读 state 行，固定间隔 tick） */
    private void tick(String taskName, Runnable body) {
        ScheduledTask task = timeScheduler.scheduleFixedRate(taskName, body, SCHEDULE_INTERVAL_SEC, TimeUnit.SECONDS);
        tickTasks.put(taskName, task);
    }

    /** 取消任务（注册的 future） */
    private void cancelAll(Map<String, ScheduledTask> tasks) {
        for (ScheduledTask task : tasks.values()) {
            try {
                task.cancel();
            } catch (Exception e) {
                log.debug("取消定时任务失败: {}", task.getId(), e);
            }
        }
        tasks.clear();
    }

    private ScheduledTask cancel(String taskName, Map<String, ScheduledTask> tasks) {
        ScheduledTask task = tasks.remove(taskName);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception e) {
                log.debug("取消定时任务失败: {}", taskName, e);
            }
        }
        return task;
    }

    /** 注册表里已存在的 tick（全局或凭据级） */
    private ScheduledTask registered(String taskName) {
        ScheduledTask task = tickTasks.get(taskName);
        if (task != null) {
            return task;
        }
        return credentialTasks.get(taskName);
    }

    /** 取任务状态行上的间隔，未补种/未改过则分取 in 的 interval 字段 */
    private Integer taskStateInterval(String taskName, Integer configuration) {
        DeviceTaskState stateRow;
        try {
            stateRow = taskStateService.getState(taskName);
        } catch (Exception e) {
            // 状态行尚未补种时不阻塞
            log.debug("取任务状态行失败: {}", taskName, e);
            return configuration == null ? DEFAULT_INTERVAL_SEC : configuration;
        }
        if (stateRow != null && stateRow.getDeviceTaskStateInterval() != null && stateRow.getDeviceTaskStateInterval() > 0) {
            return stateRow.getDeviceTaskStateInterval();
        }
        return configuration == null ? DEFAULT_INTERVAL_SEC : configuration;
    }

    /**
     * 离线检测（全局）
     */
    private void executeOfflineCheck() {
        if (!taskStateService.shouldRun(TASK_OFFLINE_CHECK)) {
            return;
        }
        if (!taskStateService.markRunning(TASK_OFFLINE_CHECK)) {
            return;
        }
        try {
            doOfflineCheck(providerConfigService.listCheckableDevices());
        } finally {
            taskStateService.markFinished(TASK_OFFLINE_CHECK);
        }
    }

    /**
     * 设备离线检测
     */
    private void doOfflineCheck(List<DeviceInfo> devices) {
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

    /** 事件轮询 */
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

    /** 设备目录同步（全部厂商） */
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

    /** 门禁记录抽取 */
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

    private boolean isRecoveryDue(DeviceInfo device) {
        LocalDateTime lastOfflineAt = device.getDeviceInfoLastOfflineAt();
        if (lastOfflineAt == null) {
            return true;
        }
        return Duration.between(lastOfflineAt, LocalDateTime.now()).getSeconds() >= RECOVERY_INTERVAL_SEC;
    }

    private void tryRecover(DeviceInfo device) {
        try {
            deviceService.reconnect(device.getDeviceInfoId());
            log.info("设备恢复在线: id={}, ip={}", device.getDeviceInfoId(), device.getDeviceInfoIp());
        } catch (Exception e) {
            statusService.updateOnline(device, false, true);
            log.debug("设备恢复失败: id={}, message={}", device.getDeviceInfoId(), e.getMessage());
        }
    }

    /**
     * 凭据 ID 反推（任务名 → credId）
     */
    private String credentialIdOf(String taskName) {
        int sep = taskName.lastIndexOf('_');
        return sep <= 0 ? null : taskName.substring(sep + 1);
    }

    /**
     * 任务名 → 凭据级任务来源；不是凭据级任务返回 null
     */
    private CredentialTask sourceOf(String taskName) {
        for (CredentialTask source : CredentialTask.values()) {
            if (taskName.startsWith(source.prefix + "_")) {
                return source;
            }
        }
        return null;
    }

    /**
     * 凭据级任务分类（开关 + 间隔 + 调用体）
     */
    private enum CredentialTask {
        OFFLINE_DETECT(PREFIX_OFFLINE_DETECT, "在离线检测", DeviceProviderCredential::getDeviceCredentialOfflineDetectEnabled,
                DeviceProviderCredential::getDeviceCredentialOfflineDetectInterval, DeviceScheduleManager::executeOfflineDetect),
        DEVICE_SYNC(PREFIX_DEVICE_SYNC, "设备自动同步", DeviceProviderCredential::getDeviceCredentialDeviceSyncEnabled,
                DeviceProviderCredential::getDeviceCredentialDeviceSyncInterval, DeviceScheduleManager::executeDeviceSyncCredential),
        ORG_SYNC(PREFIX_ORG_SYNC, "组织自动同步", DeviceProviderCredential::getDeviceCredentialOrgSyncEnabled,
                DeviceProviderCredential::getDeviceCredentialOrgSyncInterval, DeviceScheduleManager::executeOrgSyncCredential);

        private final String prefix;
        private final String description;
        private final java.util.function.Function<DeviceProviderCredential, Boolean> enabled;
        private final java.util.function.Function<DeviceProviderCredential, Integer> interval;
        private final java.util.function.Function<String, Runnable> body;

        CredentialTask(String prefix, String description,
                       java.util.function.Function<DeviceProviderCredential, Boolean> enabled,
                       java.util.function.Function<DeviceProviderCredential, Integer> interval,
                       java.util.function.Function<String, Runnable> body) {
            this.prefix = prefix;
            this.description = description;
            this.enabled = enabled;
            this.interval = interval;
            this.body = body;
        }

        /** 开关是否开启 */
        boolean acts(DeviceProviderCredential credential) {
            return Boolean.TRUE.equals(enabled.apply(credential));
        }

        /** 间隔秒 */
        Integer intervalOf(DeviceProviderCredential credential) {
            return interval.apply(credential);
        }

        /** 任务名 = prefix_ + 凭据 ID */
        String name(String credId) {
            return prefix + "_" + credId;
        }

        /** 描述（任务状态行） */
        String description(DeviceProviderCredential credential) {
            return credential.getDeviceCredentialName() + "（" + description + "）";
        }

        /** 任务体（按 creditedId 创建） */
        Runnable body(String credId) {
            return body.apply(credId);
        }
    }

    /** 离线检测 — 按凭据维度 */
    private Runnable executeOfflineDetect(String credId) {
        return () -> doOfflineCheck(deviceService.listByCredentialId(credId));
    }

    /** 设备目录同步 — 按凭据维度 */
    private Runnable executeDeviceSyncCredential(String credId) {
        return () -> syncService.syncDevicesByCredential(credId);
    }

    /** 组织同步 — 按凭据维度 */
    private Runnable executeOrgSyncCredential(String credId) {
        return () -> syncService.syncOrganizationsByCredential(credId);
    }
}
