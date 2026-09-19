package com.chua.starter.ai.training.support.spi.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.train.ResNet50TransferTrainer;
import com.chua.starter.ai.training.support.spi.TrainContext;
import com.chua.starter.ai.training.support.spi.TrainProvider;

/**
 * DL4J 训练引擎适配（类型=resnet50，默认实现）。
 *
 * <p>把既有 {@code new ResNet50TransferTrainer().train(...)} 调用语义原样搬进 SPI 实现，
 * 保证默认路径与历史行为完全一致（引擎 SPI 化是纯增量重构）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class Dl4jTrainProvider implements TrainProvider {

    @Override
    public String type() {
        return "resnet50";
    }

    @Override
    public String engine() {
        return "dl4j";
    }

    @Override
    public TrainResult train(TrainArgument argument, TrainContext ctx, TrainListener listener) throws Exception {
        /**
         * Rnet50调动trainer。
         * @return Rnet50调动trainer的结果
         */
        return new ResNet50TransferTrainer().train(
                argument,
                ctx.modelPath(),
                ctx.savePath(),
                ctx.dataRootPath(),
                listener);
    }
}
