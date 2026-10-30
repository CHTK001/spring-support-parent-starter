package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.model.MemoryCandidateDTO;
import com.chua.starter.ai.support.model.MemoryExtractionContext;
import com.chua.starter.ai.support.spi.MemoryExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则记忆抽取器 —— 从用户输入里识别显式的指令、偏好与自述事实。
 *
 * <p>只读用户输入、不读模型回复：模型回复里的"事实"可能是幻觉，写进长期记忆会把一次错误
 * 固化成后续所有会话的上下文。规则命中即产出候选，由存储层按内容指纹去重合并。</p>
 *
 * <p>零外部依赖、默认开启，因此即使没有可用模型，记忆体依然能记录用户显式交代的口径。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class RuleMemoryExtractor implements MemoryExtractor {

    /**
     * 单条候选内容的最大长度
     */
    private static final int MAX_CONTENT = 200;

    /**
     * 子句终止符（中英文句号、问号、叹号、分号与换行）
     */
    private static final String CLAUSE_ENDS = "。！？!?；;，,\n";

    /**
     * 疑问句收尾特征：以疑问词结尾的子句是一次提问而非交代
     */
    private static final Pattern QUESTION_TAIL = Pattern.compile(
            "(什么|啥|吗|呢|么|多少|几点|几人|谁|哪里|哪儿|何时|多久|为什么|为啥|是否|哪个|哪些|怎么|怎样|如何"
                    + "|what|who|which|where|when|why|how|many|much)\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 抽取规则表：种类、默认重要度与匹配式
     */
    private static final List<Rule> RULES = List.of(
            new Rule("instruction", 0.9D, "(?:请?记住|记下|牢记|别忘了)[:：]?\\s*([^。！？!?\n]{2,120})"),
            new Rule("instruction", 0.85D, "以后(?:都|要|请|别|不要|不许|不准)([^。！？!?\n]{2,80})"),
            new Rule("instruction", 0.8D, "(?:每次|每当)[^。！？!?\n]{0,20}(?:都|要|请)([^。！？!?\n]{2,80})"),
            new Rule("preference", 0.8D, "(?:回答|回复|输出|解释)[^。！？!?\n]{0,6}(?:用|使用|采用)\\s*"
                    + "(中文|英文|English|日本語|markdown|JSON|表格|列表)[^。！？!?\n]{0,10}"),
            new Rule("preference", 0.75D, "我(?:不喜欢|讨厌|反感|不想看到)([^。！？!?\n]{2,80})"),
            new Rule("preference", 0.75D, "我(?:喜欢|偏好|习惯|更倾向|钟爱)([^。！？!?\n]{2,80})"),
            new Rule("fact", 0.8D, "我(?:的名字)?叫\\s*([\\u4e00-\\u9fa5A-Za-z·]{1,20})"),
            new Rule("fact", 0.75D, "我(?:是|在)[^。！？!?\n]{1,40}(?:工作|上班|就读|读书|负责|任职)"),
            new Rule("fact", 0.7D, "我(?:住在|来自|生活在)([^。！？!?\n]{2,40})"),
            new Rule("fact", 0.7D, "我(?:的)(?:项目|产品|系统|团队|公司|岗位)[^。！？!?\n]{1,60}"),
            new Rule("instruction", 0.85D, "(?i)(always|never|remember to)\\s+([^.!?\n]{3,120})"),
            new Rule("preference", 0.75D, "(?i)i (?:prefer|like|love|hate|dislike)\\s+([^.!?\n]{3,100})"),
            new Rule("fact", 0.75D, "(?i)my name is\\s+([A-Za-z·]{1,30})")
    );

    @Override
    /**
     * 抽取器名称。
     *
     * @return 名称
     */
    public String name() {
        return "规则抽取（显式指令/偏好/自述）";
    }

    @Override
    /**
     * 从用户输入中按规则产出候选记忆。
     *
     * @param context 抽取上下文
     * @return 候选记忆列表
     */
    public List<MemoryCandidateDTO> extract(MemoryExtractionContext context) {
        List<MemoryCandidateDTO> candidates = new ArrayList<>();
        String text = context == null ? null : context.userMessage();
        if (text == null || text.isBlank()) {
            return candidates;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                if (isQuestion(text, matcher.start())) {
                    continue;
                }
                String content = matcher.group(0).trim();
                if (content.length() > MAX_CONTENT) {
                    content = content.substring(0, MAX_CONTENT);
                }
                if (!seen.add(rule.kind() + "#" + content)) {
                    continue;
                }
                candidates.add(new MemoryCandidateDTO(
                        rule.kind(),
                        null,
                        content,
                        keywordsOf(content),
                        rule.importance(),
                        null,
                        null));
            }
        }
        if (!candidates.isEmpty()) {
            log.debug("[Memory] 规则抽取命中 {} 条 (conversationId={})", candidates.size(), context.conversationId());
        }
        return candidates;
    }

    /**
     * 判断触发词所在子句是否是一次提问 —— 用户回指"你记住的…是什么"并非交代新记忆。
     *
     * @param text  用户输入全文
     * @param start 命中起点（触发词位置）
     * @return true 表示该命中来自疑问句，应丢弃
     */
    private boolean isQuestion(String text, int start) {
        int end = start;
        while (end < text.length() && CLAUSE_ENDS.indexOf(text.charAt(end)) < 0) {
            end++;
        }
        if (end < text.length() && (text.charAt(end) == '？' || text.charAt(end) == '?')) {
            return true;
        }
        return QUESTION_TAIL.matcher(text.substring(start, end).trim()).find();
    }

    /**
     * 从命中文本里切出若干检索关键词，供召回时的字面命中加权。
     *
     * @param content 候选内容
     * @return 关键词列表
     */
    private List<String> keywordsOf(String content) {
        List<String> keywords = new ArrayList<>();
        for (String part : content.split("[，,、\\s]+")) {
            String token = part.trim();
            if (token.length() >= 2 && keywords.size() < 8) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * 单条抽取规则。
     *
     * @param kind        记忆种类
     * @param importance  默认重要度
     * @param pattern     匹配式
     */
    private record Rule(String kind, double importance, Pattern pattern) {

        /**
         * 构造规则并预编译匹配式。
         * @param importance 默认重要度
         * @param kind 记忆种类
         * @param regex 匹配式文本
         * @return 返回值
        */
        Rule(String kind, double importance, String regex) {
            this(kind, importance, Pattern.compile(regex));
        }
    }
}
