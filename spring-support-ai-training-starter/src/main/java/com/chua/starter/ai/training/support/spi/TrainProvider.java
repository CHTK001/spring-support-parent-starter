package com.chua.starter.ai.training.support.spi;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainResult;

/**
* 训练引擎 SPI（扩展点）。
*
* <p>不同模型架构/训练后端的接入统一走该接口，由
* {@link com.chua.common.support.spi.ServiceProvider} + {@code META-INF/services} 注册发现，
* 按 {@link #type()} 分发（参考仓库 向量 / datasearch 的 提供者 惯例）。</p>
*
* <p>已接入类型：</p>
* <ul>
*   <li>{@code resnet50} → {@code Dl4jTrainProvider}（现状默认，适配 ResNet50 迁移学习）</li>
*   <li>{@code yolo}（占位预留）→ 计划由 {@code python-http} 引擎承载，Java 拉起本地 python
*       子进程（进程内起一次性 HTTP 训练服务），训练全程经 {@link TrainListener} 回传</li>
* </ul>
*
* @author CH
* @since 4.0.0.42
 */
public interface TrainProvider {

    /**
    * 训练类型标识（与前端传入的 类型 对齐，如 resnet50 / yolo）。
    *
    * @return 训练类型
     */
    String type();

    /**
    * 承载引擎标识（如 dl4j / Python-http），仅作描述与监控用途。
    *
    * @return 引擎标识
     */
    String engine();

    /**
    * 执行训练。
    *
    * @param argument 训练超参数
    * @param ctx      训练上下文（模型/保存/数据路径，由平台解析好传入）
    * @param listener 进度回调（可为 {@link TrainListener#NOOP}）
    * @return 训练结果
    * @throws Exception 训练过程中发生异常
     */
    TrainResult train(TrainArgument argument, TrainContext ctx, TrainListener listener) throws Exception;
}
