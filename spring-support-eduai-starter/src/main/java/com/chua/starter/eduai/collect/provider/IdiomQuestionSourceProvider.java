package com.chua.starter.eduai.collect.provider;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.eduai.collect.spi.CollectedQuestion;
import com.chua.starter.eduai.collect.spi.QuestionSourceContext;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import com.chua.starter.eduai.collect.support.HttpFetchSupport;
import com.chua.starter.eduai.collect.support.IdiomImportSupport;
import com.chua.starter.eduai.collect.support.SourceConfigReader;
import com.chua.starter.eduai.model.EduIdiom;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成语采集源（自落库型）：从三方成语接口/词库 JSON 拉取并写入 edu_idiom + edu_idiom_grade。
 *
 * <p>配置（任意三方接口都能接，只需做字段映射）：
 * <pre>
 * {
 *   "url": "https://api.example.com/idiom/list",  // 三方接口地址，缺省用内置公开词库
 *   "offset": 0,                                   // 起始下标（数组型接口按位取）
 *   "limit": 50,                                   // 采集条数
 *   "dataPath": "data.list",                       // 数组所在路径（点号，可省）
 *   "fields": {                                    // 字段映射（键=库字段，值=接口字段名）
 *     "content": "word", "pinyin": "pinyin", "meaning": "explanation",
 *     "source": "derivation", "example": "example"
 *   },
 *   "gradeLevel": "小学", "grade": "一年级"
 * }
 * </pre>
 * 缺省映射即 chinese-xinhua 词库（word/pinyin/explanation/derivation/example）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Spi(value = "IDIOM", order = 90)
public class IdiomQuestionSourceProvider implements QuestionSourceProvider {

    /**
     * 缺省公开成语词库（约 3 万条，含 拼音/释义/出处/例句）
     */
    public static final String DEFAULT_URL = "https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json";

