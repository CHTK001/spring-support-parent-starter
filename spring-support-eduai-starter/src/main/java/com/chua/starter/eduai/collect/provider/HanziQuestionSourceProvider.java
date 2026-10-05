package com.chua.starter.eduai.collect.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.DbCollectSupport;
import com.chua.starter.eduai.collect.support.HttpFetchSupport;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 汉字采集源（自落库型）：从公开单字字典取汉字，写入 edu_hanzi。
 *
 * <p>与 {@link IdiomQuestionSourceProvider} 同构（自落库 + 按唯一键去重 + 自动续采），
 * 差别在于漢字要处理三件成语不需要处理的事：</p>
 * <ol>
 *   <li><b>大文件</b>：单字字典 word.json 约 27MB，超过 {@link HttpFetchSupport} 默认 20MB 上限，
 *       因此走带上限/超时的重载；解析用 Jackson 流式逐元素读，避免把整棵树读进堆
 *       （本服务 -Xmx2g 且常驻 ONNX 模型，整树会显著抬高内存峰值）。</li>
 *   <li><b>字段整理</b>：源数据的读音把 -ng 的 g 写成 U+0261（ɡ）、释义是长段落的
 *       字典排版，直接入库会出现「dīnɡ」和半页《说文》引文。</li>
 *   <li><b>宁可留空不给错</b>：间架结构只按可靠映射推导，源数据判不准时留空给老师补。</li>
 * </ol>
 *
 * <p>配置（示例）：</p>
 * <pre>
 * {
 *   "url":          "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/word.json",
 *   "charListUrl":  "https://raw.githubusercontent.com/elephantnose/characters/master/3500常用汉字.txt",
 *   "idsUrl":       "https://raw.githubusercontent.com/skishore/makemeahanzi/master/dictionary.txt",
 *   "offset":       0,          // 省略时按已入库数量自动续采
 *   "limit":        500,        // 本次最多新增条数
 *   "gradeLevel":   "小学",      // 写 edu_hanzi.hanzi_grade_level
 *   "meaningMaxChars": 90       // 释义保留长度
 * }
 * </pre>
 *
 * <p><b>口径与 {@code tools/gen-hanzi-seed.py} 一致</b>（种子数据与在线采集同规则，
 * 两处各有一份实现：跨语言无法共用代码，改动时必须同步，否则种子与续采数据会打架）。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Slf4j
@Spi(value = "HANZI", order = 91)
public class HanziQuestionSourceProvider implements QuestionSourceProvider {

    /**
     * 缺省单字字典（chinese-xinhua）。27MB，只有 raw.githubusercontent 取得到：
     * jsdelivr 对超过 20MB 的文件直接返回「File size exceeded the configured limit」。
     */
    public static final String DEFAULT_DICT_URL =
            "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/word.json";

    /**
     * 缺省字表（现代汉语常用字表一级 3500 字），按常用度排序，决定续采顺序
     */
    public static final String DEFAULT_CHAR_LIST_URL =
            "https://raw.githubusercontent.com/elephantnose/characters/master/3500%E5%B8%B8%E7%94%A8%E6%B1%89%E5%AD%97.txt";

    /**
     * 缺省 IDS 源（部首补缺 + 间架结构推导）
     */
    public static final String DEFAULT_IDS_URL =
            "https://raw.githubusercontent.com/skishore/makemeahanzi/master/dictionary.txt";

    /**
     * 字表镜像（主源不可用时依次尝试）
     */
    private static final List<String> CHAR_LIST_MIRRORS = List.of(
            DEFAULT_CHAR_LIST_URL,
            "https://cdn.jsdelivr.net/gh/elephantnose/characters@master/3500%E5%B8%B8%E7%94%A8%E6%B1%89%E5%AD%97.txt"
    );

    /**
     * IDS 源镜像
     */
    private static final List<String> IDS_MIRRORS = List.of(
            DEFAULT_IDS_URL,
            "https://cdn.jsdelivr.net/gh/skishore/makemeahanzi@master/dictionary.txt"
    );

    /**
     * 字典响应体上限（实测 27,354,320 B，留一档余量）
     */
    private static final int DICT_MAX_BYTES = 48 * 1024 * 1024;

    /**
     * 字典下载超时：27MB 慢网也要给够时间
     */
    private static final Duration DICT_TIMEOUT = Duration.ofSeconds(300);

