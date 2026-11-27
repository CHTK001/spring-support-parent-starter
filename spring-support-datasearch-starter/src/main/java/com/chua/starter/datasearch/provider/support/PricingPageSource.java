package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.PricingSourceConfig;
import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 通用官方价格页读取器：配置决定页面结构，代码只负责四种通用解析方式。
 *
 * <p>设计目标：新增一家厂商只改 {@code provider-pricing-rules.json}，不新增 Java 类：
 * 标准表格、div 网格、模型卡片、正文命名正则各有一条通用路径。任一页面改版都会
 * 返回失败，调用方不会拿旧价覆盖数据库。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public final class PricingPageSource {

    private static final Pattern PRICE = Pattern.compile("[-+]?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern NAMED_GROUP = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    /**
     * [P3C 修复] 正则编译缓存：配置正则按文本缓存（以 (?s) 内嵌开关等价 DOTALL），避免重复编译。
     */
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 工具类私有构造器，禁止实例化。
     */
    private PricingPageSource() {
    }

    /**
     * 抓取并按配置解析官方价格页。
     *
     * @param config      来源配置
     * @param fallbackUrl 登记项 doc（配置未写 url 时使用）
     * @return 刷新结果；失败时 error 有值、rules 为空
     */
    public static ProviderPricingRefresh fetch(PricingSourceConfig config, String fallbackUrl) {
        if (config == null) {
            return ProviderPricingRefresh.unsupported();
        }
        String target = config.getUrl() == null || config.getUrl().isBlank()
                ? fallbackUrl : config.getUrl();
        if (target == null || target.isBlank()) {
            return ProviderPricingRefresh.fail("未配置官方价格页地址");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "Mozilla/5.0 (compatible; SCNet-provider-pricing/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ProviderPricingRefresh.fail("官方价格页 HTTP " + response.statusCode());
            }
            return parse(response.body(), config, target, Instant.now().toString());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ProviderPricingRefresh.fail("官方价格页抓取失败: " + e.getMessage());
        }
    }

    /**
     * 离线解析固定样本（测试入口，不发网络请求）。
     *
     * @param html      HTML
     * @param config    来源配置
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     * @return 刷新结果
     */
    public static ProviderPricingRefresh parse(String html, PricingSourceConfig config,
                                               String sourceUrl, String fetchedAt) {
        if (html == null || html.isBlank()) {
            return ProviderPricingRefresh.fail("官方价格页内容为空");
        }
        if (config == null || config.getType() == null || config.getType().isBlank()) {
            return ProviderPricingRefresh.fail("未配置价格页解析方式");
        }
        try {
            Document doc = Jsoup.parse(html);
            List<ProviderPricingRule> rules = switch (config.getType().trim().toLowerCase(Locale.ROOT)) {
                case "html-table" -> parseTable(doc, config, sourceUrl, fetchedAt);
                case "html-grid" -> parseGrid(doc, config, sourceUrl, fetchedAt);
                case "html-cards" -> parseCards(doc, config, sourceUrl, fetchedAt);
                case "named-regex" -> parseNamedRegex(doc.text(), config, sourceUrl, fetchedAt);
                default -> throw new IllegalArgumentException("未知价格页解析方式: " + config.getType());
            };
            if (rules.isEmpty()) {
                return ProviderPricingRefresh.fail("未解析到价格表（页面结构可能已改版）");
            }
            return ProviderPricingRefresh.ok(rules, fetchedAt);
        } catch (RuntimeException e) {
            return ProviderPricingRefresh.fail("官方价格页解析失败: " + e.getMessage());
        }
    }

    /**
     * 标准 HTML 表格。
     *
     * @param doc       解析后的文档
     * @param config    来源配置
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     * @return 解析出的价格规则列表（可能为空）
     */
    private static List<ProviderPricingRule> parseTable(Document doc, PricingSourceConfig config,
                                                        String sourceUrl, String fetchedAt) {
        String selector = blank(config.getTableSelector()) ? "table" : config.getTableSelector();
        List<ProviderPricingRule> rules = new ArrayList<>(64);
        Set<String> seen = new LinkedHashSet<>();
        for (Element table : doc.select(selector)) {
            Elements headers = table.select("thead th");
            if (headers.isEmpty()) {
                headers = table.select("tr").first() == null
                        ? new Elements() : table.select("tr").first().select("th");
            }
            if (headers.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>(headers.size());
            for (Element header : headers) {
                names.add(header.text());
            }
            int modelIndex = columnIndex(names, "model");
            Map<Integer, String> itemByColumn = itemColumns(names, config);
            if (modelIndex < 0 || !itemByColumn.containsValue("input_cache_miss")
                    || !itemByColumn.containsValue("output")) {
                continue;
            }
            Elements rows = table.select("tbody tr");
            if (rows.isEmpty()) {
                rows = table.select("tr");
            }
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() <= modelIndex || cells.size() < names.size()) {
                    continue;
                }
                String model = model(cells.get(modelIndex).text(), config);
                if (model.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Integer, String> entry : itemByColumn.entrySet()) {
                    int index = entry.getKey();
                    if (index >= cells.size()) {
                        continue;
                    }
                    add(rules, seen, model, entry.getValue(), cells.get(index).text(),
                            config, sourceUrl, fetchedAt);
                }
            }
        }
        return rules;
    }

    /**
     * div 网格：每行由 {@code role="row"} 标记，直接子 div 是单元格。
     *
     * @param doc       解析后的文档
     * @param config    来源配置
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     * @return 解析出的价格规则列表（可能为空）
     */
    private static List<ProviderPricingRule> parseGrid(Document doc, PricingSourceConfig config,
                                                       String sourceUrl, String fetchedAt) {
        String selector = blank(config.getRowSelector()) ? "[role=row]" : config.getRowSelector();
        List<ProviderPricingRule> rules = new ArrayList<>(128);
        Set<String> seen = new LinkedHashSet<>();
        List<String> headers = null;
        Map<Integer, String> itemByColumn = Map.of();
        int modelIndex = -1;
        for (Element row : doc.select(selector)) {
            List<String> cells = directCellTexts(row);
            if (cells.isEmpty()) {
                continue;
            }
            if (headers == null) {
                int maybeModel = columnIndex(cells, "model");
                Map<Integer, String> maybeItems = itemColumns(cells, config);
                if (maybeModel >= 0 && maybeItems.containsValue("input_cache_miss")
                        && maybeItems.containsValue("output")) {
                    headers = cells;
                    modelIndex = maybeModel;
                    itemByColumn = maybeItems;
                }
                continue;
            }
            if (cells.size() < headers.size() || modelIndex >= cells.size()) {
                continue;
            }
            String model = model(cells.get(modelIndex), config);
            if (model.isEmpty()) {
                continue;
            }
            for (Map.Entry<Integer, String> entry : itemByColumn.entrySet()) {
                if (entry.getKey() < cells.size()) {
                    add(rules, seen, model, entry.getValue(), cells.get(entry.getKey()),
                            config, sourceUrl, fetchedAt);
                }
            }
        }
        return rules;
    }

    /**
     * 模型卡片：标题 + 一组 label/value 价格行。
     *
     * @param doc       解析后的文档
     * @param config    来源配置
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     * @return 解析出的价格规则列表（可能为空）
     */
    private static List<ProviderPricingRule> parseCards(Document doc, PricingSourceConfig config,
                                                        String sourceUrl, String fetchedAt) {
        String cardSelector = blank(config.getCardSelector()) ? "[class*=modelCard]" : config.getCardSelector();
        String titleSelector = blank(config.getTitleSelector()) ? "h3" : config.getTitleSelector();
        String rowSelector = blank(config.getPriceRowSelector()) ? "[class*=priceRow]" : config.getPriceRowSelector();
        String labelSelector = blank(config.getLabelSelector()) ? "[class*=priceLabel]" : config.getLabelSelector();
        String valueSelector = blank(config.getValueSelector()) ? "[class*=priceValue]" : config.getValueSelector();
        List<ProviderPricingRule> rules = new ArrayList<>(32);
        Set<String> seen = new LinkedHashSet<>();
        for (Element card : doc.select(cardSelector)) {
            Element title = card.selectFirst(titleSelector);
            if (title == null) {
                continue;
            }
            String model = model(title.text(), config);
            if (model.isEmpty()) {
                continue;
            }
            Set<String> perCard = new LinkedHashSet<>();
            for (Element row : card.select(rowSelector)) {
                Element labelElement = row.selectFirst(labelSelector);
                Element valueElement = row.selectFirst(valueSelector);
                if (labelElement == null || valueElement == null) {
                    continue;
                }
                String item = cardItem(labelElement.text());
                if (item == null || !perCard.add(item)) {
                    // 同一卡片里 Input / Output 可能按上下文长度分档，先取第一档；
                    // 分档价格不能塞进一条 standardPrice，避免把两档混成一个数
                    continue;
                }
                add(rules, seen, model, item, valueElement.text(), config, sourceUrl, fetchedAt);
            }
        }
        return rules;
    }

    /**
     * 正文命名正则：每次匹配是一行，命名组即计费项。
     *
     * @param text      页面正文文本
     * @param config    来源配置
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     * @return 解析出的价格规则列表（可能为空）
     * @throws IllegalArgumentException pattern 未配置或缺少 model 命名组
     */
    private static List<ProviderPricingRule> parseNamedRegex(String text, PricingSourceConfig config,
                                                             String sourceUrl, String fetchedAt) {
        if (blank(config.getPattern())) {
            throw new IllegalArgumentException("named-regex 未配置 pattern");
        }
        // [P3C 修复] 走缓存复用预编译 Pattern；前缀 (?s) 与原 Pattern.DOTALL 行为一致
        Pattern pattern = PATTERN_CACHE.computeIfAbsent("(?s)" + config.getPattern(), Pattern::compile);
        Set<String> groups = new LinkedHashSet<>();
        Matcher names = NAMED_GROUP.matcher(config.getPattern());
        while (names.find()) {
            groups.add(names.group(1));
        }
        if (!groups.contains("model")) {
            throw new IllegalArgumentException("named-regex 必须包含 model 命名组");
        }
        List<ProviderPricingRule> rules = new ArrayList<>(32);
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String model = model(matcher.group("model"), config);
            if (model.isEmpty()) {
                continue;
            }
            for (String group : groups) {
                if ("model".equals(group)) {
                    continue;
                }
                String value;
                try {
                    value = matcher.group(group);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                add(rules, seen, model, groupItem(group, config), value, config, sourceUrl, fetchedAt);
            }
        }
        return rules;
    }

    /**
     * 捕获组名 → 计费项；没配置映射时把组名直接当计费项。
     *
     * @param group  命名组名
     * @param config 来源配置
     * @return 计费项名称
     */
    private static String groupItem(String group, PricingSourceConfig config) {
        Map<String, String> mapping = config.getGroupItems();
        if (mapping != null && mapping.containsKey(group)) {
            return mapping.get(group);
        }
        return group;
    }

    /**
     * 写一条规则；空值 / 占位符直接跳过。
     *
     * @param rules     接收规则的目标列表
     * @param seen      去重集合
     * @param model     模型名
     * @param item      计费项
     * @param rawPrice  原始价格文本
     * @param config    来源配置
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     */
    private static void add(List<ProviderPricingRule> rules, Set<String> seen,
                            String model, String item, String rawPrice,
                            PricingSourceConfig config, String sourceUrl, String fetchedAt) {
        BigDecimal price = price(rawPrice, config.isIncludeFree());
        if (price == null || item == null || item.isBlank()) {
            return;
        }
        String key = model.toLowerCase(Locale.ROOT) + '\u0000' + item;
        if (!seen.add(key)) {
            return;
        }
        ProviderPricingRule rule = new ProviderPricingRule();
        rule.setModelPattern(model);
        rule.setItem(item);
        rule.setUnit(blank(config.getUnit()) ? "token" : config.getUnit());
        rule.setQuantity(config.getQuantity() == null ? new BigDecimal("1000000") : config.getQuantity());
        rule.setStandardPrice(price);
        rule.setCurrency(blank(config.getCurrency()) ? "USD" : config.getCurrency());
        rule.setNote(blank(config.getNote()) ? "官方价格页实时抓取" : config.getNote());
        rule.setSourceUrl(sourceUrl);
        rule.setVerified(true);
        rule.setFetchedAt(fetchedAt);
        rules.add(rule);
    }

    /**
     * 表头 → 列计费项。
     *
     * @param headers 表头文本列表
     * @param config  来源配置
     * @return 列下标 → 计费项映射（可能为空）
     */
    private static Map<Integer, String> itemColumns(List<String> headers, PricingSourceConfig config) {
        Map<Integer, String> out = new LinkedHashMap<>();
        Map<String, String> configured = config.getColumns();
        if (CollectionUtils.isEmpty(configured)) {
            return out;
        }
        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalize(headers.get(i));
            for (Map.Entry<String, String> entry : configured.entrySet()) {
                String key = normalize(entry.getKey());
                if (!key.isEmpty() && (normalized.equals(key) || normalized.contains(key))) {
                    out.putIfAbsent(i, entry.getValue());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * 找列下标：按归一化后的完整匹配，找不到再按包含匹配。
     *
     * @param headers 表头文本列表
     * @param name    要找的列名
     * @return 列下标，找不到返回 -1
     */
    private static int columnIndex(List<String> headers, String name) {
        String wanted = normalize(name);
        for (int i = 0; i < headers.size(); i++) {
            if (normalize(headers.get(i)).equals(wanted)) {
                return i;
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            if (normalize(headers.get(i)).contains(wanted)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 直接子 div 的文本；卡片内的 span/p 不会被误当成下一列。
     *
     * @param row 行元素
     * @return 单元格文本列表
     */
    private static List<String> directCellTexts(Element row) {
        List<String> cells = new ArrayList<>();
        for (Element child : row.children()) {
            if ("div".equals(child.tagName())) {
                cells.add(child.text());
            }
        }
        return cells;
    }

    /**
     * 卡片价格标签 → 计费项。
     *
     * @param label 价格标签文本
     * @return 计费项名称，无法识别时返回 null
     */
    private static String cardItem(String label) {
        String value = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("read")) {
            return "input_cache_hit";
        }
        if (value.startsWith("write")) {
            return "input_cache_write";
        }
        if (value.startsWith("input")) {
            return "input_cache_miss";
        }
        if (value.startsWith("output")) {
            return "output";
        }
        return null;
    }

    /**
     * 官方模型名 → 目录模型名。
     *
     * @param raw    原始模型名
     * @param config 来源配置
     * @return 规整后的模型名，空输入返回空串
     */
    private static String model(String raw, PricingSourceConfig config) {
        String value = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            return "";
        }
        if (config.getStripPrefixes() != null) {
            for (String prefix : config.getStripPrefixes()) {
                if (StringUtils.isNotEmpty(prefix) && value.startsWith(prefix)) {
                    value = value.substring(prefix.length()).trim();
                }
            }
        }
        if (config.getStripSuffixes() != null) {
            for (String suffix : config.getStripSuffixes()) {
                if (StringUtils.isNotEmpty(suffix) && value.endsWith(suffix)) {
                    value = value.substring(0, value.length() - suffix.length()).trim();
                }
            }
        }
        return (config.getModelPrefix() == null ? "" : config.getModelPrefix())
                + value
                + (config.getModelSuffix() == null ? "" : config.getModelSuffix());
    }

    /**
     * 价格文本 → 数值；Free 写 0，横杠/空值不写。
     *
     * @param raw         原始价格文本
     * @param includeFree 是否把 Free 计为 0
     * @return 价格数值，无法解析时返回 null
     */
    private static BigDecimal price(String raw, boolean includeFree) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "-".equals(value) || "—".equals(value) || "n/a".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.toLowerCase(Locale.ROOT).contains("free")) {
            return includeFree ? BigDecimal.ZERO : null;
        }
        Matcher matcher = PRICE.matcher(value.replace(",", ""));
        return matcher.find() ? new BigDecimal(matcher.group()) : null;
    }

    /**
     * 归一化表头 / 列名：小写，去掉空格、斜杠和标点，保留字母数字。
     *
     * @param value 原始文本
     * @return 归一化文本
     */
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("cached", "cache")
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * 空串判断。
     *
     * @param value 待判断文本
     * @return true 表示 null 或空白
     */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
