package com.chua.payment.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.entity.PaymentSchedulerConfig;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.PaymentSchedulerConfigMapper;
import com.chua.payment.support.task.PaymentManagedTask;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 支付动态调度任务管理器，基于内部线程池执行支付订单超时取消、通知重试等定时任务。
 * <p>
 * 该实现仅在 {@code engine=internal} 时生效，负责 payment 模块内部的本地调度。
 * 切换到 {@code engine=job} 后，统一由 job-starter 轮询任务表并触发业务 Job。
 * </p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "internal", matchIfMissing = true)
public class PaymentTaskSchedulerManager implements SmartInitializingSingleton {

    /** Payment调度器配置mapper */
    private final PaymentSchedulerConfigMapper paymentSchedulerConfigMapper;
    /** 任务调度器 */
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, PaymentManagedTask> taskRegistry = new LinkedHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    /**
     * 创建 PaymentTaskSchedulerManager 实例
     * @param paymentSchedulerConfigMapper paymentSchedulerConfigMapper
     * @param taskScheduler taskScheduler
     * @param managedTasks managedTasks
     */
    public PaymentTaskSchedulerManager(PaymentSchedulerConfigMapper paymentSchedulerConfigMapper,
                                       @Qualifier("paymentThreadPoolTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
                                       List<PaymentManagedTask> managedTasks) {
        this.paymentSchedulerConfigMapper = paymentSchedulerConfigMapper;
        this.taskScheduler = taskScheduler;
        managedTasks.stream()
                .sorted(Comparator.comparing(PaymentManagedTask::taskKey))
                .forEach(task -> this.taskRegistry.put(task.taskKey(), task));
    }

    @Override
    /** AfterSingletonsInstantiated */
    public void afterSingletonsInstantiated() {
        syncDefaults();
        refreshAll();
    }

    /** ListTasks */
    public synchronized List<PaymentSchedulerTaskVO> listTasks() {
        syncDefaults();
        Map<String, PaymentSchedulerConfig> configMap = loadConfigMap();
        return taskRegistry.values().stream()
                .map(task -> toTaskVO(task, configMap.get(task.taskKey())))
                .toList();
    }

    /** 更新Task */
    public synchronized PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        PaymentManagedTask task = requireTask(taskKey);
        PaymentSchedulerConfig config = requireConfig(taskKey);

        if (dto == null) {
            throw new PaymentException("任务更新内容不能为空");
        }
        if (StringUtils.hasText(dto.getCronExpression())) {
            validateCron(dto.getCronExpression());
            config.setCronExpression(dto.getCronExpression().trim());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        paymentSchedulerConfigMapper.updateById(config);
        refreshTask(taskKey);
        return toTaskVO(task, requireConfig(taskKey));
    }

    /** TriggerTask */
    public synchronized PaymentSchedulerTaskVO triggerTask(String taskKey) {
        PaymentManagedTask task = requireTask(taskKey);
        executeTask(task, requireConfig(taskKey));
        return toTaskVO(task, requireConfig(taskKey));
    }

    /** RefreshAll */
    public synchronized void refreshAll() {
        taskRegistry.keySet().forEach(this::refreshTask);
    }

    /** RefreshTask */
    public synchronized void refreshTask(String taskKey) {
        PaymentManagedTask task = requireTask(taskKey);
        PaymentSchedulerConfig config = requireConfig(taskKey);
        cancelTask(taskKey);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        validateCron(config.getCronExpression());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTask(task, requireConfig(taskKey)),
                new CronTrigger(config.getCronExpression()));
        if (future != null) {
            scheduledFutures.put(taskKey, future);
        }
    }

    /** 执行Task */
    private void executeTask(PaymentManagedTask task, PaymentSchedulerConfig config) {
        LocalDateTime startedAt = LocalDateTime.now();
        config.setLastStartedAt(startedAt);
        config.setLastRunStatus("RUNNING");
        config.setLastRunMessage("任务开始执行");
        paymentSchedulerConfigMapper.updateById(config);

        try {
            task.execute();
            config.setLastRunStatus("SUCCESS");
            config.setLastRunMessage("任务执行成功");
        } catch (Throwable e) {
            log.error("支付调度任务执行失败: taskKey={}", task.taskKey(), e);
            config.setLastRunStatus("FAILED");
            config.setLastRunMessage(abbreviate(resolveErrorMessage(e), 500));
            throw rethrow(e, task.taskKey());
        } finally {
            config.setLastFinishedAt(LocalDateTime.now());
            paymentSchedulerConfigMapper.updateById(config);
        }
    }

