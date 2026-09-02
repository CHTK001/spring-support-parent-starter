package com.chua.starter.ai.training.support.domain;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import lombok.Data;

import java.time.Instant;

/**
 * 训练任务实体，保存单次训练的完整生命周期状态。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class TrainTask {

    /**
     * 任务 ID（UUID）。
     */
    private String id;

    /**
     * 训练状态。
     */
    private TrainStatus status = TrainStatus.PENDING;

    /**
     * 上传的 ZIP 原始文件名。
     */
    private String originalFileName;

    /**
     * 解压后的数据目录路径。
     */
    private String dataPath;

    /**
     * 训练超参数。
     */
    private TrainArgument argument;

    /**
     * 训练进度快照（实时更新，供轮询）。
     */
    private TrainProgress progress = new TrainProgress();

    /**
     * 任务创建时间。
     */
    private Instant createdAt = Instant.now();

    /**
     * 训练开始时间（异步启动时）。
     */
    private Instant startedAt;

    /**
     * 训练完成/失败时间。
     */
    private Instant finishedAt;

    /**
     * 错误信息（失败时填充）。
     */
    private String errorMessage;
}