package com.chua.starter.eduai.collect.provider;

import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.JsonQuestionsParser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本→题目的三级提取管线基类：JSON 直解 → AI 提取 → 正则兜底。
 * <p>
 * 实现类经自研 SPI（META-INF/extensions）实例化，非 Spring Bean，
 * AI 服务在调用时经 {@code SpringBeanUtils} 懒查找（容器未就绪时视为不可用）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public abstract class AbstractTextExtractProvider implements QuestionSourceProvider {

    /**
     * 题目块起始（1. / 1、 / 1. ）
     */
    private static final Pattern BLOCK_START = Pattern.compile("^\\s*(\\d{1,3})\\s*[.、．]\\s*(.+)$");

    /**
     * 选项行（A. xx 支持一行多选项）
     */
    private static final Pattern OPTION_LINE = Pattern.compile("([A-H])\\s*[.、．]\\s*([^A-H\\s][^A-H]*|[A-H](?!\\s*[.、．])[^A-H]*)");

    /**
     * 答案行
     */
    private static final Pattern ANSWER_LINE = Pattern.compile("答案\\s*[:：]\\s*(.+)");

    /**
     * 解析行
     */
    private static final Pattern ANALYSIS_LINE = Pattern.compile("(?:答案解析|解析)\\s*[:：]\\s*(.+)");

    /**
     * 运行时懒查找 AI 服务（SPI 实例不在 Spring 容器内）。
     *
     * @return AI 服务，不可用时 null
     */
    protected EduAiService aiService() {
        try {
            return SpringBeanUtils.getBean(EduAiService.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从任意文本内容提取题目。
     *
     * @param content 原始内容（JSON 或自然文本）
     * @return 题目列表
     */
    protected List<CollectedQuestion> extract(String content) {
        Optional<List<CollectedQuestion>> parsed = JsonQuestionsParser.tryParse(content);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        List<CollectedQuestion> byAi = extractByAi(content);
        if (!byAi.isEmpty()) {
            return byAi;
        }
        return extractByRegex(content);
    }

    private List<CollectedQuestion> extractByAi(String content) {
        EduAiService aiService = aiService();
        if (aiService == null || !aiService.isAvailable()) {
            return List.of();
        }
        String stripped = stripHtml(content);
        String prompt = "从以下文本中提取所有题目，输出 JSON 数组（不要输出其它内容），每个元素字段："
                + "{\"questionType\":\"SINGLE_CHOICE|MULTIPLE_CHOICE|JUDGE|FILL|SHORT_ANSWER\","
                + "\"stem\":\"题干\",\"options\":[\"A. ...\",\"B. ...\"],\"answer\":\"答案\",\"analysis\":\"解析\"}。"
                + "无选项的题目 options 输出空数组。文本：\n"
                + truncate(stripped, 8000);
        try {
            String response = aiService.answerQuestion("你是教育题目结构化助手，只输出合法 JSON。", prompt);
            String json = sliceJsonArray(response);
            if (json == null) {
                return List.of();
            }
            return JsonQuestionsParser.tryParse(json).orElseGet(List::of);
        } catch (Exception e) {
            log.warn("AI 提取题目失败, 转正则兜底: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CollectedQuestion> extractByRegex(String content) {
        List<CollectedQuestion> questions = new ArrayList<>();
        CollectedQuestion current = null;
        StringBuilder stemBuffer = new StringBuilder();
        StringBuilder answerBuffer = new StringBuilder();
        for (String rawLine : stripHtml(content).split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher block = BLOCK_START.matcher(line);
            if (block.matches()) {
                questions.add(finish(current, stemBuffer, answerBuffer));
                current = new CollectedQuestion();
                current.setStem(block.group(2).trim());
                current.setOptionsJson("[]");
                stemBuffer = new StringBuilder();
                answerBuffer = new StringBuilder();
                continue;
            }
            if (current == null) {
                continue;
            }
            Matcher answer = ANSWER_LINE.matcher(line);
            if (answer.find()) {
                answerBuffer.append(answer.group(1).trim());
                continue;
            }
            Matcher analysis = ANALYSIS_LINE.matcher(line);
            if (analysis.find()) {
                current.setAnalysis(analysis.group(1).trim());
                continue;
            }
            if (OPTION_LINE.matcher(line).find() && line.matches(".*[A-H]\\s*[.、．].*")) {
                current.setOptionsJson(mergeOptions(current.getOptionsJson(), line));
                stemBuffer.setLength(0);
                continue;
            }
            stemBuffer.append(line);
        }
        questions.add(finish(current, stemBuffer, answerBuffer));
        questions.removeIf(q -> q == null || q.getStem() == null || q.getStem().isBlank());
        return questions;
    }

    private CollectedQuestion finish(CollectedQuestion current, StringBuilder stemBuffer, StringBuilder answerBuffer) {
        if (current == null) {
            return null;
        }
        if (stemBuffer.length() > 0) {
            current.setStem(current.getStem() + stemBuffer);
        }
        current.setAnswer(answerBuffer.toString());
        String options = current.getOptionsJson();
        boolean hasOptions = options != null && options.length() > 2;
        current.setQuestionType(hasOptions ? "SINGLE_CHOICE" : "SHORT_ANSWER");
        return current;
    }

    private String mergeOptions(String existingJson, String line) {
        List<String> merged = new ArrayList<>();
        if (existingJson != null && existingJson.length() > 2) {
            for (String part : existingJson.substring(1, existingJson.length() - 1).split("\",\"")) {
                merged.add(part.replace("\\\"", "\""));
            }
        }
        Matcher matcher = OPTION_LINE.matcher(line);
        while (matcher.find()) {
            merged.add(matcher.group(1).trim() + ". " + matcher.group(2).trim());
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < merged.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append('"').append(merged.get(i).replace("\"", "\\\"")).append('"');
        }
        return json.append("]").toString();
    }

    private String stripHtml(String content) {
        return content.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", "\n")
                .replaceAll("(?i)&nbsp;?", " ")
                .replaceAll("(?i)&lt;|&gt;", "");
    }

    private String sliceJsonArray(String response) {
        if (response == null) {
            return null;
        }
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        return start >= 0 && end > start ? response.substring(start, end + 1) : null;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
