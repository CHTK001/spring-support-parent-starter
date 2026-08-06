package com.chua.starter.ai.support.configuration;

import com.chua.common.support.file.txtractor.TextExtractResult;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrRecognizer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * OCR 文本提取器 —— 使用 deeplearning OCR 模型从图片中识别文字。
 *
 * <p>供 RAG 上传图片时提取文本（消费全局配置 {@code rag_ocr_model}）：
 * 仅对图片类型文件执行 OCR，非图片文件抛出异常由 {@link com.chua.common.support.ai.rag.MemoryRagClient}
 * 降级为 UTF-8 读取。</p>
 *
 * <p>识别器采用懒加载：仅在首次遇到图片文件时才创建 OCR 模型，
 * 避免构建阶段初始化 deeplearning 模型导致启动阻塞或崩溃。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OcrTextExtractor implements TextExtractor {

    /**
     * OCR 模型名称
     */
    private final String modelName;

    /**
     * 识别器（懒加载）
     */
    private volatile OcrRecognizer recognizer;

    /**
     * 构造。
     *
     * @param modelName OCR 模型名称
     */
    public OcrTextExtractor(String modelName) {
        this.modelName = modelName;
        log.info("[OcrTextExtractor] OCR 提取器已创建（懒加载）: model={}", modelName);
    }

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
            String text = resolveRecognizer().recognize(imageData);
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
     * 懒加载识别器。
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
