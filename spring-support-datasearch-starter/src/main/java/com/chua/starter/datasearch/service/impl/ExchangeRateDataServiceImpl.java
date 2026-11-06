// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasearch.service.impl;

import com.chua.common.support.datasearch.exchange.spi.ExchangeRateProvider;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClient;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.starter.datasearch.pojo.exchangerate.ExchangeRateOverviewVO;
import com.chua.starter.datasearch.service.ExchangeRateDataService;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 顶栏汇率数据服务实现。
 *
 * <p>两条取数链路，互不阻塞：</p>
 * <ol>
 *   <li><b>最新价</b>：{@link ExchangeRateProvider} SPI（open.er-api.com，USD 基准全量表，
 *       自带 6 小时内存缓存）。SPI 未注册（宿主未引入 utils 数据检索模块）或拿不到表时，
 *       直接调 frankfurter 的 {@code /latest} 兜底——顶栏是可选增强，不能因为缺一个 SPI
 *       就整块空白。</li>
 *   <li><b>周期序列</b>：frankfurter 的日期区间接口
 *       {@code /YYYY-MM-DD..YYYY-MM-DD?from=BASE&to=Q1,Q2}，一次拿到整段收盘价。
 *       前端曾逐日外呼（一个周期几十次请求），后台没有理由重复踩这个坑。</li>
 * </ol>
 *
 * <p>涨跌与极值口径与前端 {@code buildOverview} 一致：以窗口内最早的可用收盘价为基价，
 * 极值取窗口收盘价与最新价的最大/最小。口径不一致会让「后台有数」与「三方兜底」
 * 在切换瞬间数字跳变，用户会以为行情错了。</p>
 *
 * <p>历史序列按「基准币种 + 目标币种集合 + 天数」做 10 分钟内存缓存：一个顶栏组件的
 * 每个用户、每次切周期都会打到这里，不缓存等于把三方当自建接口用。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Service
public class ExchangeRateDataServiceImpl implements ExchangeRateDataService {

    /**
     * 默认周期
     */
    private static final String DEFAULT_PERIOD = "1m";

    /**
     * 默认币种对（与前端 app.yaml 的 other.exchangeRate.symbols 默认值一致）
     */
    private static final List<String> DEFAULT_SYMBOLS =
            List.of("USD/EUR", "USD/GBP", "USD/JPY", "USD/CNY", "USD/HKD", "USD/SGD");

    /**
     * 周期 → 回看天数（与前端 PERIOD_DAYS 一致）
     */
    private static final Map<String, Integer> PERIOD_DAYS = Map.of(
            "1m", 30,
            "3m", 90,
            "6m", 180,
            "12m", 365);

    /**
     * frankfurter 基地址（免费、无需 Key，与前端 FRANKFURTER_CANDIDATES 的公网项一致）
     */
    private static final String FRANKFURTER_BASE = "https://api.frankfurter.app";

    /**
     * 三方外呼超时（毫秒）：取数在页面渲染路径上，宁可空态也不能挂住
     */
    private static final long TIMEOUT_MILLIS = 8000L;

    /**
     * 历史序列缓存有效期（毫秒）：行情按天变动，10 分钟足够，且能挡住切换周期的重复外呼
     */
    private static final long HISTORY_CACHE_MILLIS = 10 * 60 * 1000L;

    /**
     * 历史序列缓存条目上限（防止异常入参把内存撑大）
     */
    private static final int HISTORY_CACHE_LIMIT = 64;

    /**
     * 币种对展示格式的分隔符
     */
    private static final String SYMBOL_SEPARATOR = "/";

    /**
     * 日期区间连接符（frankfurter 的区间语法）
     */
    private static final String RANGE_SEPARATOR = "..";

    /**
     * ISO 币种代码长度
     */
    private static final int CURRENCY_CODE_LENGTH = 3;

    /**
     * 币种对总长度（USDEUR 形态）
     */
    private static final int SYMBOL_LENGTH = CURRENCY_CODE_LENGTH * 2;

    /**
     * 输出保留小数位
     */
    private static final int SCALE = 6;

    /**
     * 百分比换算基数
     */
    private static final double PERCENT_BASE = 100D;

    /**
     * 数据来源标识：SPI 优先
     */
    private static final String SOURCE_OPEN_EXCHANGE = "open-exchange-rate";

    /**
     * 数据来源标识：frankfurter 兜底
     */
    private static final String SOURCE_FRANKFURTER = "frankfurter";

    /**
     * 复用 HTTP 客户端（HttpClientFactory 内部即全局单例）
     */
    private final HttpClient httpClient = HttpClientFactory.getClient();

