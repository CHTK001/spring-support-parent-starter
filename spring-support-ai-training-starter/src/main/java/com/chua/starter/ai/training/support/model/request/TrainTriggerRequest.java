package com.chua.starter.ai.training.support.model.request;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 启动训练请求。
 *
 * <p>不传 {@code resumeModelPath} 时使用默认预训练模型（冷启动训练）；
 * 传入已训练模型路径时从该模型继续微调。</p>
 *
 * @param taskId          训练任务标识
 * @param resumeModelPath 续训练模型路径；不传则使用默认预训练模型冷启动
 * @param type            训练类型；resnet50(默认,DL4J 图像分类) / yolo(python 检测)
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "启动训练请求")
public record TrainTriggerRequest(
        @NotBlank(message = "taskId 不能为空")
        @Schema(description = "训练任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String taskId,
        @Schema(description = "续训练模型路径；不传则使用默认预训练模型冷启动")
        String resumeModelPath,
        @Schema(description = "训练类型；resnet50(默认,DL4J 图像分类) / yolo(python 检测)", example = "resnet50")
        String type) {

    /**
     * 是否为续训练模式（由领域语义驱动，非简单 getter 委托）。
     *
     * @return true 若指定了续训模型
     */
    public boolean isResumeMode() {
        return resumeModelPath != null && !resumeModelPath.isBlank();
    }

    /**
     * 转换为领域配置（仅携带 resume模型路径，供部分更新合并）。
     *
     * @return 领域对象
     */
    public TrainArgument toArgument() {
        return TrainArgument.builder()
                .resumeModelPath(resumeModelPath)
                .build();
    }
}