    /**
     * 单次写库的批大小（逐条 SELECT 去重，批太大会长时间占连接）
     */
    private static final int BATCH_SIZE = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonFactory FACTORY = new JsonFactory();

    @Override
    public String type() {
        return "HANZI";
    }

    @Override
    public boolean selfPersisted() {
        return true;
    }

    @Override
    public List<CollectedQuestion> fetch(QuestionSourceContext context) {
        // 自落库型：不走题目落库流程
        return List.of();
    }

    @Override
    public int persist(QuestionSourceContext context) {
        DbCollectSupport support = DbCollectSupport.get();
        if (support == null) {
            throw new IllegalStateException("入库支持未初始化");
        }
        String config = context.getSourceConfig();
        String dictUrl = firstText(SourceConfigReader.text(config, "url"), DEFAULT_DICT_URL);
        String idsUrl = firstText(SourceConfigReader.text(config, "idsUrl"), DEFAULT_IDS_URL);
        String gradeLevel = firstText(SourceConfigReader.text(config, "gradeLevel"), context.getGradeLevel(), "小学");
        int limit = intOf(SourceConfigReader.text(config, "limit"), 500);
        int meaningMaxChars = intOf(SourceConfigReader.text(config, "meaningMaxChars"), 90);
        // 未配置起始偏移时自动续采：从已入库数量开始，重复执行每次都有新数据
        int offset = intOf(SourceConfigReader.text(config, "offset"), (int) countHanzi(support));

        List<Character> wanted = loadCharList(SourceConfigReader.text(config, "charListUrl"));
        if (wanted.isEmpty()) {
            throw new IllegalStateException("字表为空，无法确定采集范围");
        }
        Map<Character, DictEntry> dict = loadDict(dictUrl, wanted);
        Map<Character, IdsEntry> ids = loadIds(idsUrl, wanted);
        if (dict.isEmpty()) {
            throw new IllegalStateException("单字字典未取到可用条目: " + dictUrl);
        }

        int inserted = importContinuously(support, wanted, dict, ids, offset, limit,
                gradeLevel, meaningMaxChars, context.getOwnerUserId());
        log.info("[collect] 汉字采集完成: dict={} 字表={} 命中={} offset={} limit={} 新增={} 学段={}",
                dictUrl, wanted.size(), dict.size(), offset, limit, inserted, gradeLevel);
        return inserted;
    }

    /**
     * 连续抽取：从 offset 起向后扫描，直到本次新增达到 limit 或字表遍历完。
     *
     * <p>与成语采集同思路：偏移与数据集下标不一定对齐（续采时 offset 是「已入库数量」），
     * 所以遇到整批都是重复数据时要继续往后扫，不能只试一批就收工。</p>
     *
     * @param support        入库支持
     * @param wanted         字表（按常用度排序）
     * @param dict           字典条目
     * @param ids            IDS 条目
     * @param offset         起始下标
     * @param limit          期望新增条数
     * @param gradeLevel     学段
     * @param meaningMaxChars 释义保留长度
     * @param ownerUserId    归属用户
     * @return 实际新增条数
     */
    private int importContinuously(DbCollectSupport support, List<Character> wanted,
                                  Map<Character, DictEntry> dict, Map<Character, IdsEntry> ids,
                                  int offset, int limit, String gradeLevel, int meaningMaxChars,
                                  String ownerUserId) {
        int cursor = Math.max(0, Math.min(offset, wanted.size()));
        int inserted = 0;
        while (cursor < wanted.size() && inserted < limit) {
            List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
            int scanned = cursor;
            // 一批里尽量凑够 limit 条「源里有、库里还没有」的数据
            while (scanned < wanted.size() && batch.size() < Math.min(BATCH_SIZE, limit - inserted)) {
                Character ch = wanted.get(scanned);
                scanned++;
                DictEntry entry = dict.get(ch);
                if (entry == null) {
                    continue;
                }
                batch.add(toRow(ch, entry, ids.get(ch), gradeLevel, meaningMaxChars, ownerUserId));
            }
            if (batch.isEmpty()) {
                break;
            }
            Map<String, Integer> counter = support.upsert("edu_hanzi", List.of("hanzi_char"), false, batch);
            inserted += counter.getOrDefault("inserted", 0);
            cursor = scanned;
            if (cursor >= wanted.size()) {
                break;
            }
        }
        return inserted;
    }

