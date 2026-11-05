// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * 该实现仅在 {@code engine=internal} 时生效，负责“插件”模块内部的本地调度；
 * 切换为 {@code engine=job} 后，统一由“作业”插件轮询任务表并触发业务作业。
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

    /**
     * 支付调度器配置映射
     */
    private final PaymentSchedulerConfigMapper paymentSchedulerConfigMapper;
    /**
     * 任务调度器
     */
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, PaymentManagedTask> taskRegistry = new LinkedHashMap<>(); // 任务注册表
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>(); // 调度句柄

    /**
     * 构造支付任务调度器管理器实例。
     * @param paymentSchedulerConfigMapper 支付调度器配置映射
     * @param taskScheduler 任务调度器
     * @param managedTasks 受管任务集合
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
    /**
     * 容器中所有单例实例化完成之后回调
     */
    public void afterSingletonsInstantiated() {
        /**
         * 同步默认配置
         */
        syncDefaults();
        /**
         * 刷新全部任务
         */
        refreshAll();
    }

    /**
     * 列出全部任务
     *
     * @return 任务列表
     */
    public synchronized List<PaymentSchedulerTaskVO> listTasks() {
        /**
         * 同步默认配置
         */
        syncDefaults();
        Map<String, PaymentSchedulerConfig> configMap = loadConfigMap();
        return taskRegistry.values().stream()
                .map(task -> toTaskVO(task, configMap.get(task.taskKey())))
                .toList();
    }

    /**
     * 更新任务配置
     *
     * @param taskKey 任务标识
     * @param dto dto
     * @return 更新后的任务
     */
    public synchronized PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        PaymentManagedTask task = requireTask(taskKey);
        PaymentSchedulerConfig config = requireConfig(taskKey);

        if (dto == null) {
            throw new PaymentException("任务更新内容不能为空");
        }
        if (StringUtils.hasText(dto.cronExpression())) {
            /**
             * 校验 cron 表达式
             * @param dto.cronExpression() 获取 cron 表达式
             */
            validateCron(dto.cronExpression());
            config.setCronExpression(dto.cronExpression().trim());
        }
        if (dto.enabled() != null) {
            config.setEnabled(dto.enabled());
        }
        paymentSchedulerConfigMapper.updateById(config);
        /**
         * 刷新任务调度
         * @param taskKey 任务标识
         */
        refreshTask(taskKey);
        return toTaskVO(task, requireConfig(taskKey));
    }

    /**
     * 手动触发任务
     *
     * @param taskKey 任务标识
     * @return 触发后的任务
     */
    public synchronized PaymentSchedulerTaskVO triggerTask(String taskKey) {
        PaymentManagedTask task = requireTask(taskKey);
        /**
         * 立即执行一次任务
         * @param task 任务
         * @param requireConfig(taskKey) 按任务标识获取配置
         */
        executeTask(task, requireConfig(taskKey));
        return toTaskVO(task, requireConfig(taskKey));
    }

    /**
     * 刷新全部任务
     */
    public synchronized void refreshAll() {
        taskRegistry.keySet().forEach(this::refreshTask);
    }

    /**
     * 刷新单个任务
     *
     * @param taskKey 任务标识
     */
    public synchronized void refreshTask(String taskKey) {
        PaymentManagedTask task = requireTask(taskKey);
        PaymentSchedulerConfig config = requireConfig(taskKey);
        /**
         * 先取消旧的调度
         * @param taskKey 任务标识
         */
        cancelTask(taskKey);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        /**
         * 校验 cron 表达式
         * @param config.getCronExpression() 获取配置中的 cron 表达式
         */
        validateCron(config.getCronExpression());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTask(task, requireConfig(taskKey)),
                new CronTrigger(config.getCronExpression()));
        if (future != null) {
            scheduledFutures.put(taskKey, future);
        }
    }

    /**
     * 执行任务
     *
     * @param task 任务
     * @param config 配置
     */
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
            log.error("支付调度任务执行失败 taskKey={}", task.taskKey(), e);
            config.setLastRunStatus("FAILED");
            config.setLastRunMessage(abbreviate(resolveErrorMessage(e), 500));
            throw rethrow(e, task.taskKey());
        } finally {
            config.setLastFinishedAt(LocalDateTime.now());
            paymentSchedulerConfigMapper.updateById(config);
        }
    }

    /**
     * 同步默认配置：补全缺失字段并初始化缺失的任务配置
     */
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

    /**
     * 加载全部任务配置并按任务标识索引
     *
     * @return 任务配置映射
     */
    private Map<String, PaymentSchedulerConfig> loadConfigMap() {
        return paymentSchedulerConfigMapper.selectList(new LambdaQueryWrapper<PaymentSchedulerConfig>()
                        .orderByAsc(PaymentSchedulerConfig::getId))
                .stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getTaskKey(), item), LinkedHashMap::putAll);
    }

    /**
     * 转换为任务视图对象
     *
     * @param task 任务
     * @param config 配置
     * @return 任务视图对象
     */
    private PaymentSchedulerTaskVO toTaskVO(PaymentManagedTask task, PaymentSchedulerConfig config) {
        String cronExpression = config != null ? config.getCronExpression() : task.defaultCron();
        Boolean enabled = config != null ? Boolean.TRUE.equals(config.getEnabled()) : Boolean.TRUE;
        return new PaymentSchedulerTaskVO(
                task.taskKey(),
                config != null && StringUtils.hasText(config.getTaskName()) ? config.getTaskName() : task.taskName(),
                cronExpression,
                enabled,
                config != null && StringUtils.hasText(config.getDescription()) ? config.getDescription() : task.description(),
                isScheduled(task.taskKey()),
                resolveNextExecutionTime(cronExpression, enabled),
                config != null ? config.getLastStartedAt() : null,
                config != null ? config.getLastFinishedAt() : null,
                config != null ? config.getLastRunStatus() : null,
                config != null ? config.getLastRunMessage() : null);
    }

    /**
     * 计算下一次执行时间
     *
     * @param cronExpression cron 表达式
     * @param enabled 是否已启用
     * @return 下一次执行时间，未启用或表达式非法时返回 null
     */
    private LocalDateTime resolveNextExecutionTime(String cronExpression, Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled) || !StringUtils.hasText(cronExpression) || !CronExpression.isValidExpression(cronExpression)) {
            return null;
        }
        return CronExpression.parse(cronExpression).next(LocalDateTime.now());
    }

    /**
     * 判断任务是否已排期
     *
     * @param taskKey 任务标识
     * @return 是否已排期
     */
    private boolean isScheduled(String taskKey) {
        ScheduledFuture<?> future = scheduledFutures.get(taskKey);
        return future != null && !future.isCancelled();
    }

    /**
     * 取消任务排期
     *
     * @param taskKey 任务标识
     */
    private void cancelTask(String taskKey) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 获取任务，不存在时抛出异常
     *
     * @param taskKey 任务标识
     * @return 任务
     */
    private PaymentManagedTask requireTask(String taskKey) {
        PaymentManagedTask task = taskRegistry.get(taskKey);
        if (task == null) {
            throw new PaymentException("未找到调度任务: " + taskKey);
        }
        return task;
    }

    /**
     * 获取任务配置，不存在时抛出异常
     *
     * @param taskKey 任务标识
     * @return 任务配置
     */
    private PaymentSchedulerConfig requireConfig(String taskKey) {
        PaymentSchedulerConfig config = paymentSchedulerConfigMapper.selectOne(new LambdaQueryWrapper<PaymentSchedulerConfig>()
                .eq(PaymentSchedulerConfig::getTaskKey, taskKey)
                .last("limit 1"));
        if (config == null) {
            throw new PaymentException("调度任务配置不存在: " + taskKey);
        }
        return config;
    }

    /**
     * 校验 cron 表达式合法性
     *
     * @param cronExpression cron 表达式
     */
    private void validateCron(String cronExpression) {
        if (!StringUtils.hasText(cronExpression) || !CronExpression.isValidExpression(cronExpression.trim())) {
            throw new PaymentException("无效的 Cron 表达式: " + cronExpression);
        }
    }

    /**
     * 按最大长度截断文本
     * @param maxLength 最大长度
     * @param text 文本
     * @return 返回值
    */
    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * 解析异常消息
     *
     * @param throwable 异常
     * @return 解析出的异常消息
     */
    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (StringUtils.hasText(throwable.getMessage())) {
            return throwable.getMessage().trim();
        }
        return throwable.getClass().getSimpleName();
    }

    /**
     * 统一异常抛出：受检异常包装为 PaymentException
     * @param taskKey 任务标识
     * @param throwable 异常
     * @return 返回值
    */
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