package com.chua.starter.eduai.support.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 古诗文本工具
 *
 * <p>与前端 {@code pages/eduai/src/utils/poemText.ts} 保持同一套规则，两端行为一致。</p>
 *
 * <p>正文有两种形态，切句前必须归一，否则会把 JSON 的方括号与引号当诗句：
 * 采集器入库的是 JSON 数组字符串，initdata / 手工录入的是按行纯文本。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class PoemTextUtils {

    /**
     * 用于把 JSON 数组形态的正文还原为纯文本
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 句末标点（切句用）
     */
    private static final String SENTENCE_DELIMITERS = "[。！？!?；;]";

    private PoemTextUtils() {
    }

    /**
     * 把 JSON 数组形态的正文还原为按行分隔的纯文本；非数组形态原样返回。
     *
     * @param raw 原始正文
     * @return 纯文本
     */
    public static String normalizeContent(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith("[") || !text.endsWith("]")) {
            return text;
        }
        try {
            List<String> parts = JSON.readValue(text, new TypeReference<List<String>>() {
            });
            List<String> cleaned = new ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    cleaned.add(part.trim());
                }
            }
            return String.join("\n", cleaned);
        } catch (Exception ignored) {
            // 不是合法字符串数组时按原文本处理
            return text;
        }
    }

    /**
     * 按句切分正文（每句连同其标点，已去空）。
     *
     * @param raw 原始正文
     * @return 句子列表
     */
    public static List<String> splitSentences(String raw) {
        List<String> sentences = new ArrayList<>();
        String plain = normalizeContent(raw);
        if (plain.isEmpty()) {
            return sentences;
        }
        for (String segment : plain.split("\\r?\\n")) {
            for (String part : segment.split(SENTENCE_DELIMITERS)) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    sentences.add(trimmed);
                }
            }
        }
        return sentences;
    }
}
