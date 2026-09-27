package com.chua.starter.monitor.support.nginx;

import com.chua.starter.monitor.support.model.NginxAccessTopVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nginx access log 解析器：把日志文本聚合成 Top 统计。
 *
 * <p>目标是「格式不认识就跳过，而不是抛异常」：真实现场的 log_format 经常被改过，
 * 解析器一旦因为某一行格式不同就整体失败，Top 图会永远空白且看不出原因。
 * 因此逐行匹配，匹配不上的行计入未解析数后跳过。</p>
 *
 * <p><strong>支持的默认格式</strong>（nginx 官方 combined）：
 * <pre>
 * $remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent ...
 * </pre>
 * 例如：
 * <pre>
 * 192.168.1.10 - - [27/Sep/2026:09:00:01 +0800] "GET /api/v1/items HTTP/1.1" 200 512 "-" "curl/8.0"
 * </pre>
 * </p>
 *
 * <p>路径归一化：把 {@code /api/items/123} 里的数字 ID 换成 {@code :id}，
 * 否则带 ID 的接口会把 Top 榜刷成几百条互不相干的路径，看不出主要入口。
 * 归一化只替换路径的最后一段数字与 UUID，不动中间段（{@code /v1/items/12} 仍算不同路径，
 * 因为是否归一化中间段属于业务语义，不该由日志分析器替用户决定）。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class NginxAccessLogParser {

    /**
     * 默认 combined 格式的正则。
     *
     * <p>{@code request} 用非贪婪匹配并允许为空（有些格式不记 request）。</p>
     */
    private static final Pattern COMBINED = Pattern.compile(
            "^(\\S+)\\s+\\S+\\s+\\S+\\s+\\[([^\\]]+)\\]\\s+\"([^\"]*)\"\\s+(\\d{3})\\b");

    /**
     * 时间戳格式：27/Sep/2026:09:00:01 +0800
     */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", java.util.Locale.ENGLISH);

    /**
     * 路径里的纯数字段（末尾）
     */
    private static final Pattern TRAILING_ID = Pattern.compile("/\\d+(?=/|$)");

    /**
     * 路径里的 UUID 段（末尾）
     */
    private static final Pattern TRAILING_UUID = Pattern.compile(
            "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");

    /**
     * 常见 HTTP 方法：用于判断 $request 里第一个 token 是不是方法名。
     *
     * <p>不能简单写成「取第二个 token」——有的站点 log_format 只记路径不记方法，
     * 那样路径就成了第一个 token。这里按「首 token 是已知方法」来判断，才稳。</p>
     */
    private static final java.util.Set<String> HTTP_METHODS = java.util.Set.of(
            "GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH", "TRACE", "CONNECT");

    /**
     * 工具类不允许实例化。
     */
    private NginxAccessLogParser() {
    }

    /**
     * 解析并聚合。
     *
     * @param text      日志文本（一般是 tail 出来的尾部若干行）
     * @param topN      每个维度取前 N
     * @param since     只统计该时刻之后的行（含）；为 null 表示不按时间过滤
     * @param logName   日志文件名，用于提示信息
     * @return Top 统计
     */
    public static NginxAccessTopVO parse(String text, int topN, LocalDateTime since, String logName) {
        if (text == null || text.isBlank()) {
            return NginxAccessTopVO.empty(0, logName + " 是空的，没有访问记录可供统计。");
        }
        String[] lines = text.split("\\R");
        Map<String, long[]> paths = new LinkedHashMap<>();
        Map<String, long[]> ips = new LinkedHashMap<>();
        Map<String, long[]> statuses = new LinkedHashMap<>();
        int parsed = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            Matcher m = COMBINED.matcher(line);
            // 用 find() 而不是 matches()：matches() 要求整行匹配，
            // 而 combined 格式在 $status 之后还有 $body_bytes_sent / referer / user-agent，
            // 用 matches() 会让每一行都判为「格式不匹配」，Top 永远空白。
            // 模式本身已用 ^ 锚定行首，find() 的语义正是「从行首开始的一段」。
            if (!m.find()) {
                continue;
            }
            String ip = m.group(1);
            String time = m.group(2);
            String request = m.group(3);
            int status = Integer.parseInt(m.group(4));
            if (since != null && !withinSince(time, since)) {
                continue;
            }
            parsed++;
            String path = normalizePath(request);
            if (path != null) {
                paths.computeIfAbsent(path, k -> new long[1])[0]++;
            }
            ips.computeIfAbsent(ip, k -> new long[1])[0]++;
            statuses.computeIfAbsent(category(status), k -> new long[1])[0]++;
        }
        if (parsed == 0) {
            return NginxAccessTopVO.empty(lines.length,
                    "扫描了 " + lines.length + " 行，但没有一行符合 nginx combined 格式"
                            + "（$remote_addr [$time_local] \"$request\" $status）。"
                            + "如果站点改过 log_format，这里解析不出来。");
        }
        String note = "统计口径：最近 " + lines.length + " 行中解析成功的 " + parsed + " 行";
        if (since != null) {
            note += "（且不早于 " + since + "）";
        }
        note += "。高流量时最近 N 行只覆盖很短的时间段，不等于「最近 N 分钟」。";
        return new NginxAccessTopVO(
                parsed,
                lines.length,
                topItems(paths, topN),
                topItems(ips, topN),
                statusItems(statuses, parsed),
                note);
    }

    /**
     * 判断时间戳是否不早于 since。
     *
     * <p>时间戳解析失败时<strong>保守放行</strong>：宁可多统计一行，
     * 也不要因为时间格式变了一行就把整段时间窗的数据丢掉。</p>
     *
     * @param rawTime 日志里的时间串
     * @param since   起点
     * @return 是否应计入
     */
    private static boolean withinSince(String rawTime, LocalDateTime since) {
        try {
            return LocalDateTime.parse(rawTime, TS).isAfter(since);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * 从 request 里取路径并归一化。
     *
     * @param request 请求行，如 {@code GET /a/b/12 HTTP/1.1}
     * @return 归一化后的路径；无法解析时返回 null
     */
    private static String normalizePath(String request) {
        String r = request == null ? "" : request.trim();
        if (r.isEmpty()) {
            return null;
        }
        // $request 的标准形态是 "METHOD PATH PROTOCOL"（如 "GET /a/b HTTP/1.1"），
        // 路径是**第二个** token。早先取第一个 token 会把所有条目都统计成 "GET"。
        // 也有站点把 log_format 写成只记路径（没有方法与协议），这种情况第一个 token 就是路径。
        String[] parts = r.split("\\s+");
        String target;
        if (parts.length >= 2 && HTTP_METHODS.contains(parts[0].toUpperCase(java.util.Locale.ROOT))) {
            target = parts[1];
        } else {
            target = parts[0];
        }
        // 形如 HTTP/1.1 的裸协议行（$request 只记了协议）没有路径
        if (target.startsWith("HTTP/") || target.startsWith("https:") || target.startsWith("http:")) {
            return null;
        }
        int q = target.indexOf('?');
        String path = q > 0 ? target.substring(0, q) : target;
        path = TRAILING_UUID.matcher(path).replaceFirst("/:id");
        path = TRAILING_ID.matcher(path).replaceFirst("/:id");
        return path.isEmpty() ? "/" : path;
    }

    /**
     * 状态码归类。
     *
     * @param status 状态码
     * @return 类别
     */
    private static String category(int status) {
        return switch (status / 100) {
            case 2 -> "2xx 成功";
            case 3 -> "3xx 重定向";
            case 4 -> "4xx 客户端错误";
            case 5 -> "5xx 服务端错误";
            default -> "other";
        };
    }

    /**
     * 取 Top N。
     *
     * <p>排序：次数降序；次数相同时按 key 字典序，保证同一份日志每次渲染顺序稳定，
     * 否则两次刷新 Top 榜顺序乱跳会被误读成「流量在剧烈变化」。</p>
     *
     * @param src  计数
     * @param topN 取前几
     * @return 条目列表
     */
    private static List<NginxAccessTopVO.Item> topItems(Map<String, long[]> src, int topN) {
        List<NginxAccessTopVO.Item> list = new ArrayList<>(src.size());
        src.forEach((k, v) -> list.add(new NginxAccessTopVO.Item(k, v[0])));
        list.sort(Comparator.<NginxAccessTopVO.Item>comparingLong(NginxAccessTopVO.Item::hits)
                .reversed()
                .thenComparing(NginxAccessTopVO.Item::key));
        return list.size() > topN ? new ArrayList<>(list.subList(0, topN)) : list;
    }

    /**
     * 状态码分布（不截断，类别只有 5 个）。
     *
     * @param src   计数
     * @param total 已解析行数
     * @return 条目列表
     */
    private static List<NginxAccessTopVO.StatusItem> statusItems(Map<String, long[]> src, int total) {
        List<NginxAccessTopVO.StatusItem> list = new ArrayList<>(src.size());
        src.forEach((k, v) -> list.add(new NginxAccessTopVO.StatusItem(k, v[0],
                Math.round(v[0] * 1000.0 / Math.max(1, total)) / 10.0)));
        list.sort(Comparator.comparingDouble(NginxAccessTopVO.StatusItem::pct).reversed()
                .thenComparing(NginxAccessTopVO.StatusItem::category));
        return list;
    }
}