    /**
     * 公开词库镜像（按序尝试，任一可用即可；配置了 url 时只用配置的地址）
     */
    private static final List<String> MIRRORS = List.of(
            "https://cdn.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
            "https://fastly.jsdelivr.net/gh/pwxcoo/chinese-xinhua@master/data/idiom.json",
            "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json",
            "https://ghproxy.net/https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "IDIOM";
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
        String config = context.getSourceConfig();
        String url = SourceConfigReader.text(config, "url");
        // 未配置起始偏移时自动续采：从已入库数量开始，重复执行每次都有新数据
        String offsetText = SourceConfigReader.text(config, "offset");
        int offset = (offsetText == null || offsetText.isBlank())
                ? (int) IdiomImportSupport.countIdioms()
                : intOf(offsetText, 0);
        int limit = intOf(SourceConfigReader.text(config, "limit"), 50);
        String dataPath = SourceConfigReader.text(config, "dataPath");
        Map<String, String> fields = parseFields(SourceConfigReader.text(config, "fields"));
        String gradeLevel = firstText(SourceConfigReader.text(config, "gradeLevel"), context.getGradeLevel(), "小学");
        String grade = firstText(SourceConfigReader.text(config, "grade"), context.getGrade(), "一年级");

        String json;
        String usedUrl;
        try {
            if (url != null && !url.isBlank()) {
                usedUrl = url.trim();
                json = HttpFetchSupport.get(usedUrl);
            } else {
                // 多镜像容错：依次尝试，避免单一 CDN 不可用时采集失败
                usedUrl = null;
                json = null;
                Exception last = null;
                for (String mirror : MIRRORS) {
                    try {
                        json = HttpFetchSupport.get(mirror);
                        usedUrl = mirror;
                        break;
                    } catch (Exception e) {
                        last = e;
                        log.warn("[collect] 成语词库镜像不可用，尝试下一个: {} 原因: {}", mirror, e.getMessage());
                    }
                }
                if (json == null) {
                    throw new IllegalStateException("成语词库全部镜像不可用: " + (last == null ? "" : last.getMessage()), last);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("成语接口拉取失败: " + e.getMessage(), e);
        }
        List<EduIdiom> idioms = parse(json, 0, Integer.MAX_VALUE, dataPath, fields);
        int saved = importContinuously(idioms, offset, limit, gradeLevel, grade, context.getOwnerUserId());
        log.info("[collect] 成语采集完成: url={} offset={} limit={} 词库={} 入库={} 学段={} 年级={}",
                usedUrl, offset, limit, idioms.size(), saved, gradeLevel, grade);
        return saved;
    }

    /**
     * 连续抽取：从 offset 起向后扫描，直到本次新入库达到 limit 或词库遍历完，
     * 避免“偏移与数据集下标不对齐”导致的采不满（旧数据被去重跳过）。
     *
     * @param all         词库全量（已解析）
     * @param offset      起始下标（自动续采时为已入库数量）
     * @param limit       期望新增条数
     * @param gradeLevel  学段
     * @param grade       年级
     * @param ownerUserId 归属用户
     * @return 实际新增条数
     */
    private int importContinuously(List<EduIdiom> all, int offset, int limit,
                                   String gradeLevel, String grade, String ownerUserId) {
        int cursor = Math.max(0, offset);
        int imported = 0;
        while (cursor < all.size() && imported < limit) {
            int end = Math.min(all.size(), cursor + (limit - imported));
            List<EduIdiom> batch = new ArrayList<>(all.subList(cursor, end));
            if (batch.isEmpty()) {
                break;
            }
            int saved = IdiomImportSupport.importIdioms(batch, gradeLevel, grade, ownerUserId);
            imported += saved;
            cursor = end;
            // 整批都是新数据：说明已对齐，后续无需继续扫描
            if (saved == batch.size()) {
                break;
            }
        }
        return imported;
    }

    /**
     * 解析字段映射配置；缺省映射为 chinese-xinhua 词库字段。
     *
     * @param fieldsJson 配置中的 fields 段（可为空，JSON 对象文本）
     * @return 映射表（键：content/pinyin/meaning/source/example）
     */
    private Map<String, String> parseFields(String fieldsJson) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("content", "word");
        map.put("pinyin", "pinyin");
        map.put("meaning", "explanation");
        map.put("source", "derivation");
        map.put("example", "example");
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return map;
        }
        try {
            JsonNode node = MAPPER.readTree(fieldsJson);
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    map.put(entry.getKey(), entry.getValue().asText());
                }
            });
        } catch (Exception e) {
            log.warn("[collect] 成语字段映射解析失败，使用默认映射: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 解析接口返回并截取 [offset, offset+limit) 区间。
     *
     * @param json     接口返回 JSON
     * @param offset   起始下标
     * @param limit    条数
     * @param dataPath 数组所在路径（点号路径，可空）
     * @param fields   字段映射
     * @return 成语列表
     */
    private List<EduIdiom> parse(String json, int offset, int limit, String dataPath, Map<String, String> fields) {
        List<EduIdiom> list = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode array = locateArray(root, dataPath);
            if (array == null || !array.isArray()) {
                throw new IllegalStateException("成语接口未返回 JSON 数组（可用 dataPath 指定数组路径）");
            }
            int end = Math.min(array.size(), offset + limit);
            for (int i = Math.max(0, offset); i < end; i++) {
                JsonNode node = array.get(i);
                String content = text(node, fields.get("content"));
                if (content == null || content.isBlank()) {
                    continue;
                }
                EduIdiom idiom = new EduIdiom();
                idiom.setIdiomContent(content.trim());
                idiom.setIdiomPinyin(firstText(text(node, fields.get("pinyin")), ""));
                idiom.setIdiomMeaning(firstText(text(node, fields.get("meaning")), ""));
                idiom.setIdiomSource(firstText(text(node, fields.get("source")), ""));
                idiom.setIdiomExample(firstText(text(node, fields.get("example")), ""));
                idiom.setIdiomDifficulty(3);
                idiom.setIdiomTags("");
                list.add(idiom);
            }
        } catch (Exception e) {
            throw new IllegalStateException("成语接口解析失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 定位 JSON 中的成语数组：dataPath 优先，其次根数组，再次 data 字段。
     *
     * @param root     根节点
     * @param dataPath 点号路径（如 data.list）
     * @return 数组节点，找不到返回 null
     */
    private JsonNode locateArray(JsonNode root, String dataPath) {
        if (dataPath != null && !dataPath.isBlank()) {
            JsonNode node = root;
            for (String segment : dataPath.split("\\.")) {
                if (node == null || segment.isBlank()) {
                    continue;
                }
                node = node.get(segment);
            }
            return node;
        }
        if (root.isArray()) {
            return root;
        }
        return root.path("data");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
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
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