    /**
     * 组装一行入库数据（列名与 edu_hanzi 一致）。
     *
     * @param ch              汉字
     * @param entry           字典条目
     * @param idsEntry        IDS 条目（可空）
     * @param gradeLevel      学段
     * @param meaningMaxChars 释义保留长度
     * @param ownerUserId     归属用户
     * @return 入库行
     */
    private Map<String, Object> toRow(Character ch, DictEntry entry, IdsEntry idsEntry,
                                      String gradeLevel, int meaningMaxChars, String ownerUserId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hanzi_char", String.valueOf(ch));
        row.put("hanzi_pinyin", normalizePinyin(entry.pinyin()));
        row.put("hanzi_strokes", entry.strokes());
        row.put("hanzi_radical", radicalOf(entry, idsEntry));
        row.put("hanzi_structure", structureOf(idsEntry == null ? null : idsEntry.decomposition()));
        // 公开数据集没有笔画名（只有 SVG 笔顺轨迹），笔顺留空由老师按教材口径补
        row.put("hanzi_meaning", condenseMeaning(entry.explanation(), meaningMaxChars));
        row.put("hanzi_grade_level", gradeLevel);
        row.put("hanzi_status", 1);
        if (ownerUserId != null && !ownerUserId.isBlank()) {
            row.put("hanzi_owner_user_id", ownerUserId);
        }
        return row;
    }

