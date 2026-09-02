package com.chua.starter.ai.training.support.domain;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.spi.TrainProviders;
import lombok.Getter;

import java.time.Instant;

/**
 * 训练任务领域对象（充血模型）。
 *
 * <p>封装训练任务完整生命周期状态机：PENDING → RUNNING → SUCCESS / FAILED / CANCELLED，
 * 以及超参数合并、进度观测等业务行为，Service 层只编排领域方法，不直接 setter 拼装状态。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class TrainTask {

    /**
     * 任务 ID（UUID）。
     */
    private final String id;

    /**
     * 上传的 ZIP 原始文件名。
     */
    private final String originalFileName;

    /**
     * 解压后的数据目录路径。
     */
    private final String dataPath;

    /**
     * 任务创建时间。
     */
    private final Instant createdAt;

    /**
     * 训练状态。
     */
    private volatile TrainStatus status;

    /**
     * 训练超参数。
     */
    private volatile TrainArgument argument;

    /**
     * 训练进度快照（实时更新，供轮询）。
     */
    private volatile TrainProgress progress;

    /**
     * 训练开始时间。
     */
    private volatile Instant startedAt;

    /**
     * 训练完成/失败时间。
     */
    private volatile Instant finishedAt;

    /**
     * 错误信息（失败时填充）。
     */
    private volatile String errorMessage;

    /**
     * 训练类型（引擎路由键，如 resnet50 / yolo）。创建时默认 resnet50，
     * 启动训练前可经 {@link #selectType(String)} 显式指定（仅 PENDING 可改）。
     * 注意：类型变更必须走 {@link #selectType(String)}（含 PENDING-only 守卫），
     * 不提供公开 setter，避免绕开状态机直接改写类型。
     */
    private volatile String type = TrainProviders.DEFAULT_TYPE;

    /**
     * 领域工厂：创建新训练任务。
     *
     * @param originalFileName 上传文件名
     * @param dataPath         解压后数据目录
     * @return 新任务（PENDING + 默认超参数）
     */
    public static TrainTask create(String originalFileName, String dataPath) {
        TrainTask task = new TrainTask(
                java.util.UUID.randomUUID().toString(),
                originalFileName,
                dataPath,
                Instant.now());
        task.status = TrainStatus.PENDING;
        task.argument = TrainArgument.defaults();
        task.progress = new TrainProgress();
        return task;
    }

    private TrainTask(String id, String originalFileName, String dataPath, Instant createdAt) {
        this.id = id;
        this.originalFileName = originalFileName;
        this.dataPath = dataPath;
        this.createdAt = createdAt;
    }

    /**
     * 启动训练（PENDING → RUNNING）。同步方法保证并发重复触发时
     * 同一任务只允许一次成功流转（后到者看到 RUNNING 即抛）。
     *
     * @throws IllegalStateException 非 PENDING 状态时抛出
     */
    public synchronized void start() {
        if (status != TrainStatus.PENDING) {
            throw new IllegalStateException("任务状态为 " + status + "，不可启动（仅 PENDING 可启动）");
        }
        this.status = TrainStatus.RUNNING;
        this.startedAt = Instant.now();
        this.progress = new TrainProgress()
                .setStatus(TrainStatus.RUNNING)
                .setStage("准备训练");
    }

    /**
     * 指定训练类型（引擎路由键）。null/空串回退默认类型，仅 PENDING 可改。
     *
     * @param type 训练类型（resnet50 / yolo ...）；null 视为未指定
     * @return 本次实际生效的类型
     * @throws IllegalStateException 已启动后不可再改类型
     */
    public synchronized String selectType(String type) {
        if (status != TrainStatus.PENDING) {
            throw new IllegalStateException("任务状态为 " + status + "，不可再改训练类型（仅 PENDING 可指定）");
        }
        this.type = (type == null || type.isBlank()) ? TrainProviders.DEFAULT_TYPE : type;
        return this.type;
    }

    /**
     * 标记训练成功。
     *
     * @param result 训练结果（含 bestScore、elapsedMs、labels）
     */
    public synchronized void succeed(TrainResult result) {
        this.status = TrainStatus.SUCCESS;
        this.finishedAt = Instant.now();
        if (result.getBestScore() != 0) {
            this.progress.setBestScore(result.getBestScore());
        }
        if (result.getModelPath() != null) {
            this.progress.setModelPath(result.getModelPath());
        }
        if (result.getLabels() != null) {
            this.progress.setClassLabels(result.getLabels().toString());
        }
    }

    /**
     * 标记训练失败。
     *
     * @param errorMessage 错误描述
     */
    public synchronized void fail(String errorMessage) {
        this.status = TrainStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    /**
     * 标记训练失败（含原始异常）。
     *
     * @param errorMessage 错误描述
     * @param cause        原始异常
     */
    public synchronized void fail(String errorMessage, Throwable cause) {
        this.status = TrainStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
        if (cause != null && cause.getMessage() != null) {
            this.errorMessage = errorMessage + " | " + cause.getMessage();
        }
    }

    /**
     * 标记训练被取消。
     */
    public synchronized void cancel() {
        this.status = TrainStatus.CANCELLED;
        this.finishedAt = Instant.now();
        this.errorMessage = "训练被中断";
    }

    /**
     * 合并超参数更新（部分更新语义，仅非空字段覆盖，领域行为封装）。
     *
     * @param incoming 新超参数
     */
    public void mergeArgument(TrainArgument incoming) {
        this.argument = argument == null ? incoming : argument.merge(incoming);
    }

    /**
     * 返回绑定到当前任务的训练监听器（观察者模式，由任务自身生产）。
     *
     * @return TrainListener
     */
    public TrainListener createListener() {
        return new TrainListener() {
            @Override
            public void onStarted(TrainProgress p) {
                progress = p;
                status = p.getStatus();
            }

            @Override
            public void onEpochCompleted(TrainProgress p) {
                progress = p;
                status = p.getStatus();
            }

            @Override
            public void onFinished(TrainProgress p) {
                progress = p;
                status = p.getStatus();
            }

            @Override
            public void onFailed(TrainProgress p, Throwable cause) {
                progress = p;
                status = p.getStatus();
                errorMessage = cause != null ? cause.getMessage() : null;
            }
        };
    }
}