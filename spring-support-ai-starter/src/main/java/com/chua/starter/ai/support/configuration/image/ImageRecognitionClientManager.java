package com.chua.starter.ai.support.configuration.image;

import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.image.ImageCaptioning;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 图片识别客户端管理器。
 *
 * <p>按 {@code image} 分组全局设置（sys_ai_global_setting）懒构建
 * 图像分类（{@link ImageClassifier}）、目标检测（{@link ImageDetector}）、
 * 图像描述（{@link ImageCaptioning}）与超分修复（{@link ImageEnhancer}），
 * 未配置的能力返回 空，由调用方降级。配置变更通过指纹比对自动重建。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageRecognitionClientManager {

    /**
     * 全局设置键值分组
     */
    private static final String GROUP = "image";

    /**
     * 全局设置读取工具
     */
    private final GlobalSettingReader settingReader;

    /**
     * 当前生效的图像分类器（可为 空）
     */
    private volatile ImageClassifier classifier;

    /**
     * 当前生效的图像分类器配置指纹
     */
    private volatile String classifierFp;

    /**
     * 当前生效的目标检测器（可为 空）
     */
    private volatile ImageDetector detector;

    /**
     * 当前生效的目标检测器配置指纹
     */
    private volatile String detectorFp;

    /**
     * 当前生效的图像描述器（可为 空）
     */
    private volatile ImageCaptioning captioning;

    /**
     * 当前生效的图像描述器配置指纹
     */
    private volatile String captioningFp;

    /**
     * 当前生效的图像超分器（可为 空）
     */
    private volatile ImageEnhancer enhancer;

    /**
     * 当前生效的图像超分器配置指纹
     */
    private volatile String enhancerFp;

    /**
     * 图像分类 Top-K（未配置模型时返回 空）。
     *
     * @param imageData 图片字节
     * @param topK      返回类别数
     * @return 分类结果列表或 空
     */
    public List<DetectionInfo> classifyTopK(byte[] imageData, int topK) {
        ImageClassifier c = currentClassifier();
        if (c == null) {
            return null;
        }
        return c.classifyTopK(imageData, topK);
    }

    /**
     * 目标检测（未配置模型时返回 空）。
     *
     * @param imageData 图片字节
     * @return 检测结果列表或 空
     */
    public List<DetectionInfo> detect(byte[] imageData) {
        ImageDetector d = currentDetector();
        if (d == null) {
            return null;
        }
        return d.detect(imageData);
    }

    /**
     * 图像描述（未配置模型时返回 空）。
     *
     * @param imageData 图片字节
     * @return 描述文本或 空
     */
    public String caption(byte[] imageData) {
        ImageCaptioning c = currentCaptioning();
        if (c == null) {
            return null;
        }
        return c.describe(imageData);
    }

    /**
     * 图像超分修复（未启用或未配置模型时返回 空）。
     *
     * @param imageData 图片字节
     * @return 超分后图片字节或 空
     */
    public byte[] superResolution(byte[] imageData) {
        ImageEnhancer e = currentEnhancer();
        if (e == null) {
            return null;
        }
        return e.enhance(imageData);
    }

    /**
     * 枚举可用模型清单（按能力分组）。
     *
     * @return 能力分组 → 模型 标识 列表
     */
    public Map<String, List<String>> listModels() {
        return Map.of(
                "classifier", ImageClassifier.listModels(),
                "detector", ImageDetector.listModels(),
                "captioning", ImageCaptioning.listModels(),
                "enhancer", ImageEnhancer.listModels());
    }

    /**
     * 获取当前图像分类器（配置变更自动重建，未配置返回 空）。
     *
     * @return 分类器或 空
     */
    private ImageClassifier currentClassifier() {
        String fp = String.join("|",
                StringUtils.defaultString(settingReader.getStr(GROUP, "classify_model"), ""),
                String.valueOf(settingReader.getDecimalOrNull(GROUP, "classify_threshold")),
                StringUtils.defaultString(settingReader.getStr(GROUP, "device"), ""));
        if (fp.equals(classifierFp)) {
            return classifier;
        }
        synchronized (this) {
            if (!fp.equals(classifierFp)) {
                String model = settingReader.getStr(GROUP, "classify_model");
                if (StringUtils.isBlank(model)) {
                    this.classifier = null;
                } else {
                    ImageClassifier c = ImageClassifier.create(model);
                    BigDecimal threshold = settingReader.getDecimalOrNull(GROUP, "classify_threshold");
                    if (threshold != null) {
                        c.threshold(threshold.floatValue());
                    }
                    String device = settingReader.getStr(GROUP, "device");
                    if (StringUtils.isNotBlank(device)) {
                        c.device(device);
                    }
                    com.chua.starter.ai.support.configuration.client.ModelParamBridge.bridge(
                            model, device, threshold == null ? null : threshold.floatValue());
                    this.classifier = c;
                }
                this.classifierFp = fp;
            }
        }
        return classifier;
    }

    /**
     * 获取当前目标检测器（配置变更自动重建，未配置返回 空）。
     *
     * @return 检测器或 空
     */
    private ImageDetector currentDetector() {
        String fp = String.join("|",
                StringUtils.defaultString(settingReader.getStr(GROUP, "detect_model"), ""),
                String.valueOf(settingReader.getDecimalOrNull(GROUP, "detect_threshold")));
        if (fp.equals(detectorFp)) {
            return detector;
        }
        synchronized (this) {
            if (!fp.equals(detectorFp)) {
                String model = settingReader.getStr(GROUP, "detect_model");
                if (StringUtils.isBlank(model)) {
                    this.detector = null;
                } else {
                    ImageDetector d = ImageDetector.create(model);
                    BigDecimal threshold = settingReader.getDecimalOrNull(GROUP, "detect_threshold");
                    if (threshold != null) {
                        d.threshold(threshold.floatValue());
                    }
                    String device = settingReader.getStr(GROUP, "device");
                    if (StringUtils.isNotBlank(device)) {
                        d.device(device);
                    }
                    com.chua.starter.ai.support.configuration.client.ModelParamBridge.bridge(
                            model, device, threshold == null ? null : threshold.floatValue());
                    this.detector = d;
                }
                this.detectorFp = fp;
            }
        }
        return detector;
    }

    /**
     * 获取当前图像描述器（配置变更自动重建，未配置返回 空）。
     *
     * @return 描述器或 空
     */
    private ImageCaptioning currentCaptioning() {
        String fp = StringUtils.defaultString(settingReader.getStr(GROUP, "captioning_model"), "");
        if (fp.equals(captioningFp)) {
            return captioning;
        }
        synchronized (this) {
            if (!fp.equals(captioningFp)) {
                this.captioning = fp.isBlank() ? null : ImageCaptioning.create(fp);
                if (!fp.isBlank()) {
                    com.chua.starter.ai.support.configuration.client.ModelParamBridge
                            .bridgeDevice(fp, settingReader.getStr(GROUP, "device"));
                }
                this.captioningFp = fp;
            }
        }
        return captioning;
    }

    /**
     * 获取当前图像超分器（未启用或未配置模型时返回 空）。
     *
     * @return 超分器或 空
     */
    private ImageEnhancer currentEnhancer() {
        String fp = settingReader.getBool(GROUP, "super_resolution_enabled", false)
                ? StringUtils.defaultString(settingReader.getStr(GROUP, "super_resolution_model"), "")
                : "";
        if (fp.equals(enhancerFp)) {
            return enhancer;
        }
        synchronized (this) {
            String fp2 = settingReader.getBool(GROUP, "super_resolution_enabled", false)
                    ? StringUtils.defaultString(settingReader.getStr(GROUP, "super_resolution_model"), "")
                    : "";
            if (!fp2.equals(enhancerFp)) {
                this.enhancer = fp2.isBlank() ? null : ImageEnhancer.create(fp2);
                this.enhancerFp = fp2;
            }
        }
        return enhancer;
    }
}
