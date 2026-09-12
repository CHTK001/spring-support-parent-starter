package com.chua.starter.ai.training.support.model.view;

import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.domain.TrainTask;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 训练任务详情视图（状态查询接口返回）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class TrainTaskView implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
      * 任务 标识。
     */
    private String taskId;

    /**
     * 训练状态。
     */
    private TrainStatus status;

    /**
      * 上传的 压缩 原始文件名。
     */
    private String fileName;

    /**
     * 训练进度。
     */
    private TrainProgressView progress;

    /**
     * 模型保存路径（训练完成时填充）。
     */
    private String modelPath;

    /**
     * 类别标签串（训练完成后填充）。
     */
    private String classLabels;

    /**
     * 实际使用的基模型路径。
     */
    private String baseModelPath;

    /**
     * 任务创建时间。
     */
    private Instant createdAt;

    /**
     * 训练开始时间。
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

    /**
     * 从领域对象构造视图。
     *
     * @param task 训练任务领域对象
     * @return 视图对象
     */
    public static TrainTaskView from(TrainTask task) {
        TrainTaskView view = new TrainTaskView();
        view.setTaskId(task.getId());
        view.setStatus(task.getStatus());
        view.setFileName(task.getOriginalFileName());
        view.setProgress(TrainProgressView.from(task.getProgress()));
        view.setModelPath(task.getProgress().getModelPath());
        view.setClassLabels(task.getProgress().getClassLabels());
        view.setBaseModelPath(task.getProgress().getBaseModelPath());
        view.setCreatedAt(task.getCreatedAt());
        view.setStartedAt(task.getStartedAt());
        view.setFinishedAt(task.getFinishedAt());
        view.setErrorMessage(task.getErrorMessage());
        return view;
    }

    /**
     * 内部进度视图。
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class TrainProgressView implements Serializable {

        private static final long serialVersionUID = 1L; // 串行版本uid

        /**
          * 当前 轮次。
         */
        private int currentEpoch;

        /**
          * 总 轮次 数。
         */
        private int totalEpochs;

        /**
         * 当前训练阶段描述。
         */
        private String stage;

        /**
         * 进度百分比（0~100）。
         */
        private double progress;

        /**
         * 当前最优 F1 值。
         */
        private double bestScore;

        /**
         * 从领域对象构造进度视图。
         *
         * @param progress 训练进度领域对象
         * @return 进度视图
         */
        public static TrainProgressView from(TrainProgress progress) {
            TrainProgressView view = new TrainProgressView();
            view.setCurrentEpoch(progress.getCurrentEpoch());
            view.setTotalEpochs(progress.getTotalEpochs());
            view.setStage(progress.getStage());
            view.setProgress(progress.getProgress());
            view.setBestScore(progress.getBestScore());
            return view;
        }
    }
}