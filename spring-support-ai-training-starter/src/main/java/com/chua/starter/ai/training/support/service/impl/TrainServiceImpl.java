package com.chua.starter.ai.training.support.service.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.config.TrainProperties;
import com.chua.starter.ai.training.support.domain.TrainTask;
import com.chua.starter.ai.training.support.event.TrainLifecycleEvent;
import com.chua.starter.ai.training.support.event.TrainStage;
import com.chua.starter.ai.training.support.service.TrainService;
import com.chua.starter.ai.training.support.spi.TrainContext;
import com.chua.starter.ai.training.support.spi.TrainProvider;
import com.chua.starter.ai.training.support.spi.TrainProviders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 训练服务实现：任务管理 + 异步训练调度 + 生命周期事件广播。
 *
 * <p>职责为编排领域方法：创建任务、启动训练线程、查询状态，并在每个
 * {@link TrainStage} 外发 {@link TrainLifecycleEvent}（供 @EventListener 注入监听）；
 * 具体状态机与进度更新行为封装在 {@link TrainTask} 领域对象内部。</p>
 *
 * <p>训练引擎通过 {@link TrainProviders}（Java SPI）按 type 分发，默认 resnet50 走 DL4J，
 * 引擎 SPI 化后新增 YOLO（python-http）只需追加实现并在 META-INF/services 注册。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainServiceImpl implements TrainService {

    private final TrainProperties properties;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 内存任务存储（key=taskId）。
     */
    private final Map<String, TrainTask> taskStore = new ConcurrentHashMap<>();

    @Override
    public TrainTask createTask(String originalFileName, String dataPath) {
        TrainTask task = TrainTask.create(originalFileName, dataPath);
        taskStore.put(task.getId(), task);
        log.info("[Train] 任务已创建: id={} file={}", task.getId(), originalFileName);
        publishEvent(task.getId(), TrainStage.TASK_CREATED, task.getProgress(), null, null);
        return task;
    }

    @Override
    public void startTraining(String taskId) throws Exception {
        TrainTask task = taskStore.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 领域对象自校验并流转状态（PENDING → RUNNING）
        task.start();

        // 引擎按类型从 SPI 注册表解析（type 缺省兼容默认 DL4J）
        TrainProvider provider = TrainProviders.require(TrainProviders.DEFAULT_TYPE);
        TrainContext ctx = new TrainContext(
                properties.getModelPath(),
                properties.getSavePath(),
                task.getDataPath());

        // 异步训练线程（引擎回调 → 领域监听器更新进度 + 平台外发生命周期事件）
        Thread trainThread = new Thread(() -> {
            TrainListener taskListener = task.createListener();
            TrainListener wired = wire(task, taskListener);
            try {
                log.info("[Train] 开始训练: taskId={}, data={}, engine={}",
                        taskId, ctx.dataRootPath(), provider.engine());
                publishEvent(taskId, TrainStage.TRAIN_STARTED, task.getProgress(), null, null);

                TrainResult result = provider.train(task.getArgument(), ctx, wired);

                if (result.isSuccess()) {
                    task.succeed(result);
                    publishEvent(taskId, TrainStage.TRAIN_SUCCEEDED, task.getProgress(),
                            "F1=" + result.getBestScore() + ", 耗时=" + result.getElapsedMs() + "ms", null);
                    log.info("[Train] 训练完成: taskId={}, F1={}, 耗时={}ms",
                            taskId, result.getBestScore(), result.getElapsedMs());
                } else {
                    task.fail(result.getErrorMessage());
                    publishEvent(taskId, TrainStage.TRAIN_FAILED, task.getProgress(),
                            result.getErrorMessage(), null);
                    log.error("[Train] 训练失败: taskId={}, error={}", taskId, result.getErrorMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.cancel();
                publishEvent(taskId, TrainStage.TRAIN_CANCELLED, task.getProgress(), "训练被中断", e);
                log.warn("[Train] 训练被中断: taskId={}", taskId);
            } catch (Exception e) {
                task.fail("训练异常", e);
                publishEvent(taskId, TrainStage.TRAIN_FAILED, task.getProgress(), "训练异常", e);
                log.error("[Train] 训练异常: taskId={}", taskId, e);
            }
        }, "train-" + provider.type() + "-" + taskId);
        trainThread.setDaemon(true);
        trainThread.start();

        log.info("[Train] 异步训练已启动: taskId={}, type={}", taskId, provider.type());
    }

    @Override
    public TrainTask getTask(String taskId) {
        return taskStore.get(taskId);
    }

    @Override
    public List<TrainTask> listTasks() {
        return new ArrayList<>(taskStore.values());
    }

    @Override
    public void updateArgument(String taskId, TrainArgument argument) {
        TrainTask task = taskStore.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        if (task.getStatus() == TrainStatus.RUNNING) {
            throw new IllegalStateException("任务正在运行中，无法修改超参数: " + taskId);
        }
        // 领域对象内部完成合并（仅覆盖非空字段）
        task.mergeArgument(argument);
        TrainArgument merged = task.getArgument();
        publishEvent(taskId, TrainStage.ARGUMENT_UPDATED, task.getProgress(),
                "epoch=" + merged.getEpoch() + ", batchSize=" + merged.getBatchSize()
                        + ", nClasses=" + merged.getNClasses()
                        + ", resumeModelPath=" + merged.getResumeModelPath(), null);
        log.info("[Train] 超参数已更新: taskId={}, epoch={}, batchSize={}, nClasses={}, resumeModelPath={}",
                taskId, merged.getEpoch(), merged.getBatchSize(), merged.getNClasses(), merged.getResumeModelPath());
    }

    /**
     * 引擎监听器接线：委托给任务领域监听器更新进度，并把迭代进度外发为平台事件。
     *
     * <p>引擎层的 started/finished/failed 回调只做进度刷新，终态事件由服务在线程内
     * 根据训练结果统一外发（避免引擎回调与服务逻辑重复广播终态）。</p>
     *
     * @param task         训练任务
     * @param taskListener 任务领域监听器（负责更新 progress/status 字段）
     * @return 组合后的监听器
     */
    private TrainListener wire(TrainTask task, TrainListener taskListener) {
        return new TrainListener() {
            @Override
            public void onStarted(TrainProgress p) {
                taskListener.onStarted(p);
            }

            @Override
            public void onEpochCompleted(TrainProgress p) {
                taskListener.onEpochCompleted(p);
                publishEvent(task.getId(), TrainStage.EPOCH_PROGRESS, p, null, null);
            }

            @Override
            public void onFinished(TrainProgress p) {
                taskListener.onFinished(p);
            }

            @Override
            public void onFailed(TrainProgress p, Throwable cause) {
                taskListener.onFailed(p, cause);
            }
        };
    }

    /**
     * 外发生命周期事件（监听器异常不中断训练主流程，仅记录告警）。
     *
     * @param taskId   任务 ID
     * @param stage    生命周期阶段
     * @param progress 进度快照（可为 null）
     * @param message  附加消息（可为 null）
     * @param cause    异常原因（可为 null）
     */
    private void publishEvent(String taskId, TrainStage stage, TrainProgress progress,
                              String message, Throwable cause) {
        try {
            eventPublisher.publishEvent(new TrainLifecycleEvent(taskId, stage, progress, message, cause));
        } catch (RuntimeException e) {
            log.warn("[Train] 事件监听器执行异常已忽略: taskId={}, stage={}, error={}",
                    taskId, stage, e.getMessage());
        }
    }
}