    /**
     * 历史序列缓存：key 为「base|quotes|days」
     */
    private final Map<String, HistoryEntry> historyCache = new ConcurrentHashMap<>();

    @Override
    public ExchangeRateOverviewVO overview(List<String> symbols, String period) {
        String effectivePeriod = normalizePeriod(period);
        List<Symbol> pairs = parseSymbols(symbols);
        if (pairs.isEmpty()) {
            return null;
        }

        Map<String, Double> latest = loadLatestRates(pairs);
        String source = SOURCE_OPEN_EXCHANGE;
        if (latest.isEmpty()) {
            latest = loadLatestFromFrankfurter(pairs);
            source = SOURCE_FRANKFURTER;
            if (latest.isEmpty()) {
                log.warn("[exchange-rate] 全部最新价数据源不可用");
                return null;
            }
        }

        Map<String, Map<String, Double>> history =
                loadHistory(pairs, PERIOD_DAYS.get(effectivePeriod));
        List<ExchangeRateOverviewVO.Rate> rates = new ArrayList<>(pairs.size());
        List<String> resolvedSymbols = new ArrayList<>(pairs.size());
        for (Symbol pair : pairs) {
            Double rate = latest.get(pair.display());
            if (rate == null || !Double.isFinite(rate)) {
                // 单条取不到就跳过这一条：整块失败会让顶栏从「有 5 个币种」变成「无数据」
                continue;
            }
            rates.add(toRate(pair, rate, history));
            resolvedSymbols.add(pair.display());
        }
        if (rates.isEmpty()) {
            return null;
        }
        return new ExchangeRateOverviewVO(source, effectivePeriod, resolvedSymbols, rates);
    }

    /**
     * 汇率点：最新价 + 周期涨跌 + 周期极值。
     *
     * @param pair    币种对
     * @param rate    最新价
     * @param history 日期（升序）→ (币种 → 收盘价)
     * @return 汇率点
     */
    private ExchangeRateOverviewVO.Rate toRate(Symbol pair, double rate,
                                               Map<String, Map<String, Double>> history) {
        List<Double> closes = closesOf(pair, history);
        double base = closes.isEmpty() ? rate : closes.getFirst();
        double change = rate - base;
        double changePercent = base == 0D ? 0D : change / base * PERCENT_BASE;
        double high = rate;
        double low = rate;
        for (double close : closes) {
            high = Math.max(high, close);
            low = Math.min(low, close);
        }
        return new ExchangeRateOverviewVO.Rate(pair.display(), scale(rate), scale(change),
                scale(changePercent), scale(high), scale(low));
    }

    /**
     * 取某币种对在窗口内的收盘价序列（按日期升序）。
     *
     * @param pair    币种对
     * @param history 日期（升序）→ (币种 → 收盘价)
     * @return 收盘价列表；历史不可用时为空列表
     */
    private List<Double> closesOf(Symbol pair, Map<String, Map<String, Double>> history) {
        List<Double> result = new ArrayList<>(history.size());
        for (Map<String, Double> dayRates : history.values()) {
            Double close = dayRates.get(pair.quote());
            if (close != null && Double.isFinite(close)) {
                result.add(close);
            }
        }
        return result;
    }

    /**
     * 从 SPI 取 USD 基准全量表并按币种对换算。
     *
     * @param pairs 币种对
     * @return 展示币种对 → 最新价；SPI 不可用或无匹配时为 空
     */
    private Map<String, Double> loadLatestRates(List<Symbol> pairs) {
        Map<String, Double> result = new LinkedHashMap<>();
        Map<String, Double> usdRates = null;
        for (ExchangeRateProvider provider : SpiProviders.list(ExchangeRateProvider.class)) {
            try {
                Map<String, Double> rates = provider.getRates("USD");
                if (rates != null && !rates.isEmpty()) {
                    usdRates = rates;
                    break;
                }
            } catch (Exception e) {
                log.warn("[exchange-rate] 汇率 SPI 取数失败: provider={}, msg={}",
                        provider.name(), e.getMessage());
            }
        }
        if (usdRates == null) {
            return result;
        }
        for (Symbol pair : pairs) {
            // 任意币对 from -> to = usdRate(to) / usdRate(from)，与 SPI 的换算口径一致
            Double base = usdRates.get(pair.base());
            Double quote = usdRates.get(pair.quote());
            if (base == null || quote == null || base == 0D) {
                continue;
            }
            result.put(pair.display(), quote / base);
        }
        return result;
    }

