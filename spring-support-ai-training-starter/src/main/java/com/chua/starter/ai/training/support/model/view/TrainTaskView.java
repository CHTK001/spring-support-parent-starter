package com.chua.starter.ai.training.support.model.view;

import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.domain.TrainTask;

import java.io.Serializable;
import java.time.Instant;

/**
 * 训练任务详情视图（状态查询接口返回）。
 *
 * @param taskId       任务标识
 * @param status       训练状态
 * @param fileName     上传的压缩原始文件名
 * @param progress     训练进度
 * @param modelPath    模型保存路径（训练完成时填充）
 * @param classLabels  类别标签串（训练完成后填充）
 * @param baseModelPath 实际使用的基模型路径
 * @param createdAt    任务创建时间
 * @param startedAt    训练开始时间
 * @param finishedAt   训练完成/失败时间
 * @param errorMessage 错误信息（失败时填充）
 * @author CH
 * @since 4.0.0.42
 */
public record TrainTaskView(
        String taskId,
        TrainStatus status,
        String fileName,
        TrainProgressView progress,
        String modelPath,
        String classLabels,
        String baseModelPath,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage) implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 从领域对象构造视图。
     *
     * @param task 训练任务领域对象
     * @return 视图对象
     */
    public static TrainTaskView from(TrainTask task) {
        return new TrainTaskView(
                task.getId(),
                task.getStatus(),
                task.getOriginalFileName(),
                TrainProgressView.from(task.getProgress()),
                task.getProgress().getModelPath(),
                task.getProgress().getClassLabels(),
                task.getProgress().getBaseModelPath(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getErrorMessage());
    }

    /**
     * 内部进度视图。
     *
     * @param currentEpoch 当前轮次
     * @param totalEpochs  总轮次数
     * @param stage        当前训练阶段描述
     * @param progress     进度百分比（0~100）
     * @param bestScore    当前最优 F1 值
     * @author CH
     * @since 4.0.0
     */
    public record TrainProgressView(
            int currentEpoch,
            int totalEpochs,
            String stage,
            double progress,
            double bestScore) implements Serializable {

        private static final long serialVersionUID = 1L; // 串行版本uid

        /**
         * 从领域对象构造进度视图。
         *
         * @param progress 训练进度领域对象
         * @return 进度视图
         */
        public static TrainProgressView from(TrainProgress progress) {
            return new TrainProgressView(
                    progress.getCurrentEpoch(),
                    progress.getTotalEpochs(),
                    progress.getStage(),
                    progress.getProgress(),
                    progress.getBestScore());
        }
    }
}
