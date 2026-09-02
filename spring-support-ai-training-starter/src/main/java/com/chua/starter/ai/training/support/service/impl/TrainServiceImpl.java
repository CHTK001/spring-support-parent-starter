package com.chua.starter.ai.training.support.service.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.deeplearning.support.dl4j.train.ResNet50TransferTrainer;
import com.chua.deeplearning.support.dl4j.Trainer;
import com.chua.starter.ai.training.support.config.TrainProperties;
import com.chua.starter.ai.training.support.domain.TrainTask;
import com.chua.starter.ai.training.support.service.TrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 训练服务实现：任务管理 + 异步训练调度。
 *
 * <p>训练在后台线程中执行，通过 {@link TrainTask#getProgress()} 实时暴露进度，
 * 前端可轮询获取当前 epoch、F1 值等信息。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainServiceImpl implements TrainService {

    private final TrainProperties properties;

    /**
     * 内存任务存储（key=taskId）。
     */
    private final Map<String, TrainTask> taskStore = new ConcurrentHashMap<>();

    @Override
    public TrainTask createTask(String originalFileName, String dataPath) {
        TrainTask task = new TrainTask();
        task.setId(UUID.randomUUID().toString());
        task.setOriginalFileName(originalFileName);
        task.setDataPath(dataPath);
        task.setStatus(TrainStatus.PENDING);

        // 默认超参数（TrainArgument 无公开无参构造器，只能经 builder 构建）
        TrainArgument argument = TrainArgument.builder()
                .epoch(10)
                .batchSize(2)
                .nClasses(3)
                .learningRate(1e-3)
                .lrMomentum(0.9)
                .trainPercent(70)
                .build();
        task.setArgument(argument);

        taskStore.put(task.getId(), task);
        log.info("[Train] 任务已创建: id={} file={}", task.getId(), originalFileName);
        return task;
    }

    @Override
    public void startTraining(String taskId) throws Exception {
        TrainTask task = taskStore.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        if (task.getStatus() == TrainStatus.RUNNING) {
            throw new IllegalStateException("任务正在运行中: " + taskId);
        }

        task.setStatus(TrainStatus.RUNNING);
        task.setStartedAt(Instant.now());
        task.setProgress(new TrainProgress()
                .setStatus(TrainStatus.RUNNING)
                .setStage("准备训练"));

        String modelPath = properties.getModelPath();
        String savePath = properties.getSavePath();
        String dataRootPath = task.getDataPath();

        // 异步训练线程
        Thread trainThread = new Thread(() -> {
            try {
                log.info("[Train] 开始训练: taskId={}, data={}", taskId, dataRootPath);

                Trainer trainer = new ResNet50TransferTrainer();
                var result = trainer.train(
                        task.getArgument(),
                        modelPath,
                        savePath,
                        dataRootPath,
                        new TrainListener() {
                            @Override
                            public void onStarted(TrainProgress progress) {
                                task.setProgress(progress);
                                task.setStatus(progress.getStatus());
                            }

                            @Override
                            public void onEpochCompleted(TrainProgress progress) {
                                task.setProgress(progress);
                                task.setStatus(progress.getStatus());
                            }

                            @Override
                            public void onFinished(TrainProgress progress) {
                                task.setProgress(progress);
                                task.setStatus(progress.getStatus());
                            }

                            @Override
                            public void onFailed(TrainProgress progress, Throwable cause) {
                                task.setProgress(progress);
                                task.setStatus(progress.getStatus());
                                task.setErrorMessage(cause.getMessage());
                            }
                        });

                task.setFinishedAt(Instant.now());
                if (result.isSuccess()) {
                    task.setStatus(TrainStatus.SUCCESS);
                    log.info("[Train] 训练完成: taskId={}, F1={}, 耗时={}ms",
                            taskId, result.getBestScore(), result.getElapsedMs());
                } else {
                    task.setStatus(TrainStatus.FAILED);
                    task.setErrorMessage(result.getErrorMessage());
                    log.error("[Train] 训练失败: taskId={}, error={}", taskId, result.getErrorMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                task.setStatus(TrainStatus.CANCELLED);
                task.setErrorMessage("训练被中断");
                task.setFinishedAt(Instant.now());
                log.warn("[Train] 训练被中断: taskId={}", taskId);
            } catch (Exception e) {
                task.setStatus(TrainStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setFinishedAt(Instant.now());
                log.error("[Train] 训练异常: taskId={}", taskId, e);
            }
        }, "dl4j-train-" + taskId);
        trainThread.setDaemon(true);
        trainThread.start();

        log.info("[Train] 异步训练已启动: taskId={}", taskId);
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
        // 未显式传入类别标签时保留任务上已有的标签，避免整对象覆盖导致标签丢失
        TrainArgument current = task.getArgument();
        if (current != null) {
            if (argument.getClassLabels() == null) {
                argument.setClassLabels(current.getClassLabels());
            }
            if (argument.getDetLabels() == null) {
                argument.setDetLabels(current.getDetLabels());
            }
        }
        task.setArgument(argument);
        log.info("[Train] 超参数已更新: taskId={}, epoch={}, batchSize={}, nClasses={}",
                taskId, argument.getEpoch(), argument.getBatchSize(), argument.getNClasses());
    }
}