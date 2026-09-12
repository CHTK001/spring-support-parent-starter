package com.chua.starter.ai.training.support.model.request;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 训练超参数更新请求。
 *
 * <p>所有字段均为可选，仅显式传入的字段会覆盖到训练任务上（部分更新语义），
 * 未传字段保留任务原有配置。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "训练超参数更新请求")
public class TrainArgumentRequest {

    /**
      * 训练任务 标识。
     */
    @NotBlank(message = "taskId 不能为空")
    @Schema(description = "训练任务 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId; // 任务标识

    /**
     * 迭代周期。
     */
    @Schema(description = "迭代周期（epochs）")
    private Integer epoch; // 轮次

    /**
     * 批次大小。
     */
    @Schema(description = "批次大小")
    private Integer batchSize; // 批量大小

    /**
     * 分类数量。
     */
    @Schema(description = "分类数量（类别数）")
    private Integer nClasses; // n类

    /**
     * 学习率。
     */
    @Schema(description = "学习率")
    private Double learningRate; // 学习rate

    /**
     * 动量。
     */
    @Schema(description = "动量")
    private Double lrMomentum; // lrmomentum

    /**
     * 训练集占比（0~100）。
     */
    @Schema(description = "训练集占比（0~100，剩余为测试集）")
    private Integer trainPercent; // trainpercent

    /**
     * 续训练模型路径；传入后从该模型继续微调。
     */
    @Schema(description = "续训练模型路径；传入后从该模型继续微调")
    private String resumeModelPath; // resume模型路径

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