    /**
     * 取字表（汉字去重、保持原顺序）。
     *
     * @param configuredUrl 配置里的字表地址（可空，空则用默认）
     * @return 汉字列表
     */
    private List<Character> loadCharList(String configuredUrl) {
        List<String> urls = configuredUrl == null || configuredUrl.isBlank()
                ? CHAR_LIST_MIRRORS
                : List.of(configuredUrl.trim());
        String text = fetchFirst(urls, "字表");
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHanzi(c) && !chars.contains(c)) {
                chars.add(c);
            }
        }
        log.info("[collect] 汉字字表载入 {} 字", chars.size());
        return chars;
    }

    /**
     * 流式读取单字字典，只保留字表里要的字。
     *
     * @param url    字典地址
     * @param wanted 字表
     * @return 字 -> 条目
     */
    private Map<Character, DictEntry> loadDict(String url, List<Character> wanted) {
        byte[] body;
        try {
            body = HttpFetchSupport.getBytes(url, DICT_MAX_BYTES, DICT_TIMEOUT);
        } catch (IOException e) {
            throw new IllegalStateException("单字字典拉取失败: " + e.getMessage(), e);
        }
        Map<Character, DictEntry> map = new LinkedHashMap<>();
        try (JsonParser parser = FACTORY.createParser(new ByteArrayInputStream(body))) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("单字字典不是 JSON 数组（源结构可能已变）: " + url);
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode node = MAPPER.readTree(parser);
                String word = text(node, "word");
                if (word == null || word.length() != 1 || !isHanzi(word.charAt(0))) {
                    continue;
                }
                char ch = word.charAt(0);
                if (!wanted.contains(ch) || map.containsKey(ch)) {
                    continue;
                }
                map.put(ch, new DictEntry(text(node, "pinyin"), intOf(text(node, "strokes"), null),
                        text(node, "radicals"), text(node, "more"), text(node, "explanation")));
            }
        } catch (IOException e) {
            throw new IllegalStateException("单字字典解析失败: " + e.getMessage(), e);
        }
        log.info("[collect] 单字字典载入 {} 条（字表 {} 字，响应 {} KB）",
                map.size(), wanted.size(), body.length / 1024);
        return map;
    }

    /**
     * 读取 IDS 源（JSONL，一行一个对象），只保留字表里要的字。
     *
     * @param url    IDS 地址
     * @param wanted 字表
     * @return 字 -> 条目（失败时返回空 Map，只影响部首补缺与结构，不阻断采集）
     */
    private Map<Character, IdsEntry> loadIds(String url, List<Character> wanted) {
        Map<Character, IdsEntry> map = new LinkedHashMap<>();
        String text;
        try {
            text = fetchFirst(IDS_MIRRORS.contains(url) ? IDS_MIRRORS : List.of(url), "IDS");
        } catch (IllegalStateException e) {
            log.warn("[collect] IDS 源不可用，部首/结构将留空: {}", e.getMessage());
            return map;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                String character = text(node, "character");
                if (character == null || character.length() != 1 || !isHanzi(character.charAt(0))) {
                    continue;
                }
                char ch = character.charAt(0);
                if (wanted.contains(ch) && !map.containsKey(ch)) {
                    map.put(ch, new IdsEntry(text(node, "radical"), text(node, "decomposition")));
                }
            } catch (Exception e) {
                // 单行解析失败不影响整体（源文件里混有非 JSON 行）
                log.debug("[collect] IDS 行解析跳过: {}", e.getMessage());
            }
        }
        log.info("[collect] IDS 载入 {} 条", map.size());
        return map;
    }

    /**
     * 依次尝试多个地址取文本，全部失败抛异常（不静默降级）。
     *
     * @param urls  地址列表
     * @param label 用途标签（日志用）
     * @return 响应文本
     */
    private String fetchFirst(List<String> urls, String label) {
        Exception last = null;
        for (String url : urls) {
            try {
                return HttpFetchSupport.get(url);
            } catch (Exception e) {
                last = e;
                log.warn("[collect] 汉字{}源不可用，尝试下一个: {} 原因: {}", label, url, e.getMessage());
            }
        }
        throw new IllegalStateException("汉字" + label + "源全部镜像不可用: " + (last == null ? "" : last.getMessage()), last);
    }

    /**
     * 已入库汉字数（自动续采的起点）。
     *
     * @param support 入库支持
     * @return 条数
     */
    private long countHanzi(DbCollectSupport support) {
        Long count = support.jdbc().queryForObject("SELECT COUNT(*) FROM edu_hanzi", Long.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------ 字段整理

    /**
     * 部首三级回退：radicals 字段 → more 字段正则 → IDS 源。
     *
     * @param entry    字典条目
     * @param idsEntry IDS 条目（可空）
     * @return 部首（可空）
     */
    private static String radicalOf(DictEntry entry, IdsEntry idsEntry) {
        if (entry.radicals() != null && !entry.radicals().isBlank()) {
            return trim(entry.radicals(), 8);
        }
        // more 形如「吖 a 部首 口 部首笔画 03 总笔画 06」
        if (entry.more() != null) {
            int idx = entry.more().indexOf("部首");
            if (idx >= 0) {
                String tail = entry.more().substring(idx + 2).trim();
                if (!tail.isEmpty()) {
                    int end = 0;
                    while (end < tail.length() && !Character.isWhitespace(tail.charAt(end))) {
                        end++;
                    }
                    if (end > 0) {
                        return trim(tail.substring(0, end), 8);
                    }
                }
            }
        }
        if (idsEntry != null && idsEntry.radical() != null && !idsEntry.radical().isBlank()) {
            return trim(idsEntry.radical(), 8);
        }
        return null;
    }

    /**
     * 由 IDS 部件分解推导间架结构；不可靠时留空（宁可留空也不给错数据）。
     *
     * <p>与 {@code tools/gen-hanzi-seed.py} 的 IDS_STRUCTURE 同规则：
     * 含「？」说明有部件未知，首字符为 ⿸/⿻ 是笔画级分解（会把独体字判成包围结构），
     * 这两类一律留空。</p>
     *
     * @param decomposition 部件分解
     * @return 结构名或 null
     */
    private static String structureOf(String decomposition) {
        if (decomposition == null || decomposition.isBlank()) {
            return null;
        }
        String decomp = decomposition.trim();
        if (decomp.indexOf('？') >= 0) {
            return null;
        }
        if (decomp.length() == 1 && isHanzi(decomp.charAt(0))) {
            return "独体";
        }
        return switch (decomp.charAt(0)) {
            case '⿰' -> "左右";
            case '⿱' -> "上下";
            case '⿲' -> "左中右";
            case '⿳' -> "上中下";
            case '⿴' -> "全包围";
            case '⿵', '⿶', '⿷', '⿹', '⿺' -> "半包围";
            default -> null;
        };
    }

    /**
     * 归一化读音。
     *
     * <p>源数据把 -ng 的 g 写成 U+0261（ɡ，velar click），3500 字里出现 773 次，
     * 不修就是「dīnɡ」——孩子会读错。非拉丁字符（空格、连字符等）一律丢弃。</p>
     *
     * @param text 原始读音
     * @return 归一化读音；无有效字符返回 null
     */
    private static String normalizePinyin(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c == 'ɡ') {
                c = 'g';
            }
            if ((c >= 'a' && c <= 'z') || c == 'ü'
                    || "àáâãäåèéêëìíîïòóôõöùúûüāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ".indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 释义断句标记：出现即表示已进入古籍例句段，对识字教学没有价值。
     */
    private static final String[] MEANING_TAIL_MARKERS = {"同本义", "又如", "--《"};

    /**
     * 释义正文最少汉字数：低于此值视为空壳（如「乎」的释义就是「乎」）
     */
    private static final int MIN_MEANING_CORE_CHARS = 8;

    /**
     * 把字典长释义压成一条教学可用的短释义。
     *
     * @param explanation 原始释义
     * @param headLimit   正文保留长度
     * @return 短释义；不可用返回 null
     */
    private static String condenseMeaning(String explanation, int headLimit) {
        String text = cleanText(explanation);
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.length() <= headLimit) {
            return usable(text) ? text : null;
        }
        String window = text.substring(0, headLimit);
        int cut = -1;
        for (String marker : MEANING_TAIL_MARKERS) {
            int idx = window.indexOf(marker);
            // 只认过半处的标记（靠前的往往只是词头的一部分），并取最早的一个
            if (idx >= headLimit / 2 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        if (cut < 0) {
            cut = Math.max(window.lastIndexOf('。'), Math.max(window.lastIndexOf('；'),
                    Math.max(window.lastIndexOf('，'), window.lastIndexOf(','))));
        }
        if (cut < headLimit / 3) {
            cut = headLimit;
        }
        String head = window.substring(0, Math.min(cut, window.length())).trim();
        // 「又如…」是组词，识字教学用得上，摘出来附在后面
        int wordIdx = text.indexOf("又如");
        if (wordIdx >= 0) {
            int end = wordIdx + 2;
            while (end < text.length() && end - wordIdx < 62
                    && "。；;".indexOf(text.charAt(end)) < 0) {
                end++;
            }
            String words = text.substring(wordIdx + 2, Math.min(end, text.length())).trim();
            if (!words.isEmpty()) {
                head = head + "；组词：" + words;
            }
        }
        head = head.replaceAll("^[，,、;；\\s]+|[，,、;；\\s]+$", "");
        return usable(head) ? head : null;
    }

    /**
     * 释义是否可用：排除空壳（去掉标点后几乎没有汉字）与非中文条目（含谚文/假名）。
     *
     * @param text 释义
     * @return 可用返回 true
     */
    private static boolean usable(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '가' && c <= '힯') || (c >= '぀' && c <= 'ヿ')) {
                return false;
            }
        }
        int core = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isHanzi(text.charAt(i))) {
                core++;
            }
        }
        return core >= MIN_MEANING_CORE_CHARS;
    }

    /**
     * 清排版：换行/制表/分隔线压成空格，NFKC 归一。
     *
     * @param text 原文
     * @return 清理后的文本；无内容返回 null
     */
    private static String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean space = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c) || isSeparator(c)) {
                space = true;
                continue;
            }
            if (space && sb.length() > 0) {
                sb.append(' ');
            }
            space = false;
            sb.append(c);
        }
        String cleaned = sb.toString().trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 是否排版分隔符（长横线、破折号、省略号等）。
     *
     * @param c 字符
     * @return 是返回 true
     */
    private static boolean isSeparator(char c) {
        return (c >= '─' && c <= '╿') || c == '—' || c == '…' || c == '·';
    }

    /**
     * 是否汉字（基本区）。
     *
     * @param c 字符
     * @return 是返回 true
     */
    private static boolean isHanzi(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static String trim(String text, int max) {
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static int intOf(String value, int defaultValue) {
        Integer parsed = intOf(value, null);
        return parsed == null ? defaultValue : parsed;
    }

    private static Integer intOf(String value, Integer defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 单字字典条目。
     *
     * @param pinyin      读音
     * @param strokes     笔画数
     * @param radicals    部首
     * @param more        附加信息（内含「部首 X」文本）
     * @param explanation 释义
     */
    private record DictEntry(String pinyin, Integer strokes, String radicals, String more, String explanation) {
    }

    /**
     * IDS 条目。
     *
     * @param radical       部首
     * @param decomposition 部件分解
     */
    private record IdsEntry(String radical, String decomposition) {
    }
}