package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.file.txtractor.TextExtractResult;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrRecognizer;
import com.chua.deeplearning.support.ocr.OcrResult;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OCR 文本提取器 —— 使用 deeplearning OCR 管线从图片中识别文字。
 *
 * <p>供 RAG 上传图片时提取文本（消费全局配置 {@code rag_ocr_model} 或 {@code ocr} 分组）：
 * 仅对图片类型文件执行 OCR，非图片文件抛出异常由 {@link com.chua.common.support.ai.rag.MemoryRagClient}
 * 降级为 UTF-8 读取。</p>
 *
 * <p>识别器懒加载：仅在首次遇到图片文件时才创建 OCR 模型，
 * 避免构建阶段初始化 deeplearning 模型导致启动阻塞或崩溃。</p>
 *
 * <p>支持两种构造：
 * <ul>
 *   <li>单模型名（兼容旧用法）：recognizer 单模型路径，不走管线</li>
 *   <li>完整管线：detector + recognizer + 阈值 + 阅读序，应用全局 ocr 配置</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class OcrTextExtractor implements TextExtractor {

    /**
     * OCR 模型名称（兼容旧构造）。
     */
    private final String modelName;

    /**
     * 识别器（懒加载，单模型路径）。
     */
    private volatile OcrRecognizer recognizer;

    /**
     * 完整管线（懒加载，detector+recognizer 路径）。
     * 为空时走单模型路径。
     */
    private volatile OcrPipeline pipeline;

    /**
     * 兼容旧构造（单模型名作为 recognizer）。
     *
     * @param modelName OCR 模型名称（同时作为 detector/recognizer）
     */
    public OcrTextExtractor(String modelName) {
        this.modelName = modelName;
        log.info("[OcrTextExtractor] OCR 提取器已创建（懒加载）: model={}", modelName);
    }

    /**
     * 完整 OCR 管线构造（detector + recognizer + 阈值 + 阅读序）。
     *
     * @param detectorModel   文本检测模型名
     * @param recognizerModel 文本识别模型名
     * @param minConfidence   检测置信度阈值（0~1，过滤低置信度文本框）
     * @param sortReadingOrder 是否按阅读顺序排序（先 y 后 x）
     * @param directionModel 方向矫正模型（空则关闭）
     * @param restorerModel  文字超分修复模型（空则关闭）
     */
    public OcrTextExtractor(String detectorModel, String recognizerModel,
                            float minConfidence, boolean sortReadingOrder,
                            String directionModel, String restorerModel) {
        this.modelName = recognizerModel;
        // 保存完整管线参数，懒加载时构建
        this.detectorModel = detectorModel;
        this.recognizerModel = recognizerModel;
        this.minConfidence = minConfidence;
        this.sortReadingOrder = sortReadingOrder;
        this.directionModel = directionModel;
        this.restorerModel = restorerModel;
        log.info("[OcrTextExtractor] 完整管线 OCR 提取器（懒加载）: detector={}, recognizer={}, threshold={}",
                detectorModel, recognizerModel, minConfidence);
    }

    /**
     * 完整管线参数（懒加载时用）。
     */
    private String detectorModel;
    /** Recognizer模型 */
    private String recognizerModel;
    /** 最小值confidence */
    private float minConfidence = 0f;
    /** Sortreading排序 */
    private boolean sortReadingOrder = true;
    /** Direction模型 */
    private String directionModel;
    /** Restorer模型 */
    private String restorerModel;

    /**
     * 从图片中提取文本，非图片文件抛出异常触发 UTF-8 降级。
     *
     * @param file 待提取的文件
     * @return 结构化文本结果列表
     */
    @Override
    public List<TextExtractResult> extractText(File file) {
        if (!isImage(file)) {
            throw new IllegalArgumentException("非图片文件，交由降级读取: " + file.getName());
        }
        try {
            byte[] imageData = Files.readAllBytes(file.toPath());
            String text;
            if (detectorModel != null && recognizerModel != null) {
                // 完整管线：拼接各识别框文本
                OcrPipeline pp = resolvePipeline();
                List<OcrResult> results = pp.recognizeDetail(imageData);
                text = results.stream()
                        .map(OcrResult::text)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining("\n"));
            } else {
                // 兼容路径：单模型 recognizer
                text = resolveRecognizer().recognize(imageData);
            }
            if (StringUtils.isBlank(text)) {
                return List.of();
            }
            return List.of(new TextExtractResult(text, "ocr"));
        } catch (Exception e) {
            log.warn("[OcrTextExtractor] OCR 识别失败: file={}, model={}, error={}",
                    file.getName(), modelName, e.getMessage());
            throw new IllegalArgumentException("OCR 识别失败，交由降级读取", e);
        }
    }

    /**
     * 懒加载识别器（兼容旧路径）。
     *
     * @return OCR 识别器
     */
    private OcrRecognizer resolveRecognizer() {
        OcrRecognizer current = recognizer;
        if (current == null) {
            synchronized (this) {
                if (recognizer == null) {
                    recognizer = OcrRecognizer.create(modelName);
                }
                current = recognizer;
            }
        }
        return current;
    }

    /**
     * 懒加载 OCR 管线（detect→crop→recognize，含阈值过滤）。
     *
     * @return OCR 管线
     */
    private OcrPipeline resolvePipeline() {
        OcrPipeline current = pipeline;
        if (current == null) {
            synchronized (this) {
                if (pipeline == null) {
                    OcrPipeline.Builder builder = OcrPipeline.builder()
                            .detector(detectorModel)
                            .recognizer(recognizerModel)
                            .minConfidence(minConfidence)
                            .sortReadingOrder(sortReadingOrder);
                    if (StringUtils.isNotBlank(directionModel)) {
                        builder.direction(directionModel);
                    }
                    if (StringUtils.isNotBlank(restorerModel)) {
                        builder.enhancer(restorerModel);
                    }
                    pipeline = builder.build();
                }
                current = pipeline;
            }
        }
        return current;
    }

    /**
     * 判断是否为支持的图片类型。
     *
     * @param file 文件
     * @return true 表示图片
     */
    private static boolean isImage(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".bmp") || name.endsWith(".webp") || name.endsWith(".gif");
    }

    /**
     * 获取提取器类型名称。
     *
     * @return 类型名称
     */
    @Override
    public String type() {
        return "ocr";
    }
}
