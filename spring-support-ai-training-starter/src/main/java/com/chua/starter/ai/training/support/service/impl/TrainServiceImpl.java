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
* 引擎 SPI 化后新增 YOLO（Python-http）只需追加实现并在 META-INF/服务 注册。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainServiceImpl implements TrainService {

    private final TrainProperties properties; // 属性

    private final ApplicationEventPublisher eventPublisher; // 事件发布

    /**
    * 内存任务存储（Key=任务标识）。
    */
    private final Map<String, TrainTask> taskStore = new ConcurrentHashMap<>();

    @Override
    public TrainTask createTask(String originalFileName, String dataPath) {
        TrainTask task = TrainTask.create(originalFileName, dataPath);
        taskStore.put(task.getId(), task);
        log.info("[Train] 任务已创建: id={} file={}", task.getId(), originalFileName);
        /**
        * 发布事件。
        * @param task.getId( 任务.获取标识(
        */
        publishEvent(task.getId(), TrainStage.TASK_CREATED, task.getProgress(), null, null);
        return task;
    }

    @Override
    public void startTraining(String taskId, String type) throws Exception {
        TrainTask task = taskStore.get(taskId);
        if (task == null) {
            /**
            * IllegalArgumentException。
            * @param taskId 任务标识
            * @return IllegalArgumentException的结果
            */
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 先锁定训练类型（缺省兼容默认 DL4J；仅 PENDING 可指定）
        task.selectType(type);
 // 引擎按任务类型从 SPI 注册表解析；必须先于 任务.启动() 校验存在性——
 // 若 类型 未接入，抛异常时任务仍保持 PENDING，用户可修正 类型 后重试（不被污染成 RUNNING 僵尸）
        TrainProvider provider = TrainProviders.require(task.getType());
        // 再流转状态（引擎已确认可提供，后续不再有同步失败点）
        task.start();
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
                /**
                * 发布事件。
                * @param taskId 任务标识
                * @param TrainStage.TRAIN_STARTED trainstage.train启动
                * @param task.getProgress( 任务.获取进步(
                */
                publishEvent(taskId, TrainStage.TRAIN_STARTED, task.getProgress(), null, null);

                TrainResult result = provider.train(task.getArgument(), ctx, wired);

                if (result.isSuccess()) {
                    task.succeed(result);
                    /**
                    * 发布事件。
                    * @param taskId 任务标识
                    * @param TrainStage.TRAIN_SUCCEEDED trainStage.TRAIN_SUCCEEDED
                    * @param task.getProgress( 任务.获取进步(
                    */
                    publishEvent(taskId, TrainStage.TRAIN_SUCCEEDED, task.getProgress(),
                            "F1=" + result.getBestScore() + ", 耗时=" + result.getElapsedMs() + "ms", null);
                    log.info("[Train] 训练完成: taskId={}, F1={}, 耗时={}ms",
                            taskId, result.getBestScore(), result.getElapsedMs());
                } else {
                    task.fail(result.getErrorMessage());
                    /**
                    * 发布事件。
                    * @param taskId 任务标识
                    * @param TrainStage.TRAIN_FAILED trainstage.train失败
                    * @param task.getProgress( 任务.获取进步(
                    */
                    publishEvent(taskId, TrainStage.TRAIN_FAILED, task.getProgress(),
                            result.getErrorMessage(), null);
                    log.error("[Train] 训练失败: taskId={}, error={}", taskId, result.getErrorMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.cancel();
                /**
                * 发布事件。
                * @param taskId 任务标识
                * @param TrainStage.TRAIN_CANCELLED trainStage.TRAIN_CANCELLED
                * @param task.getProgress( 任务.获取进步(
                */
                publishEvent(taskId, TrainStage.TRAIN_CANCELLED, task.getProgress(), "训练被中断", e);
                log.warn("[Train] 训练被中断: taskId={}", taskId);
            } catch (Exception e) {
                task.fail("训练异常", e);
                /**
                * 发布事件。
                * @param taskId 任务标识
                * @param TrainStage.TRAIN_FAILED trainstage.train失败
                * @param task.getProgress( 任务.获取进步(
                */
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
            /**
            * IllegalArgumentException。
            * @param taskId 任务标识
            * @return IllegalArgumentException的结果
            */
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
 // 仅 PENDING 可修改（与领域 选择类型 同语义）：RUNNING 修改会污染进行中训练，
 // 终态(成功/失败/CANCELLED)任务无法重触发，修改无意义且误导调用方。
        if (task.getStatus() != TrainStatus.PENDING) {
            throw new IllegalStateException(
                    "任务状态为 " + task.getStatus() + "，不可修改超参数（仅 PENDING 可修改）: " + taskId);
        }
        // 领域对象内部完成合并（仅覆盖非空字段）
        task.mergeArgument(argument);
        TrainArgument merged = task.getArgument();
        /**
        * 发布事件。
        * @param taskId 任务标识
        * @param TrainStage.ARGUMENT_UPDATED trainstage.参数更新
        * @param task.getProgress( 任务.获取进步(
        */
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
    * @param taskListener 任务领域监听器（负责更新 进步/状态 字段）
    * @return 组合后的监听器
    */
    private TrainListener wire(TrainTask task, TrainListener taskListener) {
        /**
        * train监听器。
        * @return train监听器的结果
        */
        return new TrainListener() {
            @Override
            public void onStarted(TrainProgress p) {
                taskListener.onStarted(p);
            }

            @Override
            public void onEpochCompleted(TrainProgress p) {
                taskListener.onEpochCompleted(p);
                /**
                * 发布事件。
                * @param task.getId( 任务.获取标识(
                */
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
    * @param taskId   任务 标识
    * @param stage    生命周期阶段
    * @param progress 进度快照（可为 空）
    * @param message  附加消息（可为 空）
    * @param cause    异常原因（可为 空）
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
