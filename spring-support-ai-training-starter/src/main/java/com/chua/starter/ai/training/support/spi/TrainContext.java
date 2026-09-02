package com.chua.starter.ai.training.support.spi;

/**
 * 训练上下文：平台层解析好的运行参数，统一交给 {@link TrainProvider}。
 *
 * <p>将"路径从哪来"（properties / 任务数据目录）与"引擎怎么用"解耦，
 * 引擎实现不感知配置细节，只消费本上下文。</p>
 *
 * @param modelPath    预训练模型路径（本地 zip 或目录，冷启动基座）
 * @param savePath     模型保存路径（目录或完整文件路径）
 * @param dataRootPath 训练数据根目录（分类：子目录为类别；YOLO：images+labels 布局）
 * @author CH
 * @since 4.0.0.42
 */
public record TrainContext(String modelPath, String savePath, String dataRootPath) {
}
