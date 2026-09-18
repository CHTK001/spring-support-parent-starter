package com.chua.starter.project.support.scheduler;

import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.service.WatchdogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 守护进程时间轮调度器（HashedWheelTimer 思路，自实现，不引第三方）
 * <p>
 * 统一 1s tick 驱动环形槽位；每个项目按各自的
 * {@code msProjectWatchdogInterval} 换算到期 tick 数后入对应槽位，
 * 到期即由单守护线程依次执行检测，完成后按间隔重新入轮。
 * 项目级互斥：同一项目一轮未跑完时不重复触发。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectWatchdogWheel {

    /** 时间轮槽位数（32 × 1s = 一圈 32s，更长间隔靠多圈计数） */
    private static final int SLOT_COUNT = 32;

    /** 单 tick 时长（毫秒） */
    private static final long TICK_MS = 1000L;

    /** 守护检测服务 */
    private final WatchdogService watchdogService;

    /** 槽位：每个槽位挂该 tick 到期的项目 ID */
    private final List<List<Long>> slots = newSlotArray();

    /** 每个项目当前挂起圈数（轮转超过一圈仍未到期的补圈数） */
    private final ConcurrentHashMap<Long, Integer> pendingLaps = new ConcurrentHashMap<>();

    /** 已入轮项目集合（防重复入轮） */
    private final ConcurrentHashMap<Long, Boolean> scheduled = new ConcurrentHashMap<>();

    /** 正在执行的项目（防同项目重入） */
    private final ConcurrentHashMap<Long, Boolean> running = new ConcurrentHashMap<>();

    /** 时间轮驱动线程 */
    private ScheduledExecutorService wheelExecutor;

    /** 守护任务执行线程（与轮转线程分离，避免检测阻塞整个时间轮） */
    private ScheduledExecutorService runExecutor;

    private int cursor = 0;

    /**
    * 应用就绪后启动时间轮
    */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        wheelExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "project-watchdog-wheel");
            t.setDaemon(true);
            return t;
        });
        runExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "project-watchdog-run");
            t.setDaemon(true);
            return t;
        });
        scheduleAllEnabled();
        wheelExecutor.scheduleAtFixedRate(this::onTick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        log.info("[watchdog] 时间轮已启动：{} 槽 × {}ms tick，入轮项目 {} 个",
                SLOT_COUNT, TICK_MS, scheduled.size());
    }

    /**
    * 单 tick：轮转游标，执行该槽位到期的项目，并把项目重新挂回对应槽位
    */
    private void onTick() {
        cursor = (cursor + 1) % SLOT_COUNT;
        List<Long> due = slots.get(cursor);
        for (Long projectId : new ArrayList<>(due)) {
            if (!due.remove(projectId)) {
                continue;
            }
            int laps = pendingLaps.getOrDefault(projectId, 0);
            pendingLaps.remove(projectId);
            scheduled.remove(projectId);
            if (laps > 0) {
                // 尚未到期，剩余圈数继续挂到当前槽位
                pendingLaps.merge(projectId, laps, Integer::sum);
                due.add(projectId);
                continue;
            }
            runExecutor.execute(() -> runOnce(projectId));
        }
    }

    /**
    * 执行单个项目一轮检测，完成后按项目间隔重新入轮
    *
    * @param projectId 项目 标识
    */
    private void runOnce(Long projectId) {
        if (running.containsKey(projectId)) {
            // 上一轮未跑完，仅重挂不重复执行
            rearm(projectId);
            return;
        }
        running.put(projectId, Boolean.TRUE);
        try {
            MsProject project = watchdogService.reloadEnabled(projectId);
            if (project == null) {
                scheduled.remove(projectId);
                pendingLaps.remove(projectId);
                for (List<Long> slot : slots) {
                    slot.remove(projectId);
                }
                return;
            }
            watchdogService.checkOne(project);
            rearm(projectId);
        } catch (Exception e) {
            log.warn("[watchdog] 项目 {} 检测异常: {}", projectId, e.getMessage());
            rearm(projectId);
        } finally {
            running.remove(projectId);
        }
    }

    /**
    * 按项目当前间隔重新挂入时间轮
    *
    * @param projectId 项目 标识
    */
    private void rearm(Long projectId) {
        int intervalSec = watchdogService.watchdogIntervalOf(projectId);
        long totalMs = intervalSec * 1000L;
        int delayTicks = Math.max(1, (int) Math.ceil(totalMs / (double) TICK_MS));
        int targetSlot = (cursor + delayTicks) % SLOT_COUNT;
        int laps = delayTicks / SLOT_COUNT;
        pendingLaps.put(projectId, Math.max(laps, 0));
        slots.get(targetSlot).add(projectId);
        scheduled.put(projectId, Boolean.TRUE);
    }

    /**
    * 全量启用守护的项目入轮（启动时 + 外部触发重载）
    */
    public void scheduleAllEnabled() {
        List<MsProject> projects = watchdogService.enabledProjects();
        for (MsProject p : projects) {
            int intervalSec = watchdogService.watchdogIntervalOf(p.getId());
            int delayTicks = Math.max(1, intervalSec);
            int targetSlot = (cursor + delayTicks) % SLOT_COUNT;
            pendingLaps.put(p.getId(), delayTicks / SLOT_COUNT);
            slots.get(targetSlot).add(p.getId());
            scheduled.put(p.getId(), Boolean.TRUE);
        }
        log.info("[watchdog] 调度 {} 个启用守护的项目", projects.size());
    }

    /**
    * 新增/变更项目守护配置后调用：先摘除旧槽位再按新间隔入轮
    *
    * @param projectId 项目 标识
    */
    public void reschedule(Long projectId) {
        for (List<Long> slot : slots) {
            slot.remove(projectId);
        }
        pendingLaps.remove(projectId);
        if (watchdogService.isEnabled(projectId)) {
            rearm(projectId);
        } else {
            scheduled.remove(projectId);
        }
    }

    /**
    * 创建槽位数组
    *
    * @return 新槽位数组
    */
    private static List<List<Long>> newSlotArray() {
        List<List<Long>> array = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            array.add(new java.util.concurrent.CopyOnWriteArrayList<>());
        }
        return array;
    }
}
