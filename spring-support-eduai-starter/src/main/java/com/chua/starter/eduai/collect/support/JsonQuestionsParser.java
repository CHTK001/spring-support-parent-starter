package com.chua.starter.eduai.collect.support;

import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 题目 JSON 数组解析（支持裸数组、{data:[...]}、{code,data:[...]}、{data:{list:[...]}} 包装）。
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class JsonQuestionsParser {

    /**
     * 共享 ObjectMapper
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonQuestionsParser() {
    }

    /**
     * 尝试把内容解析为题目列表；内容不是 JSON 或不含题目数组时返回空。
     *
     * @param content 原始内容
     * @return 题目列表（可能为空 Optional.empty）
     */
    public static Optional<List<CollectedQuestion>> tryParse(String content) {
        if (content == null) {
            return Optional.empty();
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(trimmed);
            JsonNode array = locateArray(root);
            if (array == null || !array.isArray() || array.isEmpty()) {
                return Optional.empty();
            }
            List<CollectedQuestion> questions = new ArrayList<>();
            for (JsonNode node : array) {
                CollectedQuestion question = readOne(node);
                if (question != null) {
                    questions.add(question);
                }
            }
            return questions.isEmpty() ? Optional.empty() : Optional.of(questions);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static JsonNode locateArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        JsonNode data = root.get("data");
        if (data == null) {
            data = root.get("list");
        }
        if (data == null) {
            return null;
        }
        if (data.isArray()) {
            return data;
        }
        return data.has("list") ? data.get("list") : null;
    }

    private static CollectedQuestion readOne(JsonNode node) {
        String stem = text(node, "stem", "question", "questionStem", "title");
        String answer = text(node, "answer", "answers", "questionAnswer");
        if (stem == null || stem.isBlank()) {
            return null;
        }
        CollectedQuestion question = new CollectedQuestion();
        question.setStem(stem.trim());
        question.setAnswer(answer == null ? "" : answer.trim());
        question.setQuestionType(text(node, "questionType", "type"));
        question.setAnalysis(text(node, "analysis", "explanation", "questionAnalysis"));
        question.setSubject(text(node, "subject", "questionSubject"));
        question.setGradeLevel(text(node, "gradeLevel", "questionGradeLevel"));
        question.setGrade(text(node, "grade", "questionGrade"));
        question.setSource(text(node, "source", "questionSource"));
        JsonNode options = node.get("options");
        if (options != null && !options.isNull()) {
            question.setOptionsJson(options.isArray() ? options.toString() : options.asText());
        }
        if (question.getQuestionType() == null) {
            question.setQuestionType(options != null && options.isArray() && options.size() > 1 ? "SINGLE_CHOICE" : "SHORT_ANSWER");
        }
        return question;
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
            if (value != null && value.isNumber()) {
                return value.asText();
            }
        }
        return null;
    }
}
