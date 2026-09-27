package com.chua.starter.ai.support.configuration.client;

import com.chua.deeplearning.support.engine.ModelParams;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型级参数桥接工具。
 *
 * <p>把各能力分组（face / ocr / layout / image 等）配置表中的「模型字段 + 运行参数」
 * 桥接到 {@link ModelParams} 的 per-model 存储，使：</p>
 * <ul>
 *   <li>同一进程内不同模型可以使用不同推理设备（device）；</li>
 *   <li>阈值等运行参数在引擎侧自动合并（见
 *       {@code AbstractIdentificationEngine#get(String, Class, Map)}）；</li>
 *   <li>DJL 链路与裸 ONNX Runtime 链路使用同一份参数。</li>
 * </ul>
 *
 * <p>桥接是<strong>运行时</strong>行为：仅当配置表中该字段有值时才写入 ModelParams，
 * 不会污染数据库；持久化由 {@link OnnxModelParamService} 负责（两者可共存，
 * 以 {@link OnnxModelParamService} 落库的值优先）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ModelParamBridge {

    /**
     * 桥接工具。
     */
    private ModelParamBridge() {
    }

    /**
     * 将能力分组配置中的 device 写入指定模型的参数。
     *
     * @param modelId 模型标识；为空 时不处理
     * @param device  设备配置值（cpu / gpu / auto）；为空 时不处理
     */
    public static void bridgeDevice(String modelId, String device) {
        if (StringUtils.isBlank(modelId) || StringUtils.isBlank(device)) {
            return;
        }
        ModelParams.putAll(modelId, Map.of(ModelParams.KEY_DEVICE, device.trim().toLowerCase()));
    }

    /**
     * 将能力分组配置中的阈值写入指定模型的参数。
     *
     * @param modelId   模型标识；为空 时不处理
     * @param threshold 置信度阈值；为空 时不处理
     */
    public static void bridgeThreshold(String modelId, Float threshold) {
        if (StringUtils.isBlank(modelId) || threshold == null) {
            return;
        }
        ModelParams.putAll(modelId,
                Map.of(com.chua.deeplearning.support.ai.DetectionConfiguration.KEY_THRESHOLD, threshold));
    }

    /**
     * 一次性桥接某模型的 device 与阈值。
     *
     * @param modelId   模型标识
     * @param device    设备配置值
     * @param threshold 置信度阈值
     */
    public static void bridge(String modelId, String device, Float threshold) {
        if (StringUtils.isBlank(modelId)) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(device)) {
            params.put(ModelParams.KEY_DEVICE, device.trim().toLowerCase());
        }
        if (threshold != null) {
            params.put(com.chua.deeplearning.support.ai.DetectionConfiguration.KEY_THRESHOLD, threshold);
        }
        if (!params.isEmpty()) {
            ModelParams.putAll(modelId, params);
        }
    }
}