    /** SyncDefaults */
    private synchronized void syncDefaults() {
        Map<String, PaymentSchedulerConfig> configMap = loadConfigMap();
        for (PaymentManagedTask task : taskRegistry.values()) {
            PaymentSchedulerConfig existing = configMap.get(task.taskKey());
            if (existing != null) {
                boolean needUpdate = false;
                if (!StringUtils.hasText(existing.getTaskName())) {
                    existing.setTaskName(task.taskName());
                    needUpdate = true;
                }
                if (!StringUtils.hasText(existing.getCronExpression())) {
                    existing.setCronExpression(task.defaultCron());
                    needUpdate = true;
                }
                if (existing.getEnabled() == null) {
                    existing.setEnabled(Boolean.TRUE);
                    needUpdate = true;
                }
                if (!StringUtils.hasText(existing.getDescription())) {
                    existing.setDescription(task.description());
                    needUpdate = true;
                }
                if (needUpdate) {
                    paymentSchedulerConfigMapper.updateById(existing);
                }
                continue;
            }

            PaymentSchedulerConfig config = new PaymentSchedulerConfig();
            config.setTaskKey(task.taskKey());
            config.setTaskName(task.taskName());
            config.setCronExpression(task.defaultCron());
            config.setEnabled(Boolean.TRUE);
            config.setDescription(task.description());
            config.setLastRunStatus("NEVER");
            config.setLastRunMessage("任务尚未执行");
            paymentSchedulerConfigMapper.insert(config);
        }
    }

    /** 加载ConfigMap */
    private Map<String, PaymentSchedulerConfig> loadConfigMap() {
        return paymentSchedulerConfigMapper.selectList(new LambdaQueryWrapper<PaymentSchedulerConfig>()
                        .orderByAsc(PaymentSchedulerConfig::getId))
                .stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getTaskKey(), item), LinkedHashMap::putAll);
    }

    /** ToTaskVO */
    private PaymentSchedulerTaskVO toTaskVO(PaymentManagedTask task, PaymentSchedulerConfig config) {
        PaymentSchedulerTaskVO vo = new PaymentSchedulerTaskVO();
        vo.setTaskKey(task.taskKey());
        vo.setTaskName(config != null && StringUtils.hasText(config.getTaskName()) ? config.getTaskName() : task.taskName());
        vo.setCronExpression(config != null ? config.getCronExpression() : task.defaultCron());
        vo.setEnabled(config != null ? Boolean.TRUE.equals(config.getEnabled()) : Boolean.TRUE);
        vo.setDescription(config != null && StringUtils.hasText(config.getDescription()) ? config.getDescription() : task.description());
        vo.setScheduled(isScheduled(task.taskKey()));
        vo.setNextExecutionTime(resolveNextExecutionTime(vo.getCronExpression(), vo.getEnabled()));
        if (config != null) {
            vo.setLastStartedAt(config.getLastStartedAt());
            vo.setLastFinishedAt(config.getLastFinishedAt());
            vo.setLastRunStatus(config.getLastRunStatus());
            vo.setLastRunMessage(config.getLastRunMessage());
        }
        return vo;
    }

    /** 解析NextExecutionTime */
    private LocalDateTime resolveNextExecutionTime(String cronExpression, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled) || !StringUtils.hasText(cronExpression) || !CronExpression.isValidExpression(cronExpression)) {
            return null;
        }
        return CronExpression.parse(cronExpression).next(LocalDateTime.now());
    }

    /** 是否Scheduled */
    private boolean isScheduled(String taskKey) {
        ScheduledFuture<?> future = scheduledFutures.get(taskKey);
        return future != null && !future.isCancelled();
    }

    /** CancelTask */
    private void cancelTask(String taskKey) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** RequireTask */
    private PaymentManagedTask requireTask(String taskKey) {
        PaymentManagedTask task = taskRegistry.get(taskKey);
        if (task == null) {
            throw new PaymentException("未知调度任务: " + taskKey);
        }
        return task;
    }

    /** RequireConfig */
    private PaymentSchedulerConfig requireConfig(String taskKey) {
        PaymentSchedulerConfig config = paymentSchedulerConfigMapper.selectOne(new LambdaQueryWrapper<PaymentSchedulerConfig>()
                .eq(PaymentSchedulerConfig::getTaskKey, taskKey)
                .last("limit 1"));
        if (config == null) {
            throw new PaymentException("调度任务配置不存在: " + taskKey);
        }
        return config;
    }

    /** 校验Cron */
    private void validateCron(String cronExpression) {
        if (!StringUtils.hasText(cronExpression) || !CronExpression.isValidExpression(cronExpression.trim())) {
            throw new PaymentException("无效的 Cron 表达式: " + cronExpression);
        }
    }

    /** Abbreviate */
    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /** 解析记录错误Message */
    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (StringUtils.hasText(throwable.getMessage())) {
            return throwable.getMessage().trim();
        }
        return throwable.getClass().getSimpleName();
    }

    /** Rethrow */
    private RuntimeException rethrow(Throwable throwable, String taskKey) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new PaymentException("支付调度任务执行失败: " + taskKey, throwable);
    }
}

