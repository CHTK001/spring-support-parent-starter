package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图像底库关键词提取器。
 *
 * <p>按 {@code image_search} 分组的 {@code keyword_extractor} 决定关键词来源：
 * <ul>
 *   <li>{@code none}：不提取，保持原状；</li>
 *   <li>{@code ocr}：调用现有 OCR 管线，把识别出的文字块写入 tags；</li>
 *   <li>{@code model}：调用模型管理中的 vLLM / OpenAI 兼容多模态模型，
 *       把模型返回的图片信息写入 tags。</li>
 * </ul>
 *
 * <p>{@code ocr} 与 {@code model} 是互斥的两个选项，只读取当前配置的一个来源。
 * 提取失败不会阻断图片入库：图像特征向量与底库记录仍是核心，关键词只是检索增强。</p>
 *
 * @author CH
 * @since 4.0.0.46
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageKeywordExtractor {

    /** 配置分组名 */
    private static final String GROUP = "image_search";

    /** 默认多模态提取提示词 */
    private static final String DEFAULT_PROMPT =
            "请根据这张图片提取可检索的关键信息：主体、场景、品牌、文字、颜色、风格等。"
                    + "只输出 2-10 个关键词，用逗号分隔，不要解释、不要编号。";

    /** 单次入库最多保留的关键词数量 */
    private static final int MAX_TAGS = 20;

    /** tags 列约 512 字符，留一点余量不要把 DB 写爆 */
    private static final int MAX_TAG_CHARS = 500;

    private final GlobalSettingReader settingReader;
    private final OcrClientManager ocrClientManager;
    private final ModelClientResolver modelClientResolver;

    /**
     * 提取图片关键词。
     *
     * @param imageData 图片字节
     * @param name      展示名（仅用于日志/兜底）
     * @return 关键词列表；未启用或提取失败时返回空列表
     */
    public List<String> extract(byte[] imageData, String name) {
        if (imageData == null || imageData.length == 0) {
            return List.of();
        }
        String mode = StringUtils.defaultString(settingReader.getStr(GROUP, "keyword_extractor"), "none")
                .trim().toLowerCase(Locale.ROOT);
        if ("none".equals(mode) || mode.isBlank()) {
            return List.of();
        }
        try {
            if ("ocr".equals(mode)) {
                return extractByOcr(imageData, name);
            }
            if ("model".equals(mode) || "vllm".equals(mode)) {
                return extractByModel(imageData, name);
            }
            log.warn("[ImageKeyword] keyword_extractor 配置无效：{}，本次不提取关键词", mode);
            return List.of();
        } catch (Exception ex) {
            log.warn("[ImageKeyword] 图片关键词提取失败：name={}, mode={} -> {}",
                    name, mode, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 用 OCR 识别并收集文本块。
     *
     * @param imageData 图片字节
     * @param name      展示名
     * @return 识别文字块
     */
    private List<String> extractByOcr(byte[] imageData, String name) {
        if (!ocrClientManager.available()) {
            log.warn("[ImageKeyword] keyword_extractor=ocr 但 OCR 未配置模型，跳过：name={}", name);
            return List.of();
        }
        List<String> raw = new ArrayList<>();
        for (com.chua.deeplearning.support.ocr.OcrResult result :
                ocrClientManager.recognizeDetail(imageData)) {
            if (result != null && StringUtils.isNotBlank(result.text())) {
                raw.add(result.text());
            }
        }
        if (raw.isEmpty()) {
            log.info("[ImageKeyword] OCR 未识别到文字：name={}", name);
        }
        return normalize(raw);
    }

    /**
     * 用配置的多模态模型提取关键词。
     *
     * @param imageData 图片字节
     * @param name      展示名
     * @return 模型输出拆分后的关键词
     */
    private List<String> extractByModel(byte[] imageData, String name) {
        String model = StringUtils.defaultString(settingReader.getStr(GROUP, "keyword_model"), "");
        if (StringUtils.isBlank(model)) {
            log.warn("[ImageKeyword] keyword_extractor=model 但未配置 keyword_model，跳过：name={}", name);
            return List.of();
        }
        String prompt = StringUtils.defaultString(settingReader.getStr(GROUP, "keyword_prompt"), DEFAULT_PROMPT);
        if (StringUtils.isBlank(prompt)) {
            prompt = DEFAULT_PROMPT;
        }
        ChatClient client = modelClientResolver.resolve(model.trim());
        if (client == null) {
            log.warn("[ImageKeyword] 多模态模型不可用：model={}，跳过：name={}", model, name);
            return List.of();
        }
        String text;
        synchronized (this) {
            text = client.newChat()
                    .model(model.trim())
                    .system(prompt)
                    .temperature(0.1d)
                    .maxTokens(256)
                    .addImage(dataUri(imageData))
                    .chatSync(prompt);
        }
        if (StringUtils.isBlank(text)) {
            log.info("[ImageKeyword] 多模态模型未返回内容：model={}, name={}", model, name);
            return List.of();
        }
        return normalize(splitModelText(text));
    }

    /**
     * 把模型返回的文本拆成候选关键词。
     *
     * @param text 模型返回文本
     * @return 候选关键词
     */
    private static List<String> splitModelText(String text) {
        String cleaned = text
                .replace("```", " ")
                .replace("关键词：", " ")
                .replace("关键词:", " ")
                .replace("keywords：", " ")
                .replace("keywords:", " ");
        List<String> raw = new ArrayList<>();
        for (String part : cleaned.split("[,，;；、\\n\\r]+")) {
            String item = part == null ? "" : part.trim();
            if (!item.isEmpty()) {
                raw.add(item);
            }
        }
        return raw;
    }

    /**
     * 规整并限长关键词列表。
     *
     * @param raw 原始候选
     * @return 去重、限长后的关键词
     */
    private static List<String> normalize(List<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        int total = 0;
        for (String item : raw) {
            String clean = item == null ? "" : item.trim().replaceAll("\\s+", " ");
            if (clean.isEmpty()) {
                continue;
            }
            if (clean.length() > 120) {
                clean = clean.substring(0, 120);
            }
            if (!seen.add(clean)) {
                continue;
            }
            total += clean.length() + 1;
            if (seen.size() >= MAX_TAGS || total > MAX_TAG_CHARS) {
                break;
            }
        }
        return List.copyOf(seen);
    }

    /**
     * 转成 OpenAI 兼容多模态接口可用的 data URI。
     *
     * @param imageData 图片字节
     * @return data URI
     */
    private static String dataUri(byte[] imageData) {
        return "data:" + mimeType(imageData) + ";base64," + Base64.getEncoder().encodeToString(imageData);
    }

    /**
     * 按魔数推断图片 MIME。
     *
     * @param data 图片字节
     * @return MIME 类型
     */
    private static String mimeType(byte[] data) {
        if (data != null && data.length >= 4
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data != null && data.length >= 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data != null && data.length >= 3 && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return "image/jpeg";
    }
}