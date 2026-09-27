package com.chua.starter.eduai.collect.support;

import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionScriptHelper;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link QuestionScriptHelper} 默认实现：绑定到单次脚本执行，累积该次执行的题目。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class DefaultQuestionScriptHelper implements QuestionScriptHelper {

    /**
     * 选项序列化
     */
    private static final ObjectMapper OPTIONS_MAPPER = new ObjectMapper();

    /**
     * 选项序号表
     */
    private static final String LETTERS = "ABCDEFGH";

    /**
     * 判断题答案文案
     */
    private static final String TRUE_ANSWER = "正确";

    /**
     * 判断题错误文案
     */
    private static final String FALSE_ANSWER = "错误";

    private final QuestionSourceContext context;

    private final Logger logger;

    /**
     * 脚本累积的题目
     */
    private final List<CollectedQuestion> questions = new ArrayList<>();

    /**
     * @param context 采集上下文
     * @param logger 日志器
     */
    public DefaultQuestionScriptHelper(QuestionSourceContext context, Logger logger) {
        this.context = context;
        this.logger = logger;
    }

    @Override
    public QuestionSourceContext getContext() {
        return context;
    }

    @Override
    public List<CollectedQuestion> getQuestions() {
        return questions;
    }

    @Override
    public String config(String key) {
        return SourceConfigReader.text(context.getSourceConfig(), key);
    }

    @Override
    public String config(String key, String defaultValue) {
        String value = config(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Override
    public int configInt(String key, int defaultValue) {
        String value = config(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public String http(String url) {
        try {
            return HttpFetchSupport.get(url);
        } catch (IOException e) {
            throw new IllegalStateException("脚本抓取失败: " + url + " - " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] httpBytes(String url) {
        try {
            return HttpFetchSupport.getBytes(url);
        } catch (IOException e) {
            throw new IllegalStateException("脚本二进制抓取失败: " + url + " - " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> extract(String content, String regex) {
        List<String> hits = new ArrayList<>();
        for (List<String> groups : extractGroups(content, regex)) {
            String first = groups.get(0);
            if (first != null && !first.isBlank()) {
                hits.add(first.trim());
            }
        }
        return hits;
    }

    @Override
    public List<List<String>> extractGroups(String content, String regex) {
        List<List<String>> groups = new ArrayList<>();
        if (content == null || regex == null) {
            return groups;
        }
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(content);
        while (matcher.find()) {
            List<String> one = new ArrayList<>(Math.max(1, matcher.groupCount()));
            if (matcher.groupCount() == 0) {
                one.add(matcher.group());
            } else {
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    one.add(matcher.group(i));
                }
            }
            groups.add(one);
        }
        return groups;
    }

    @Override
    public List<CollectedQuestion> parseQuestions(String json) {
        return JsonQuestionsParser.tryParse(json)
                .orElseThrow(() -> new IllegalStateException("脚本 JSON 中未找到题目数组"));
    }

    @Override
    public void add(CollectedQuestion question) {
        if (question != null) {
            questions.add(question);
        }
    }

    @Override
    public CollectedQuestion question(String stem, String answer) {
        return build("SHORT_ANSWER", stem, null, answer, null);
    }

    @Override
    public CollectedQuestion fill(String stem, String answer, String analysis) {
        return build("FILL", stem, null, answer, analysis);
    }

    @Override
    public CollectedQuestion judge(String stem, boolean expectTrue, String analysis) {
        return build("JUDGE", stem, null, expectTrue ? TRUE_ANSWER : FALSE_ANSWER, analysis);
    }

    @Override
    public CollectedQuestion choice(String stem, List<String> options, int answerIndex, String analysis) {
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("单选题至少需要 2 个选项: " + stem);
        }
        if (answerIndex < 0 || answerIndex >= options.size()) {
            throw new IllegalArgumentException("单选题答案下标越界: " + answerIndex);
        }
        List<String> normalized = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            normalized.add(withLetter(LETTERS.charAt(i) + "", options.get(i)));
        }
        String optionsJson = writeOptions(normalized);
        return build("SINGLE_CHOICE", stem, optionsJson, LETTERS.charAt(answerIndex) + "", analysis);
    }

    @Override
    public void log(String message) {
        logger.info("eduai 采集脚本[{}] {}", context.getTaskName(), message);
    }

    private CollectedQuestion build(String type, String stem, String optionsJson, String answer, String analysis) {
        CollectedQuestion question = new CollectedQuestion();
        question.setQuestionType(type.toUpperCase(Locale.ROOT));
        question.setStem(stem);
        question.setOptionsJson(optionsJson);
        question.setAnswer(answer);
        question.setAnalysis(analysis);
        question.setSource("groovy:" + context.getTaskName());
        return question;
    }

    /**
     * 选项未自带序号时补上序号，与题库既有口径（{@code ["A. 甲"]}）保持一致。
     * <p>
     * 仅认 A-H 大写字母前缀：抓取到的释义常以词性 {@code n.} {@code v.} 开头，不能视作序号。
     */
    private String withLetter(String letter, String option) {
        String text = option == null ? "" : option.trim();
        if (text.length() > 2 && LETTERS.indexOf(text.charAt(0)) >= 0
                && (text.charAt(1) == '.' || text.charAt(1) == '、' || text.charAt(1) == '．')
                && text.charAt(2) == ' ') {
            return text;
        }
        return letter + ". " + text;
    }

    private String writeOptions(List<String> values) {
        try {
            return OPTIONS_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("选项序列化失败: " + e.getMessage(), e);
        }
    }
}
