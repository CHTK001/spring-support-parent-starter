package com.chua.starter.ai.training.support.event;

import com.chua.deeplearning.support.dl4j.TrainProgress;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 训练任务生命周期事件。
 *
 * <p>平台在每个 {@link TrainStage} 通过 ApplicationEventPublisher 广播该事件；
 * 外部监听方式（三者皆可）：</p>
 * <ul>
 *   <li>Spring {@code @EventListener(TrainLifecycleEvent.class)} Bean —— 主通道，即插即拔；</li>
 *   <li>程序化：自行收集事件后在业务内做适配；</li>
 *   <li>task 级回调 URL（预留）：由监听器把事件转发到 webhook。</li>
 * </ul>
 *
 * <p>事件 {@code source} 即任务 ID。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class TrainLifecycleEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
      * 任务 标识。
     */
    private final String taskId;

    /**
     * 生命周期阶段。
     */
    private final TrainStage stage;

    /**
      * 当前进度快照（任务创建/参数更新等无训练进度的阶段为 空）。
     */
    private final TrainProgress progress;

    /**
     * 附加消息（可选）。
     */
    private final String message;

    /**
      * 异常原因（失败/取消时携带，可为 空）。
     */
    private final Throwable cause;

    /**
     * 构造生命周期事件。
     *
     * @param taskId   任务 标识
     * @param stage    生命周期阶段
     * @param progress 进度快照（可为 空）
     * @param message  附加消息（可为 空）
     * @param cause    异常原因（可为 空）
     */
    public TrainLifecycleEvent(String taskId, TrainStage stage, TrainProgress progress,
                               String message, Throwable cause) {
        /**
         * 父。
         * @param taskId 任务标识
         */
        super(taskId);
        this.taskId = taskId;
        this.stage = stage;
        this.progress = progress;
        this.message = message;
        this.cause = cause;
    }
}
