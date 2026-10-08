package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.file.txtractor.TextExtractResult;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.ocr.OcrResult;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 侧 OCR 文本提取器 —— 直接复用共享的 {@link OcrClientManager} OCR 管线。
 *
 * <p>OCR 配置的唯一来源是 {@code ocr} 分组（配置中心 → OCR），其管线由
 * {@link OcrClientManager} 负责构建与热更新（{@code OcrSettingsChangedEvent}）。
 * 本类仅做「图片 → 文本」适配，提取时实时取当前管线，因此：</p>
 * <ul>
 *   <li>OCR 配置改动无需重建 RAG 客户端（旧实现把管线烘进每个账号客户端，需靠事件重建）；</li>
 *   <li>不与 {@link OcrClientManager} 重复构建/加载 OCR 模型（单一实例，省内存）。</li>
 * </ul>
 *
 * <p>非图片文件抛 {@link IllegalArgumentException}，由
 * {@code com.chua.common.support.ai.rag.MemoryRagClient} 降级为 UTF-8 读取。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class OcrPipelineTextExtractor implements TextExtractor {

    /**
     * 共享 OCR 管线管理器（ocr 分组单一事实来源）
     */
    private final OcrClientManager ocrClientManager;

    /**
     * 构造。
     *
     * @param ocrClientManager 共享 OCR 管线管理器
     */
    public OcrPipelineTextExtractor(OcrClientManager ocrClientManager) {
        this.ocrClientManager = ocrClientManager;
    }

    /**
     * 从图片中提取文本；非图片文件抛出异常触发 UTF-8 降级。
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
            List<OcrResult> results = ocrClientManager.recognizeDetail(imageData);
            String text = results.stream()
                    .map(OcrResult::text)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("\n"));
            if (StringUtils.isBlank(text)) {
                return List.of();
            }
            return List.of(new TextExtractResult(text, "ocr"));
        } catch (Exception e) {
            throw new IllegalArgumentException("OCR 识别失败，交由降级读取", e);
        }
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
}
