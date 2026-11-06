// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.support.scheduler;

import com.chua.common.support.task.scheduler.ScheduledTask;
import com.chua.common.support.task.scheduler.TimeScheduler;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.entity.DeviceTaskState;
import com.chua.starter.device.support.service.DeviceAccessRecordService;
import com.chua.starter.device.support.service.DeviceEventService;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.device.support.service.DeviceStatusService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.device.support.service.DeviceTaskStateService;
import com.chua.starter.device.spi.DeviceProvider;
import com.chua.starter.device.spi.DeviceSession;
import com.chua.starter.device.spi.model.DeviceCapability;
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
 *   <li>仍由时间轮统一调度的任务：<code>deviceAccessExtract</code>（门禁记录抽取）；</li>
 *   <li>凭据级任务：每套提供商凭据独立拥有 4 个任务，名称为
 *   <code>offlineDetect_{credId}</code>/<code>eventPoll_{credId}</code>/
 *   <code>deviceSync_{credId}</code>/<code>orgSync_{credId}</code>，
 *   行为由 {@link DeviceProviderCredential} 的能力配置驱动（开关 + 间隔秒），默认为关闭；
 *   在离线检测/事件轮询/设备同步/组织同步都不再有全局入口，哪套凭据开启就只跑哪套。</li>
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
     * 仍由时间轮统一调度的任务名
     */
    private static final String TASK_ACCESS_EXTRACT = "deviceAccessExtract";

    /**
     * 凭据级任务前缀（任务名 = 前缀 + '_' + 凭据 ID）
     */
    private static final String PREFIX_OFFLINE_DETECT = "offlineDetect";
    private static final String PREFIX_EVENT_POLL = "eventPoll";
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
    private final DeviceService deviceService;
    private final DeviceStatusService statusService;
    private final DeviceSessionManager sessionManager;
    private final DeviceEventService eventService;
    private final DeviceSyncService syncService;
    private final DeviceAccessRecordService accessRecordService;
    private final DeviceProviderCredentialService credentialService;
    /** 厂商 SPI：凭据能不能开某项能力，以厂商描述符的能力位为准 */
    private final List<DeviceProvider> deviceProviders;

    private TimeScheduler timeScheduler;
    /** 全局 Tick 注册表（任务名 → 注册的定时任务） */
    private final Map<String, ScheduledTask> tickTasks = new ConcurrentHashMap<>();
    /** 凭据级任务注册表（任务名 → 注册的定时任务） */
    private final Map<String, ScheduledTask> credentialTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        try {
            timeScheduler = TimeScheduler.of();
            // 仅保留仍属于平台级的 tick（门禁记录抽取）；其余能力全部下沉到凭据级
            tick(TASK_ACCESS_EXTRACT, this::executeAccessExtract);
            // 全部凭据的 Tick（从 `device_provider_credential` 的能力配置取开关与间隔）
            refresh();
            log.info("设备定时调度器已启动，tick 间隔 {} 秒（{}）；凭据级任务按实例独立注册",
                    SCHEDULE_INTERVAL_SEC, TASK_ACCESS_EXTRACT);
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
     * @param credentialId 凭据 ID（已删除时撤掉其名下全部 Tick）
     */
    public void refresh(String credentialId) {
        DeviceProviderCredential credential = credentialService.get(credentialId);
        if (credential == null) {
            // 凭据已删除：把它名下的 Tick 全撤掉，避免继续按旧配置跑
            for (CredentialTask source : CredentialTask.values()) {
                cancel(source.name(credentialId), credentialTasks);
            }
            return;
        }
        refreshCredential(credential);
    }

    /**
     * 按凭据重注册 4 个 Tick。
     * <p>先撤掉该凭据名下的旧 Tick，再按最新配置判定：凭据停用、开关关闭、
     * 间隔为空或非正数时不注册任务。</p>
     *
     * @param credential 凭据
     */
    private void refreshCredential(DeviceProviderCredential credential) {
        String credId = credential.getDeviceCredentialId();
        if (credId == null) {
            return;
        }
        for (CredentialTask source : CredentialTask.values()) {
            String taskName = source.name(credId);
            // 先撤旧的：配置改成关闭时也必须落地，而不是留着旧 Tick 继续跑
            cancel(taskName, credentialTasks);
            if (!acts(source, credential)) {
                continue;
            }
            // 间隔：凭据高级配置为准（>0），未配置时回落到状态行（任务页改过）/ 默认 30 秒
            Integer configured = source.intervalOf(credential);
            Integer interval = configured != null && configured > 0
                    ? configured
                    : taskStateInterval(taskName);
            if (interval == null || interval <= 0) {
                continue;
            }
            ScheduledTask task = timeScheduler.scheduleFixedRate(taskName,
                    () -> tickCredential(source, credId),
                    interval, TimeUnit.SECONDS);
            // 补种状态行让任务出现在任务列表里，并把生效间隔写回（行上显示与实际 tick 一致）
            taskStateService.seed(taskName, interval, source.description(credential));
            taskStateService.updateInterval(taskName, interval);
            credentialTasks.put(taskName, task);
            log.info("凭据级定时任务已注册: name={}, type={}, interval={}秒", taskName, source, interval);
        }
    }

    /**
     * 凭据级任务的 Tick 门：read config → shouldRun → markRunning → body → markFinished
     */
    private void tickCredential(CredentialTask source, String credId) {
        DeviceProviderCredential credential = credentialService.get(credId);
        if (credential == null || !acts(source, credential)) {
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
            body(source, credId).run();
        } finally {
            taskStateService.markFinished(taskName);
        }
    }

    /**
     * 立即执行（绕过时间轮 tick 的间隔判定）
     *
     * @param taskName 任务名（全局任务或凭据级任务）
     * @return 是否执行成功（未知任务名或任务体抛错为 {@code false}）
     */
    public boolean runNow(String taskName) {
        CredentialTask source = sourceOf(taskName);
        String credId = source == null ? null : credentialIdOf(taskName);
        if (source != null && credId != null) {
            return runTracked(taskName, body(source, credId));
        }
        return switch (taskName) {
            case TASK_ACCESS_EXTRACT -> runTracked(taskName, () -> {
                LocalDateTime end = LocalDateTime.now();
                accessRecordService.extractRecent(end.minusMinutes(10), end);
            });
            default -> {
                log.warn("未实现的任务名: {}", taskName);
                yield false;
            }
        };
    }

    /**
     * 立即执行一段任务体：有对应状态行时顺带记录执行时间与 running 态，
     * 没有状态行（凭据级任务尚未补种）也不影响执行本身。
     *
     * @param taskName 任务名
     * @param body     任务体
     * @return 是否执行成功（异常只记日志）
     */
    private boolean runTracked(String taskName, Runnable body) {
        boolean tracked = taskStateService.markRunning(taskName);
        try {
            body.run();
            return true;
        } catch (Exception e) {
            log.warn("立即执行失败: name={}, message={}", taskName, e.getMessage(), e);
            return false;
        } finally {
            if (tracked) {
                taskStateService.markFinished(taskName);
            }
        }
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

    /** 取任务状态行上的间隔（任务页改过就在行上），没有则回落默认 30 秒 */
    private Integer taskStateInterval(String taskName) {
        DeviceTaskState stateRow;
        try {
            stateRow = taskStateService.getState(taskName);
        } catch (Exception e) {
            // 状态行尚未补种时不阻塞
            log.debug("取任务状态行失败: {}", taskName, e);
            return DEFAULT_INTERVAL_SEC;
        }
        if (stateRow != null && stateRow.getDeviceTaskStateInterval() != null && stateRow.getDeviceTaskStateInterval() > 0) {
            return stateRow.getDeviceTaskStateInterval();
        }
        return DEFAULT_INTERVAL_SEC;
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
     * 凭据级任务分类（能力位 + 开关 + 间隔）。
     *
     * <p>能力位来自厂商描述符：厂商没这个能力时，即使凭据开关被打开也不注册任务，
     * 避免生成永远拉不到数据的空转任务。</p>
     */
    private enum CredentialTask {
        OFFLINE_DETECT(PREFIX_OFFLINE_DETECT, "在离线检测", DeviceCapability.DEVICE,
                DeviceProviderCredential::getDeviceCredentialOfflineDetectEnabled,
                DeviceProviderCredential::getDeviceCredentialOfflineDetectInterval),
        EVENT_POLL(PREFIX_EVENT_POLL, "事件轮询", DeviceCapability.EVENT,
                DeviceProviderCredential::getDeviceCredentialEventPollEnabled,
                DeviceProviderCredential::getDeviceCredentialEventPollInterval),
        DEVICE_SYNC(PREFIX_DEVICE_SYNC, "设备自动同步", DeviceCapability.DEVICE,
                DeviceProviderCredential::getDeviceCredentialDeviceSyncEnabled,
                DeviceProviderCredential::getDeviceCredentialDeviceSyncInterval),
        ORG_SYNC(PREFIX_ORG_SYNC, "组织自动同步", DeviceCapability.ORG,
                DeviceProviderCredential::getDeviceCredentialOrgSyncEnabled,
                DeviceProviderCredential::getDeviceCredentialOrgSyncInterval);

        private final String prefix;
        private final String description;
        private final DeviceCapability capability;
        private final java.util.function.Function<DeviceProviderCredential, Boolean> enabled;
        private final java.util.function.Function<DeviceProviderCredential, Integer> interval;

        CredentialTask(String prefix, String description, DeviceCapability capability,
                       java.util.function.Function<DeviceProviderCredential, Boolean> enabled,
                       java.util.function.Function<DeviceProviderCredential, Integer> interval) {
            this.prefix = prefix;
            this.description = description;
            this.capability = capability;
            this.enabled = enabled;
            this.interval = interval;
        }

        /** 开关是否开启（凭据整体停用时一律不跑） */
        boolean acts(DeviceProviderCredential credential) {
            return Boolean.TRUE.equals(credential.getDeviceCredentialEnabled())
                    && Boolean.TRUE.equals(enabled.apply(credential));
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
    }

    /**
     * 凭据是否该跑某任务：凭据开关开启、且厂商描述符声明了对应能力位。
     *
     * @param source     任务来源
     * @param credential 凭据
     * @return 是否注册/执行
     */
    private boolean acts(CredentialTask source, DeviceProviderCredential credential) {
        return source.acts(credential)
                && supports(credential.getDeviceCredentialVendor(), source.capability);
    }

    /**
     * 厂商是否声明了某项能力。
     *
     * @param vendor     厂商标识
     * @param capability 能力位
     * @return 支持才返回 {@code true}
     */
    private boolean supports(String vendor, DeviceCapability capability) {
        if (vendor == null || deviceProviders == null) {
            return false;
        }
        return deviceProviders.stream()
                .filter(provider -> vendor.equalsIgnoreCase(provider.getVendor()))
                .findFirst()
                .map(provider -> provider.descriptor().capabilities().contains(capability))
                .orElse(false);
    }

    /**
     * 凭据级任务体：按来源把任务要求成可执行的 Runnable
     */
    private Runnable body(CredentialTask source, String credId) {
        return switch (source) {
            case OFFLINE_DETECT -> () -> doOfflineCheck(deviceService.listCheckableDevicesByCredentialId(credId));
            case EVENT_POLL -> () -> eventService.pollDevicesByCredential(credId);
            case DEVICE_SYNC -> () -> syncService.syncDevicesByCredential(credId);
            case ORG_SYNC -> () -> syncService.syncOrganizationsByCredential(credId);
        };
    }
}