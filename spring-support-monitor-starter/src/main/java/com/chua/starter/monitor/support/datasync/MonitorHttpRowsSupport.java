package com.chua.starter.monitor.support.datasync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 输入源的行读取器（{@code HTTP_JSON} / {@code HTTP_HTML}）。
 *
 * <p>两条路线的差别只在「响应体 → 行」这一步：</p>
 * <ul>
 *   <li><b>JSON</b>（{@link SyncSourceType#HTTP_JSON}）：Jackson 解析后按 {@code jsonPath} 定位到数组，
 *       数组里每个对象即一行，对象的顶层字段即列名。值统一转字符串（数字、布尔转文本，
 *       嵌套对象/数组转紧凑 JSON），与文件源保持同一形状，字段映射与写入端不用分支。</li>
 *   <li><b>HTML</b>（{@link SyncSourceType#HTTP_HTML}）：jsoup 解析后按 CSS 选择器取出所有行，
 *       再在每行里按单元格选择器取值。列数不齐时补空/截断，不让一行坏掉整个任务。</li>
 * </ul>
 *
 * <p><b>为什么不分页</b>：见 {@link SyncHttpOptions} 的类注释——重复抓取由写入模式去重，
 * 控制台不猜游标字段。</p>
 *
 * <p><b>为什么不是流式</b>：文件源能逐行流式是因为行在磁盘上；HTTP 响应必须先整体收完
 * （要先看状态码、编码、正文），因此这里返回 {@code Flux.fromIterable}，
 * 内存占用与「单次响应体大小」成正比——大响应请让服务端支持筛选或分页参数。</p>
 *
 * <p><b>SSRF 说明</b>：本类会让后端向配置的 URL 发请求，URL 由监控台上的运维人员填写，
 * 属受信运维面（{@code /ms/**} 由鉴权网关把关）。为免被当成通用跳板，协议只允许 http/https，
 * 其余（file、ftp、jar、gopher 等）一律拒绝。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public final class MonitorHttpRowsSupport {

    /**
     * JSON 解析器：不加额外配置，够用且与 Spring 的 ObjectMapper 默认行为一致
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * HTTP 客户端：跟随重定向（不少接口 302 到登录页再 200）
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 点号/下标路径分段（{@code data.list[0].name} → data, list, 0, name）
     */
    private static final Pattern PATH_TOKEN = Pattern.compile("[^.\\[\\]]+");

    /**
     * Content-Type 里取 charset
     */
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 允许的协议
     */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * 工具类不允许实例化
     */
    private MonitorHttpRowsSupport() {
    }

    /**
     * 读取全部行。
 *
     * @param type    源类型（HTTP_JSON / HTTP_HTML）
     * @param url     请求地址
     * @param options 源选项
     * @return 行流，每行是「列名 → 字符串值」
     * @throws Exception 请求失败或响应无法解析
     */
    public static Flux<Map<String, Object>> read(SyncSourceType type, String url, SyncHttpOptions options) throws Exception {
        return Flux.fromIterable(rows(type, url, options));
    }

    /**
     * 读取全部行（列表形态）。
     *
     * <p>执行路径用这个而不是 {@link #read}：一次请求的响应同时用来「推导源列」和「写库」，
     * 避免两次请求——接口带一次性 token 或限流时，第二次请求会直接失败或拿到不同快照。</p>
     *
     * @param type    源类型（HTTP_JSON / HTTP_HTML）
     * @param url     请求地址
     * @param options 源选项
     * @return 行列表，每行是「列名 → 字符串值」
     * @throws Exception 请求失败或响应无法解析
     */
    public static List<Map<String, Object>> rows(SyncSourceType type, String url, SyncHttpOptions options) throws Exception {
        SyncHttpOptions effective = normalize(options);
        String body = fetch(url, effective);
        return type == SyncSourceType.HTTP_HTML
                ? rowsOfHtml(body, url, effective)
                : rowsOfJson(body, effective);
    }

    /**
     * 读取源列（预览/执行前的源列解析用，不产出数据行）。
     *
     * @param type    源类型（HTTP_JSON / HTTP_HTML）
     * @param url     请求地址
     * @param options 源选项
     * @return 列名列表
     * @throws Exception 请求失败或响应无法解析
     */
    public static List<String> headers(SyncSourceType type, String url, SyncHttpOptions options) throws Exception {
        SyncHttpOptions effective = normalize(options);
        String body = fetch(url, effective);
        if (type == SyncSourceType.HTTP_HTML) {
            return htmlHeaders(body, url, effective);
        }
        List<String> columns = new ArrayList<>();
        for (Map<String, Object> row : rowsOfJson(body, effective)) {
            for (String key : row.keySet()) {
                if (!columns.contains(key)) {
                    columns.add(key);
                }
            }
        }
        return columns;
    }

    /**
     * 归一化选项。
     *
     * @param options 原选项
     * @return 归一化后的选项
     */
    private static SyncHttpOptions normalize(SyncHttpOptions options) {
        return options == null ? SyncHttpOptions.defaults().normalized() : options.normalized();
    }

    /**
     * 发请求取响应体。
     *
     * @param url     请求地址
     * @param options 归一化后的选项
     * @return 响应体（已按编码解码的文本）
     * @throws Exception 地址非法或请求失败
     */
    private static String fetch(String url, SyncHttpOptions options) throws Exception {
        URI uri = parseUri(url);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(options.timeoutSeconds()))
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                // 显式声明编码：国内不少接口/页面无视响应头按 UTF-8 解，直接乱码
                .header("Accept-Charset", options.encoding());
        options.headers().forEach((k, v) -> {
            // headers 里带了 Cookie、而 Cookie 字段也有值时，只用字段值：
            // 两个 Cookie 头会被服务端按某种顺序拼起来，得到非预期的会话。
            if (options.hasCookie() && k != null && "cookie".equalsIgnoreCase(k.trim())) {
                return;
            }
            builder.header(k, v);
        });
        // Cookie 单独下发：JDK 的 HttpRequest 对 Connection/Content-Length/Expect/Host/Upgrade
        // 这些头会直接抛 IllegalArgumentException，Cookie 不在其中，可以正常设置。
        if (options.hasCookie()) {
            builder.header("Cookie", options.cookie().trim());
        }
        if (options.hasBody() && !"GET".equals(options.method())) {
            builder.method(options.method(), HttpRequest.BodyPublishers.ofString(options.body(), options.charset()));
        } else {
            builder.method(options.method(), HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<byte[]> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        byte[] bytes = response.body() == null ? new byte[0] : response.body();
        String text = new String(bytes, charsetOf(response, options));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + "（" + url + "）" + snippet(text));
        }
        return text;
    }

    /**
     * 解析并校验地址。
     *
     * @param url 原始地址
     * @return URI
     * @throws IllegalArgumentException 地址为空、语法错或协议不允许
     */
    private static URI parseUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("请求地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("请求地址不合法: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("只支持 http/https 地址: " + url);
        }
        return uri;
    }

    /**
     * 判定字符集：显式配置优先，其次响应头，最后 UTF-8。
     *
     * @param response 响应
     * @param options  归一化后的选项
     * @return 字符集
     */
    private static Charset charsetOf(HttpResponse<byte[]> response, SyncHttpOptions options) {
        String fromHeader = response.headers().firstValue("content-type")
                .map(MonitorHttpRowsSupport::charsetOfContentType)
                .orElse(null);
        return fromHeader == null ? options.charset() : safeCharset(fromHeader, options.charset());
    }

    /**
     * 从 Content-Type 里取 charset 名。
     *
     * @param contentType Content-Type 头值
     * @return 编码名，取不到返回 null
     */
    private static String charsetOfContentType(String contentType) {
        Matcher matcher = CHARSET.matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 安全取字符集。
     *
     * @param name     编码名
     * @param fallback 回落编码
     * @return 字符集
     */
    private static Charset safeCharset(String name, Charset fallback) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            log.debug("[sync-task] 不认识的编码名 {}, 回落 {}", name, fallback);
            return fallback;
        }
    }

    /**
     * JSON 响应 → 行。
     *
     * @param body    响应体
     * @param options 归一化后的选项
     * @return 行列表
     * @throws Exception 解析失败或未定位到数组
     */
    private static List<Map<String, Object>> rowsOfJson(String body, SyncHttpOptions options) throws Exception {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + e.getMessage(), e);
        }
        JsonNode array = locate(root, options.jsonPath());
        if (array == null || array.isMissingNode() || array.isNull()) {
            throw new IllegalStateException("按「数据位置」=" + options.jsonPath()
                    + " 没找到数据，请确认响应结构（根类型 "
                    + root.getNodeType().toString().toLowerCase(Locale.ROOT) + "）");
        }
        if (!array.isArray()) {
            throw new IllegalStateException("「数据位置」必须指向数组，当前指向 "
                    + array.getNodeType().toString().toLowerCase(Locale.ROOT));
        }
        List<Map<String, Object>> rows = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            rows.add(rowOfJson(node));
        }
        return rows;
    }

    /**
     * 按点号路径（含下标）定位 JSON 节点。
     *
     * @param root 根节点
     * @param path 路径，可为空
     * @return 目标节点，未命中返回 null
     */
    private static JsonNode locate(JsonNode root, String path) {
        if (path == null || path.isBlank()) {
            return autoArray(root);
        }
        JsonNode node = root;
        Matcher matcher = PATH_TOKEN.matcher(path);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (node == null || token.isEmpty()) {
                return null;
            }
            // 数组只能用 int 下标取，path(String) 对数组取不到
            node = node.isArray() && token.chars().allMatch(Character::isDigit)
                    ? node.path(Integer.parseInt(token))
                    : node.path(token);
        }
        return node;
    }

    /**
     * 未配路径时，根是对象就取第一个数组字段。
     *
     * <p>多数接口是 {@code {"code":0,"data":[...]}} 这种形状，让用户先研究响应才能填出路径，
     * 会让「配一次就能跑」变成一道门槛。取到就记一条日志，猜错时用户看日志就知道该显式配路径。</p>
     *
     * @param root 根节点
     * @return 定位到的数组节点；根不是对象时返回根本身
     */
    private static JsonNode autoArray(JsonNode root) {
        if (root == null || !root.isObject()) {
            return root;
        }
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            if (entry.getValue().isArray()) {
                log.info("[sync-task] 未配置「数据位置」，自动取根对象里的数组字段: {}", entry.getKey());
                return entry.getValue();
            }
        }
        return root;
    }

    /**
     * JSON 元素 → 一行。
     *
     * @param node 数组元素
     * @return 行数据
     */
    private static Map<String, Object> rowOfJson(JsonNode node) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (node.isObject()) {
            node.properties().forEach(entry -> row.put(entry.getKey(), textOf(entry.getValue())));
            return row;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                row.put("第" + (i + 1) + "列", textOf(node.get(i)));
            }
            return row;
        }
        row.put("值", textOf(node));
        return row;
    }

    /**
     * JSON 值 → 字符串（标量给文本，容器给紧凑 JSON）。
     *
     * @param node 节点
     * @return 字符串值
     */
    private static String textOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    /**
     * HTML 响应 → 表头。
     *
     * @param body    响应体
     * @param url     地址（用于相对链接解析）
     * @param options 归一化后的选项
     * @return 列名
     * @throws IllegalStateException 没有解析到任何行
     */
    private static List<String> htmlHeaders(String body, String url, SyncHttpOptions options) {
        for (Element row : selectRows(body, url, options)) {
            List<String> cells = cellsOf(row, options);
            if (!cells.isEmpty()) {
                // 表头只能取首个非空行：不配「首行是表头」时，也没有别的依据推出列名，
                // 与 rowsOfHtml 里「首行恒定列名」的取舍保持一致
                return headersOf(cells);
            }
        }
        throw new IllegalStateException("没有解析到表格行，请检查「行选择器」=" + options.rowSelector());
    }

    /**
     * HTML 响应 → 行。
     *
     * @param body    响应体
     * @param url     地址
     * @param options 归一化后的选项
     * @return 行列表
     * @throws IllegalStateException 没有解析到任何行
     */
    private static List<Map<String, Object>> rowsOfHtml(String body, String url, SyncHttpOptions options) {
        List<String> headers = null;
        List<Map<String, Object>> result = new ArrayList<>();
        int ragged = 0;
        for (Element row : selectRows(body, url, options)) {
            List<String> cells = cellsOf(row, options);
            if (cells.isEmpty()) {
                continue;
            }
            if (headers == null) {
                headers = headersOf(cells);
                if (!options.headerRow()) {
                    result.add(rowOf(cells, headers));
                }
                continue;
            }
            if (cells.size() != headers.size()) {
                ragged++;
            }
            result.add(rowOf(cells, headers));
        }
        if (headers == null) {
            throw new IllegalStateException("没有解析到表格行，请检查「行选择器」=" + options.rowSelector());
        }
        if (ragged > 0) {
            log.info("[sync-task] 有 {} 行的单元格数与表头不一致，已按补空/截断处理（选择器 {}）",
                    ragged, options.cellSelector());
        }
        return result;
    }

    /**
     * 取所有匹配行。
     *
     * @param body    响应体
     * @param url     地址
     * @param options 归一化后的选项
     * @return 行元素
     */
    private static Elements selectRows(String body, String url, SyncHttpOptions options) {
        Document document = Jsoup.parse(body, url);
        return document.select(options.rowSelector());
    }

    /**
     * 取一行的单元格文本。
     *
     * @param row     行
     * @param options 归一化后的选项
     * @return 文本列表
     */
    private static List<String> cellsOf(Element row, SyncHttpOptions options) {
        Elements cells = row.select(options.cellSelector());
        List<String> values = new ArrayList<>(cells.size());
        for (Element cell : cells) {
            // text() 把换行与多余空白压成空格，表格里这才是「看到的值」
            values.add(cell.text().trim());
        }
        return values;
    }

    /**
     * 单元格文本 → 列名（空值退化为「第N列」，重复名补后缀）。
     *
     * <p>重复名必须去重：行是 {@code Map}，两列同名时后者会覆盖前者，数据静默少一列。</p>
     *
     * @param cells 文本列表
     * @return 列名
     */
    private static List<String> headersOf(List<String> cells) {
        List<String> headers = new ArrayList<>(cells.size());
        Set<String> used = new LinkedHashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            String value = cells.get(i);
            String name = value == null || value.isBlank() ? "第" + (i + 1) + "列" : value;
            String unique = name;
            int suffix = 2;
            while (!used.add(unique)) {
                unique = name + "_" + suffix++;
            }
            headers.add(unique);
        }
        return headers;
    }

    /**
     * 单元格文本 → 一行（不足补空，多余截断）。
     *
     * @param cells   文本列表
     * @param headers 列名
     * @return 行数据
     */
    private static Map<String, Object> rowOf(List<String> cells, List<String> headers) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < cells.size() ? cells.get(i) : null;
            row.put(headers.get(i), value == null || value.isBlank() ? null : value);
        }
        return row;
    }

    /**
     * 错误信息里的响应片段（接口报错时那行 JSON 往往就是唯一线索）。
     *
     * @param text 响应体
     * @return 片段
     */
    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "（响应体为空）";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return "响应片段: " + (flat.length() > 200 ? flat.substring(0, 200) + "…" : flat);
    }
}