    /**
     * frankfurter {@code /latest} 兜底取最新价（按 base 分组，一次请求覆盖该组全部 quote）。
     *
     * @param pairs 币种对
     * @return 展示币种对 → 最新价；全部失败时为 空
     */
    private Map<String, Double> loadLatestFromFrankfurter(List<Symbol> pairs) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> group : groupByBase(pairs).entrySet()) {
            String base = group.getKey();
            String path = "/latest?from=" + base + "&to=" + String.join(",", group.getValue());
            Map<String, Object> body = read(path);
            Map<String, Double> rates = ratesOf(body);
            if (rates == null) {
                continue;
            }
            for (String quote : group.getValue()) {
                Double rate = rates.get(quote);
                if (rate != null && Double.isFinite(rate)) {
                    result.put(base + SYMBOL_SEPARATOR + quote, rate);
                }
            }
        }
        return result;
    }

    /**
     * 取周期窗口内的历史收盘价（日期区间接口，每个 base 一次请求）。
     *
     * @param pairs 币种对
     * @param days  回看天数
     * @return 日期（升序）→ (币种 → 收盘价)；不可用时为空表
     */
    private Map<String, Map<String, Double>> loadHistory(List<Symbol> pairs, int days) {
        // TreeMap 保证按日期升序：「窗口内最早可用收盘价」是涨跌基价，顺序错了涨跌就反了
        Map<String, Map<String, Double>> result = new TreeMap<>();
        for (Map.Entry<String, List<String>> group : groupByBase(pairs).entrySet()) {
            Map<String, Map<String, Double>> part = loadHistory(group.getKey(), group.getValue(), days);
            for (Map.Entry<String, Map<String, Double>> day : part.entrySet()) {
                result.computeIfAbsent(day.getKey(), key -> new LinkedHashMap<>())
                        .putAll(day.getValue());
            }
        }
        return result;
    }

    /**
     * 取单个基准币种的历史序列（优先命中 10 分钟缓存）。
     *
     * @param base   基准币种
     * @param quotes 目标币种
     * @param days   回看天数
     * @return 日期 → (币种 → 收盘价)
     */
    private Map<String, Map<String, Double>> loadHistory(String base, List<String> quotes, int days) {
        String key = base + SYMBOL_SEPARATOR + String.join(",", quotes) + SYMBOL_SEPARATOR + days;
        HistoryEntry cached = historyCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt() < HISTORY_CACHE_MILLIS) {
            return cached.days();
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        String path = SYMBOL_SEPARATOR
                + start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                + RANGE_SEPARATOR
                + end.format(DateTimeFormatter.ISO_LOCAL_DATE)
                + "?from=" + base
                + "&to=" + String.join(",", quotes);
        Map<String, Map<String, Double>> parsed = parseHistory(read(path));
        if (parsed.isEmpty() && cached != null) {
            // 三方抖动时沿用上一份可用序列，比让顶栏走势图突然清空好
            log.warn("[exchange-rate] 历史序列暂不可用，沿用上次缓存: base={}", base);
            return cached.days();
        }
        if (parsed.isEmpty()) {
            return Map.of();
        }
        if (historyCache.size() >= HISTORY_CACHE_LIMIT && !historyCache.containsKey(key)) {
            historyCache.clear();
        }
        historyCache.put(key, new HistoryEntry(parsed, now));
        return parsed;
    }

    /**
     * 解析日期区间响应：{@code {base, start_date, end_date, rates: {date: {quote: rate}}}}。
     *
     * @param body 响应体
     * @return 日期（升序）→ (币种 → 收盘价)；结构不符时为空表
     */
    private Map<String, Map<String, Double>> parseHistory(Map<String, Object> body) {
        Map<String, Object> rates = mapOf(body, "rates");
        Map<String, Map<String, Double>> result = new TreeMap<>();
        if (rates == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : rates.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> dayRates)) {
                continue;
            }
            Map<String, Double> parsed = toRateMap(dayRates);
            if (!parsed.isEmpty()) {
                result.put(entry.getKey(), parsed);
            }
        }
        return result;
    }

    /**
     * 取 latest 响应的 {@code rates} 节点（币种 → 汇率）。
     *
     * @param body 响应体
     * @return 币种 → 汇率；缺失时为空
     */
    private Map<String, Double> ratesOf(Map<String, Object> body) {
        Map<String, Object> rates = mapOf(body, "rates");
        if (rates == null) {
            return null;
        }
        Map<String, Double> parsed = toRateMap(rates);
        return parsed.isEmpty() ? null : parsed;
    }

    /**
     * 取响应体里的对象节点（值不是对象时返回 空）。
     *
     * @param body 响应体
     * @param key  键名
     * @return 对象节点
     */
    private Map<String, Object> mapOf(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * 请求 frankfurter 并解析为对象。
     *
     * @param path 相对路径（含查询串）
     * @return 响应体对象；请求失败或非 2xx 时为 空
     */
    private Map<String, Object> read(String path) {
        String url = FRANKFURTER_BASE + path;
        try {
            ClientResponse response = httpClient.request(url, HttpMethod.GET)
                    .connectTimeout(TIMEOUT_MILLIS)
                    .readTimeout(TIMEOUT_MILLIS)
                    .execute();
            if (response == null || !response.isSuccess()) {
                log.warn("[exchange-rate] 三方返回异常: url={}, code={}",
                        url, response == null ? "无响应" : response.getStatusCode());
                return null;
            }
            return Json.fromJson(response.getBodyString());
        } catch (Exception e) {
            log.warn("[exchange-rate] 三方请求失败: url={}, msg={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 把 JSON 里的数值表归一成 币种（大写）→ double。
     *
     * @param source 原始表
     * @return 归一表
     */
    private Map<String, Double> toRateMap(Map<?, ?> source) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getValue() instanceof Number number)) {
                continue;
            }
            double value = number.doubleValue();
            if (Double.isFinite(value)) {
                result.put(String.valueOf(entry.getKey()).toUpperCase(), value);
            }
        }
        return result;
    }

    /**
     * 按基准币种分组（frankfurter 的 {@code from} 只接受单个 base，{@code to} 接受多个）。
     *
     * @param pairs 币种对
     * @return base → quote 列表（保持入参顺序）
     */
    private Map<String, List<String>> groupByBase(List<Symbol> pairs) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Symbol pair : pairs) {
            List<String> quotes = groups.computeIfAbsent(pair.base(), key -> new ArrayList<>());
            if (!quotes.contains(pair.quote())) {
                quotes.add(pair.quote());
            }
        }
        return groups;
    }

    /**
     * 解析币种对入参（空取内置篮子，非法项跳过）。
     *
     * @param symbols 入参
     * @return 去重后的币种对
     */
    private List<Symbol> parseSymbols(List<String> symbols) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String raw : symbols == null || symbols.isEmpty() ? DEFAULT_SYMBOLS : symbols) {
            Symbol symbol = parseSymbol(raw);
            if (symbol == null) {
                log.warn("[exchange-rate] 无法解析币种对，已跳过: {}", raw);
                continue;
            }
            distinct.add(symbol.display());
        }
        List<Symbol> result = new ArrayList<>(distinct.size());
        for (String display : distinct) {
            Symbol symbol = parseSymbol(display);
            if (symbol != null) {
                result.add(symbol);
            }
        }
        return result;
    }

    /**
     * 解析单个币种对（支持 {@code USD/EUR} 与 {@code USDEUR}）。
     *
     * @param raw 原始写法
     * @return 币种对；非法时为 空
     */
    private Symbol parseSymbol(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        if (value.length() == SYMBOL_LENGTH && isCurrencyCode(value)) {
            return new Symbol(value.substring(0, CURRENCY_CODE_LENGTH), value.substring(CURRENCY_CODE_LENGTH));
        }
        String[] parts = value.split(SYMBOL_SEPARATOR);
        if (parts.length != 2) {
            return null;
        }
        String base = parts[0].trim();
        String quote = parts[1].trim();
        if (!isCurrencyCode(base) || !isCurrencyCode(quote) || base.equals(quote)) {
            return null;
        }
        return new Symbol(base, quote);
    }

    /**
     * 是否合法 ISO 币种代码（三位字母）。
     *
     * @param code 代码
     * @return 合法返回 {@code true}
     */
    private boolean isCurrencyCode(String code) {
        if (code == null || code.length() != CURRENCY_CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isLetter(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 归一周期：非法或缺失取默认。
     *
     * @param period 入参
     * @return 生效周期
     */
    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return DEFAULT_PERIOD;
        }
        String value = period.trim().toLowerCase();
        return PERIOD_DAYS.containsKey(value) ? value : DEFAULT_PERIOD;
    }

    /**
     * 数值保留固定小数位（去掉二进制浮点尾噪，展示与比较才稳定）。
     *
     * @param value 数值
     * @return 四舍五入后的数值
     */
    private double scale(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 币种对（base/quote）。
     *
     * @param base  基准币种
     * @param quote 目标币种
     */
    private record Symbol(String base, String quote) {

        /**
         * 展示用币种对（base/quote）。
         *
         * @return 展示文本
         */
        String display() {
            return base + SYMBOL_SEPARATOR + quote;
        }
    }

    /**
     * 历史序列缓存条目。
     *
     * @param days     日期（升序）→ (币种 → 收盘价)
     * @param loadedAt 加载时刻（毫秒）
     */
    private record HistoryEntry(Map<String, Map<String, Double>> days, long loadedAt) {
    }
}
