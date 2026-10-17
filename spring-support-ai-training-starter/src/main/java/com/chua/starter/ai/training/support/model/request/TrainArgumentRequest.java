package com.chua.starter.ai.training.support.model.request;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 训练超参数更新请求。
 *
 * <p>所有字段均为可选，仅显式传入的字段会覆盖到训练任务上（部分更新语义），
 * 未传字段保留任务原有配置。</p>
 *
 * @param taskId          训练任务标识
 * @param epoch           迭代周期（epochs）
 * @param batchSize       批次大小
 * @param nClasses        分类数量（类别数）
 * @param learningRate    学习率
 * @param lrMomentum      动量
 * @param trainPercent    训练集占比（0~100，剩余为测试集）
 * @param resumeModelPath 续训练模型路径；传入后从该模型继续微调
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "训练超参数更新请求")
public record TrainArgumentRequest(
        @NotBlank(message = "taskId 不能为空")
        @Schema(description = "训练任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String taskId,
        @Schema(description = "迭代周期（epochs）")
        Integer epoch,
        @Schema(description = "批次大小")
        Integer batchSize,
        @Schema(description = "分类数量（类别数）")
        Integer nClasses,
        @Schema(description = "学习率")
        Double learningRate,
        @Schema(description = "动量")
        Double lrMomentum,
        @Schema(description = "训练集占比（0~100，剩余为测试集）")
        Integer trainPercent,
        @Schema(description = "续训练模型路径；传入后从该模型继续微调")
        String resumeModelPath) {

    /**
     * 转换为领域对象 {@link TrainArgument}（仅携带非空字段，供部分更新合并）。
     *
     * @return TrainArgument
     */
    public TrainArgument toArgument() {
        return TrainArgument.builder()
                .epoch(epoch)
                .batchSize(batchSize)
                .nClasses(nClasses)
                .learningRate(learningRate)
                .lrMomentum(lrMomentum)
                .trainPercent(trainPercent)
                .resumeModelPath(resumeModelPath)
                .build();
    }
}
