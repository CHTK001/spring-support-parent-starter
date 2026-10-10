package com.chua.starter.datasourcehub.support.nginx;

import com.chua.starter.datasourcehub.support.model.NginxAccessTopVO;
import com.chua.starter.datasourcehub.support.model.NginxUrlTrendVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
     *
     * <p><strong>末尾的 {@code body_bytes_sent} 是可选组</strong>（用 {@code ([\d-]+)?} 加 {@code \b} 收尾），
     * 因为它是算「出站带宽」的唯一来源，但很多站点自定义过 log_format 并不带这一列。
     * 写成必需组会让整行匹配失败——那种站点的统计会从「带宽为空」退化成「什么都不统计」，
     * 代价太大。所以：带就统计，不带就留空，不影响其余指标。</p>
     */
    private static final Pattern COMBINED = Pattern.compile(
            "^(\\S+)\\s+\\S+\\s+\\S+\\s+\\[([^\\]]+)\\]\\s+\"([^\"]*)\"\\s+(\\d{3})\\b(?:\\s+([\\d-]+))?");

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
        // 出站带宽与错误计数。bytes 记 -1 表示「本行没有 body_bytes_sent 列」，
        // 不能当成 0 累加，否则会把「未知」算成「零流量」——那会让带宽显示一个假的低值。
        long bytesSent = 0;
        int bytesMissing = 0;
        int clientErrors = 0;
        int serverErrors = 0;
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
            // 二进制噪声整行丢弃：公网 80 端口常被扫描器用 TLS ClientHello / HTTP/2 PRI
            // 前言乱打，access log 里会留下 "PRI"、"\x16\x03\x01..."、"\x00"连串之类的
            // $request —— 这些行的 IP 与状态码同样是垃圾字节，只挡路径会把垃圾漏进
            // IP Top 与状态分布。计数、带宽、状态、IP、路径一个都不收（2026-10-10 反馈）。
            if (isBinaryNoise(request)) {
                continue;
            }
            int status = Integer.parseInt(m.group(4));
            if (since != null && !withinSince(time, since)) {
                continue;
            }
            parsed++;
            String rawBytes = m.group(5);
            if (rawBytes == null || rawBytes.equals("-")) {
                bytesMissing++;
            } else {
                bytesSent += parseLongQuietly(rawBytes);
            }
            if (status >= 500) {
                serverErrors++;
            } else if (status >= 400) {
                clientErrors++;
            }
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
        String trafficNote;
        Long bytesOut;
        if (bytesMissing == parsed && parsed > 0) {
            // 全部行都没有该列：给 null 而不是 0，界面才能显示「无法统计」而不是「零流量」
            bytesOut = null;
            trafficNote = " 出站带宽无法统计：所有行的 access log 都没有 $body_bytes_sent 列（自定义过 log_format）。";
        } else {
            bytesOut = bytesSent;
            trafficNote = bytesMissing > 0
                    ? " 其中 " + bytesMissing + " 行缺少 $body_bytes_sent（记为未知，未按 0 计入）。"
                    : "";
        }
        return new NginxAccessTopVO(
                parsed,
                lines.length,
                topItems(paths, topN),
                topItems(ips, topN),
                statusItems(statuses, parsed),
                note + trafficNote,
                bytesOut,
                // 全量 IP 数，不是 topItems 截断后的长度
                ips.size(),
                clientErrors,
                serverErrors);
    }

    /**
     * 宽松解析长整型，失败返回 0。
     *
     * <p>日志里的字节数理论上都是数字，但被人改过的日志可能混进异常值；
     * 单个坏值不该让整份统计抛异常——宁可少算这一行，也不要整个面板空白。</p>
     *
     * @param text 待解析文本
     * @return 解析结果，失败为 0
     */
    private static long parseLongQuietly(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 按 URL 维度统计时间趋势。
     *
     * <p><strong>时间跨度由日志密度决定</strong>，不是固定窗口：扫的是「最近 N 行」，
     * 高流量站这 N 行可能只覆盖几分钟。所以这里必须算出真实的起止时间交给前端标注，
     * 否则人会把一条 3 分钟的曲线当成 3 小时来看。</p>
     *
     * <p>桶宽自适应：先按目标桶数（约 60 个）估算需要的桶宽，再取整到「秒 / 分钟 / 小时」，
     * 这样低流量（日志稀疏）与高流量（日志密集）都能得到可读的曲线点数。
     * 桶数被 {@link #MAX_URL_BUCKETS} 硬限，避免极端情况下前端一次画几万个点。</p>
     *
     * @param text    日志文本
     * @param topN    取前几个 URL
     * @param logName 日志文件名（写进结果里便于界面标注）
     * @return 趋势结果
     */
    public static NginxUrlTrendVO urlTrend(String text, int topN, String logName) {
        if (text == null || text.isBlank()) {
            return new NginxUrlTrendVO(logName, 0, 0, 0L, 0L, null, null, 60, "日志为空，没有可统计的访问记录。", List.of());
        }
        String[] lines = text.split("\\R");
        int cap = topN <= 0 ? 5 : Math.min(topN, 10);
        // 第一遍只求时间范围：桶宽要根据真实跨度来定，而跨度必须先把有合法时间戳的行都看过才知道。
        long minEpoch = Long.MAX_VALUE;
        long maxEpoch = Long.MIN_VALUE;
        int parsed = 0;
        int timeUnparsed = 0;
        // 第一遍：求时间范围与有效行数。桶宽要按真实跨度自适应，
        // 而跨度必须先把所有合法时间戳的行看过才知道，所以只能先扫一遍不落桶。
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            Matcher m = COMBINED.matcher(line);
            if (!m.find()) {
                continue;
            }
            long epoch = parseEpoch(m.group(2));
            if (epoch <= 0) {
                // 时间戳坏掉时不能计入趋势：桶的定位全靠它，算成 0 会被塞进第一个桶，
                // 造成一条假的尖峰。仍计入 parsed（那一行格式是对的），只是不进趋势。
                timeUnparsed++;
                continue;
            }
            if (normalizePath(m.group(3)) == null) {
                continue;
            }
            parsed++;
            minEpoch = Math.min(minEpoch, epoch);
            maxEpoch = Math.max(maxEpoch, epoch);
        }
        if (parsed == 0) {
            return new NginxUrlTrendVO(logName, 0, lines.length, 0L, 0L, null, null, 60,
                    "扫描了 " + lines.length + " 行，但没有一行能解析出 URL 与时间戳，无法做趋势。", List.of());
        }
        long spanSeconds = Math.max(1, maxEpoch - minEpoch);
        int bucketSeconds = pickBucketSeconds(spanSeconds, parsed);
        int buckets = (int) Math.min(MAX_URL_BUCKETS, Math.max(1, spanSeconds / bucketSeconds + 1));
        // 第二遍才真正分桶
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            Matcher m = COMBINED.matcher(line);
            if (!m.find()) {
                continue;
            }
            long epoch = parseEpoch(m.group(2));
            if (epoch <= 0) {
                continue;
            }
            String path = normalizePath(m.group(3));
            if (path == null) {
                continue;
            }
            int idx = (int) Math.min(buckets - 1L, Math.max(0L, (epoch - minEpoch) / bucketSeconds));
            counts.computeIfAbsent(path, k -> new long[buckets])[idx]++;
        }
        List<Entry<String, long[]>> top = new ArrayList<>(counts.entrySet());
        top.sort(Comparator.comparingLong((Entry<String, long[]> e) -> sum(e.getValue())).reversed());
        List<NginxUrlTrendVO.UrlSeries> series = new ArrayList<>();
        for (int i = 0; i < top.size() && i < cap; i++) {
            Entry<String, long[]> en = top.get(i);
            List<Long> bs = new ArrayList<>(en.getValue().length);
            for (long v : en.getValue()) {
                bs.add(v);
            }
            series.add(new NginxUrlTrendVO.UrlSeries(en.getKey(), sum(en.getValue()), bs));
        }
        String msg = "覆盖 " + fmtSpan(spanSeconds) + "（" + fmtTime(minEpoch) + " → " + fmtTime(maxEpoch) + "），"
                + "桶宽 " + fmtBucket(bucketSeconds) + "，共 " + buckets + " 个点。"
                + "这是「最近 " + lines.length + " 行」的实际跨度，不是固定时间窗——高流量时它只覆盖很短一段时间。";
        if (timeUnparsed > 0) {
            msg += " 另有 " + timeUnparsed + " 行时间戳无法解析，未计入趋势。";
        }
        return new NginxUrlTrendVO(logName, parsed, lines.length, minEpoch, maxEpoch,
                fmtIso(minEpoch), fmtIso(maxEpoch), bucketSeconds, msg, series);
    }

    /**
     * 趋势里 URL 序列的最大条数
     */
    private static final int MAX_URL_BUCKETS = 240;

    /**
     * 目标桶数：够画出形状又不至于太稀疏
     */
    private static final int URL_TREND_TARGET_BUCKETS = 60;

    /**
     * 求和（long[] 求值）
     *
     * @param values 数组
     * @return 总和
     */
    private static long sum(long[] values) {
        long s = 0;
        for (long v : values) {
            s += v;
        }
        return s;
    }

    /**
     * 带时区的 {@code $time_local} 格式化器。
     *
     * <p>提成常量而不是在 {@link #parseEpoch} 里现造：{@code ofPattern} 要编译正则，
     * 按每行调用一次算，5000 行日志要跑 3 遍就是上万次编译。</p>
     */
    private static final DateTimeFormatter TS_OFFSET =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", java.util.Locale.ENGLISH);

    /**
     * 把 nginx 的 {@code $time_local} 解析成 epoch 秒。
     *
     * <p>解析失败返回 0，由调用方决定怎么处理（趋势里是跳过并计数，不参与分桶）。</p>
     *
     * <p><strong>不要对年份做任何「补 20」的前缀处理</strong>：{@code $time_local}
     * 本来就输出 4 位年份（{@code 28/Sep/2026:...}），而 {@code $1} 里已经含了斜杠，
     * 拼出来是 {@code 2028//Sep/...}，双斜杠会让每一行都解析失败——
     * 结果是整条趋势恒为空，而接口照样返回 200 成功信封。</p>
     *
     * @param rawTime 原始时间串，形如 {@code 28/Sep/2026:07:30:20 -0400}
     * @return epoch 秒；失败为 0
     */
    private static long parseEpoch(String rawTime) {
        try {
            return java.time.OffsetDateTime.parse(rawTime.trim(), TS_OFFSET).toEpochSecond();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /**
     * 按目标桶数挑一个整齐的桶宽。
     *
     * <p><strong>目标桶数要同时受「跨度」和「样本行数」约束</strong>。
     * 只按跨度算会挑出荒谬的桶宽：100 秒跨度配 60 目标桶数，
     * 理想桶宽是 1.67 秒，往上取整到档位表就变成 1 秒，于是 11 条记录被摊成
     * 101 个桶，其中 90 个必然是 0——图上是一条贴底的锯齿，覆盖区间也读成「1 秒」。
     * 而 N 个桶至少要 N 个不同时刻才填得满，所以目标桶数还得压到样本行数以内。
     * 档位表在 1~60 之间也要够密（原来 1 之后直接跳到 5，会把 2~4 秒整档丢掉）。</p>
     *
     * @param spanSeconds 实际覆盖跨度（秒）
     * @param parsed      有效记录行数
     * @return 桶宽（秒）
     */
    private static int pickBucketSeconds(long spanSeconds, int parsed) {
        int target = Math.min(URL_TREND_TARGET_BUCKETS, Math.max(1, parsed));
        long ideal = Math.max(1L, (long) Math.ceil((double) spanSeconds / target));
        int[] steps = {1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 21600, 86400};
        for (int s : steps) {
            if (s >= ideal) {
                return s;
            }
        }
        return steps[steps.length - 1];
    }

    /**
     * 把跨度描述成人类可读的文本
     *
     * @param seconds 秒
     * @return 形如「约 15 分钟」
     */
    private static String fmtSpan(long seconds) {
        if (seconds < 90) {
            return seconds + " 秒";
        }
        if (seconds < 5400) {
            return (seconds / 60) + " 分钟";
        }
        if (seconds < 172800) {
            return (seconds / 3600) + " 小时";
        }
        return (seconds / 86400) + " 天";
    }

    /**
     * 桶宽的可读描述
     *
     * @param seconds 秒
     * @return 形如「1 分钟」
     */
    private static String fmtBucket(int seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        }
        return (seconds / 3600) + " 小时";
    }

    /**
     * epoch 秒转本地可读时间
     *
     * @param epoch epoch 秒
     * @return HH:mm:ss
     */
    private static String fmtTime(long epoch) {
        return java.time.Instant.ofEpochSecond(epoch)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime().toString();
    }

    /**
     * epoch 秒转 ISO-8601 本地时间
     *
     * @param epoch epoch 秒
     * @return ISO-8601
     */
    private static String fmtIso(long epoch) {
        return java.time.Instant.ofEpochSecond(epoch)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime().toString();
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
     * 从 request 里提取请求目标 token。
     *
     * <p>{@code $request} 的标准形态是 {@code METHOD PATH PROTOCOL}（如 {@code GET /a/b HTTP/1.1}），
     * 目标是**第二个** token；也有站点的 log_format 只记路径，此时第一个 token 就是目标。
     * 早先直接取第一个 token 会把所有条目都统计成 {@code GET}。</p>
     *
     * @param request 请求行，如 {@code GET /a/b HTTP/1.1}
     * @return 目标 token；request 为空时返回空串
     */
    private static String extractTarget(String request) {
        String r = request == null ? "" : request.trim();
        if (r.isEmpty()) {
            return "";
        }
        String[] parts = r.split("\\s+");
        if (parts.length >= 2 && HTTP_METHODS.contains(parts[0].toUpperCase(java.util.Locale.ROOT))) {
            return parts[1];
        }
        return parts[0];
    }

    /**
     * 判断 {@code $request} 是不是「二进制噪声」——扫描器探测留下的垃圾负载。
     *
     * <p>判据两条，满足其一即噪声：</p>
     * <ul>
     *   <li>目标不是 origin-form：真实访问行的目标一定以 {@code /} 开头。
     *       {@code PRI}（HTTP/2 前言）、裸协议 {@code HTTP/1.1}、随机 token 都不以 {@code /} 开头；</li>
     *   <li>目标含控制字符（{@code 0x00-0x1F} / {@code 7F}）：TLS ClientHello、
     *       {@code \x00} 连串这类键一旦进榜，Top 榜就是一屏乱码（2026-10-10 用户反馈）。</li>
     * </ul>
     *
     * <p>request 本身为空（有些站点的 log_format 不记 request）**不算噪声**：
     * 那类行的 IP / 状态 / 带宽仍然有效，只是没有路径可归类。</p>
     *
     * @param request {@code $request} 原文
     * @return true 表示整行是噪声，不计入任何聚合
     */
    private static boolean isBinaryNoise(String request) {
        String target = extractTarget(request);
        return !target.isEmpty() && (!target.startsWith("/") || hasControlChars(target));
    }

    /**
     * 文本是否含控制字符（{@code 0x00-0x1F} 与 {@code 0x7F}）。
     *
     * @param text 待检查文本
     * @return 含有控制字符返回 true
     */
    private static boolean hasControlChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= 0x1F || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 request 里取路径并归一化。
     *
     * <p>只认 origin-form：目标必须以 {@code /} 开头且不含控制字符——
     * 裸协议行（{@code HTTP/1.1}）、绝对形式（{@code http://...}）与二进制噪声
     * （TLS / HTTP2 探测残留）都在这里返回 null，{@code urlTrend} 直接受益。</p>
     *
     * @param request 请求行，如 {@code GET /a/b/12 HTTP/1.1}
     * @return 归一化后的路径；无法解析时返回 null
     */
    private static String normalizePath(String request) {
        String target = extractTarget(request);
        if (target.isEmpty()) {
            return null;
        }
        if (!target.startsWith("/") || hasControlChars(target)) {
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
