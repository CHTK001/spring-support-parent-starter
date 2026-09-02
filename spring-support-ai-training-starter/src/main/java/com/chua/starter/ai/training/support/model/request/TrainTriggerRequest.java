package com.chua.starter.ai.training.support.model.request;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 启动训练请求。
 *
 * <p>不传 {@link #resumeModelPath} 时使用默认预训练模型（冷启动训练）；
 * 传入已训练模型路径时从该模型继续微调。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "启动训练请求")
public class TrainTriggerRequest {

    /**
     * 训练任务 ID。
     */
    @NotBlank(message = "taskId 不能为空")
    @Schema(description = "训练任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    /**
     * 续训练模型路径（可选）。
     */
    @Schema(description = "续训练模型路径；不传则使用默认预训练模型冷启动")
    private String resumeModelPath;

    /**
     * 训练类型（引擎路由键）。可选，缺省 {@code resnet50}（DL4J 图像分类迁移学习）；
     * 传 {@code yolo} 走 python-http 引擎（本地拉起 Ultralytics YOLO 子进程）。
     */
    @Schema(description = "训练类型；resnet50(默认,DL4J 图像分类) / yolo(python 检测)", example = "resnet50")
    private String type;

    /**
     * 是否为续训练模式（由领域语义驱动，非简单 getter 委托）。
     *
     * @return true 若指定了续训模型
     */
    public boolean isResumeMode() {
        return resumeModelPath != null && !resumeModelPath.isBlank();
    }

    /**
     * 转换为领域配置（仅携带 resumeModelPath，供部分更新合并）。
     *
     * @return 领域对象
     */
    public TrainArgument toArgument() {
        return TrainArgument.builder()
                .resumeModelPath(resumeModelPath)
                .build();
    }
}
