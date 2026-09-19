package com.chua.starter.ai.training.support.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 训练任务生命周期阶段（平台层事件粒度）。
 *
 * <p>覆盖任务从创建到终态的全过程；引擎内部的细粒度回调（TrainListener 的 4 个钩子）
 * 保留在引擎层，平台在此基础上外发这些任务级阶段事件，供外部以 Spring 事件方式注入监听。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
@RequiredArgsConstructor
public enum TrainStage {

    /**
     * 任务已创建（数据已就绪、处于 PENDING）。
     * @param "任务已创建" "任务已创建"
     * @return 任务创建的结果
     */
    TASK_CREATED("任务已创建"),

    /**
     * 超参数已更新（仅 PENDING 可改，部分更新语义）。
     * @param "超参数已更新" "超参数已更新"
     * @return 参数更新的结果
     */
    ARGUMENT_UPDATED("超参数已更新"),

    /**
     * 训练线程已启动（PENDING → RUNNING）。
     * @param "训练启动" "训练启动"
     * @return train启动的结果
     */
    TRAIN_STARTED("训练启动"),

    /**
     * 迭代进度更新（每个 轮次 完成时上报）。
     * @param "迭代进度" "迭代进度"
     * @return 轮次进步的结果
     */
    EPOCH_PROGRESS("迭代进度"),

    /**
     * 训练成功（进入 成功）。
     * @param "训练成功" "训练成功"
     * @return TRAIN_SUCCEEDED的结果
     */
    TRAIN_SUCCEEDED("训练成功"),

    /**
     * 训练失败（进入 失败，含异常信息）。
     * @param "训练失败" "训练失败"
     * @return train失败的结果
     */
    TRAIN_FAILED("训练失败"),

    /**
     * 训练被中断/取消（进入 CANCELLED）。
     * @param "训练取消" "训练取消"
     * @return TRAIN_CANCELLED的结果
     */
    TRAIN_CANCELLED("训练取消");

    /**
     * 阶段中文描述（日志/UI 展示）。
     */
    private final String display;
}
