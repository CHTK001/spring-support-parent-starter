package com.chua.starter.common.support.serviceinfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * 服务信息横幅渲染器 (纯布局, 不依赖 Spring 容器, 便于单独验证)。
 *
 * <p>唯一职责: 把 {@link ServiceInfoRegistry} 中的端点与开关渲染成"右边界严格对齐"的框线文本。
 * 颜色一律通过 {@link AnsiStyle} 输出, 本类不出现任何 {@code \u001B[xxm} 字面量。</p>
 *
 * <h3>布局算法 (两遍扫描)</h3>
 * <ol>
 *   <li><b>第一遍</b>: 量出每个单元格的"自然可见宽度", 得出各列的最大值与框体内容宽度 inner
 *       (自然宽度 + 右边距, 并夹在 [62, 118] 之间)。</li>
 *   <li><b>第二遍</b>: 用 inner 反推各列宽上限 (为右侧的状态/描述预留空间), 再逐行渲染。
 *       单元格超出列宽时按可见宽度截断并以 {@code ...} 收尾。</li>
 * </ol>
 *
 * <p>可见宽度定义: CJK/全角计 2 列, 其余计 1 列, ANSI 转义序列计 0 列。
 * 所有行的最终可见列宽恒为 {@code inner + 2} (含左右边框), 因此右边界永不错位。</p>
 *
 * <h3>输出示例</h3>
 * <pre>
 * ╔══════════════════════════════════════════════════════════════╗
 * ║                            服务信息                           ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ╟── 端点 ──────────────────────────────────────────────────────╢
 * ║  AI/AI接口        /ai/**                          AI接口      ║
 * ╟── 监控 ──────────────────────────────────────────────────────╢
 * ║    Spring Boot Actuator                              [开启]   ║
 * ╚══════════════════════════════════════════════════════════════╝
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
final class ServiceInfoBanner {

    /** 框体内容最小宽度 (不含左右边框) */
    private static final int MIN_INNER = 62;
    /** 框体内容最大宽度 */
    private static final int MAX_INNER = 118;
    /** 内容右侧留白 */
    private static final int RIGHT_MARGIN = 2;
    /** 列间距 */
    private static final int GAP = 2;
    /** 可伸缩间隙的最小列宽 */
    private static final int MIN_FILL = 2;
    /** 端点行缩进 */
    private static final int ENDPOINT_INDENT = 2;
    /** 开关行缩进 (比端点深一级, 形成层次) */
    private static final int FLAG_INDENT = 4;
    /** 端点描述列的最短保留宽度 */
    private static final int MIN_DESC_WIDTH = 12;

    /** 标题 */
    private static final String TITLE = "服务信息";
    /** 端点分组标题 */
    private static final String ENDPOINT_SECTION = "端点";
    /** 空分组的兜底标题 */
    private static final String DEFAULT_SECTION = "其它";
    /** 截断标记 */
    private static final String ELLIPSIS = "...";

    // ------------------------------------------------------------------ 框线字符

    private static final String H_LINE = "═"; // h线
    private static final String V_LINE = "║"; // v线
    private static final String T_LEFT = "╔"; // T_LEFT
    private static final String T_RIGHT = "╗"; // T_RIGHT
    private static final String B_LEFT = "╚"; // B_LEFT
    private static final String B_RIGHT = "╝"; // B_RIGHT
    private static final String SEC_LEFT = "╠"; // SEC_LEFT
    private static final String SEC_RIGHT = "╣"; // SEC_RIGHT
    private static final String DIV_LEFT = "╟"; // DIV_LEFT
    private static final String DIV_RIGHT = "╢"; // DIV_RIGHT
    private static final String DIV_LINE = "─"; // div线
    /** 分组分隔线前缀: "── " */
    private static final String DIV_PREFIX = DIV_LINE + DIV_LINE + " ";

    /** 创建 服务信息banner 实例 */
    private ServiceInfoBanner() {}

    /**
     * 渲染完整横幅。
     *
     * @param registry 注册表
     * @return 待输出的文本 (首行含前导换行); 无任何端点与开关时返回空串
     */
    static String render(ServiceInfoRegistry registry) {
        List<ServiceInfoRegistry.EndpointInfo> endpoints = dedupeEndpoints(registry.getEndpoints().values());
        Map<String, List<ServiceInfoRegistry.FlagInfo>> groups = dedupeFlags(registry.getFlags().values());
        if (endpoints.isEmpty() && groups.isEmpty()) {
            return "";
        }

        Metrics metrics = new Metrics(endpoints, groups);
        int inner = metrics.innerWidth();
        List<Row> rows = metrics.buildRows(inner);

        StringBuilder sb = new StringBuilder("\n");
        sb.append(frame(T_LEFT, H_LINE, T_RIGHT, inner)).append('\n');
        sb.append(V_LINE).append(center(AnsiStyle.bold(AnsiStyle.yellow(TITLE)), inner))
                .append(V_LINE).append('\n');
        sb.append(frame(SEC_LEFT, H_LINE, SEC_RIGHT, inner)).append('\n');
        for (Row row : rows) {
            sb.append(row.render(inner)).append('\n');
        }
        sb.append(frame(B_LEFT, H_LINE, B_RIGHT, inner));
        return sb.toString();
    }

    // ------------------------------------------------------------------ 去重

    /**
     * 端点去重: 同一路径只保留一条, 重复时保留 URL 更完整的那条
      * (例如 {@code /doc.html} 与 {@code http://host/doc.html} 由不同 启动 重复注册, 视为同一端点)。
     */
    private static List<ServiceInfoRegistry.EndpointInfo> dedupeEndpoints(
            Collection<ServiceInfoRegistry.EndpointInfo> source) {
        Map<String, ServiceInfoRegistry.EndpointInfo> byPath = new LinkedHashMap<>();
        for (ServiceInfoRegistry.EndpointInfo endpoint : source) {
            String path = pathOf(endpoint.getUrl());
            ServiceInfoRegistry.EndpointInfo previous = byPath.get(path);
            if (previous == null
                    || nullToEmpty(endpoint.getUrl()).length() > nullToEmpty(previous.getUrl()).length()) {
                byPath.put(path, endpoint);
            }
        }
        return new ArrayList<>(byPath.values());
    }

    /**
     * 开关去重并按分组聚合: 同一分组下"显示名去掉空白后相同且取值相同"的开关只保留第一条
      * (例如 {@code Knife4j接口文档} 与 {@code Knife4j 接口文档} 由不同 启动 重复注册)。
     *
     * <p>取值不同的同名开关不会被合并, 以便暴露配置冲突。</p>
     */
    private static Map<String, List<ServiceInfoRegistry.FlagInfo>> dedupeFlags(
            Collection<ServiceInfoRegistry.FlagInfo> source) {
        Map<String, List<ServiceInfoRegistry.FlagInfo>> byGroup = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (ServiceInfoRegistry.FlagInfo flag : source) {
            String key = nullToEmpty(flag.getGroup()) + '\u0000' + squash(flagLabel(flag)) + '\u0000' + flag.getValue();
            if (!seen.add(key)) {
                continue;
            }
            String group = hasText(flag.getGroup()) ? flag.getGroup() : DEFAULT_SECTION;
            byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(flag);
        }
        return byGroup;
    }

    /**
     * 端点显示名: 分组/名称
     *
     * @param endpoint 端点
     * @return 端点标签的结果
     */
    private static String endpointLabel(ServiceInfoRegistry.EndpointInfo endpoint) {
        String group = nullToEmpty(endpoint.getGroup());
        return group.isEmpty() ? nullToEmpty(endpoint.getName()) : group + "/" + nullToEmpty(endpoint.getName());
    }

    /**
     * 开关显示名: 优先描述, 描述为空时退化为开关名
     *
     * @param flag flag
     * @return flag标签的结果
     */
    private static String flagLabel(ServiceInfoRegistry.FlagInfo flag) {
        return hasText(flag.getDescription()) ? flag.getDescription() : nullToEmpty(flag.getName());
    }

    /**
     * 开关行右侧的"状态块": 可选的附加上下文 + 三态标签
     *
     * @param flag flag
     * @return 状态block的结果
     */
    private static String statusBlock(ServiceInfoRegistry.FlagInfo flag) {
        if (!hasText(flag.getSuffix())) {
            return AnsiStyle.cnLabel(flag.getValue());
        }
        return AnsiStyle.gray(flag.getSuffix()) + " " + AnsiStyle.cnLabel(flag.getValue());
    }

    // ------------------------------------------------------------------ 度量与布局

    /** 第一遍扫描得到的度量结果, 负责反推列宽并生成所有行 */
    private static final class Metrics {
        private final List<ServiceInfoRegistry.EndpointInfo> endpoints;
        private final Map<String, List<ServiceInfoRegistry.FlagInfo>> groups;
        private final int maxEndpointLabel;
        private final int maxUrl;
        private final int maxDesc;
        private final int maxFlagLabel;
        private final int maxStatus;

        Metrics(List<ServiceInfoRegistry.EndpointInfo> endpoints,
                Map<String, List<ServiceInfoRegistry.FlagInfo>> groups) {
            this.endpoints = endpoints;
            this.groups = groups;
            int epLabel = 0;
            int url = 0;
            int desc = 0;
            for (ServiceInfoRegistry.EndpointInfo endpoint : endpoints) {
                epLabel = Math.max(epLabel, visibleWidth(endpointLabel(endpoint)));
                url = Math.max(url, visibleWidth(nullToEmpty(endpoint.getUrl())));
                desc = Math.max(desc, visibleWidth(nullToEmpty(endpoint.getDescription())));
            }
            int flagLabel = 0;
            int status = 0;
            for (List<ServiceInfoRegistry.FlagInfo> flags : groups.values()) {
                for (ServiceInfoRegistry.FlagInfo flag : flags) {
                    flagLabel = Math.max(flagLabel, visibleWidth(flagLabel(flag)));
                    status = Math.max(status, visibleWidth(statusBlock(flag)));
                }
            }
            this.maxEndpointLabel = epLabel;
            this.maxUrl = url;
            this.maxDesc = desc;
            this.maxFlagLabel = flagLabel;
            this.maxStatus = status;
        }

        /**
         * 框体内容宽度: 所有行自然宽度的最大值 + 右边距, 夹在上下限之间
         *
         * @return 内部width的结果
         */
        int innerWidth() {
            int inner = MIN_INNER;
            if (!endpoints.isEmpty()) {
                inner = Math.max(inner, ENDPOINT_INDENT + maxEndpointLabel + GAP + maxUrl + GAP + maxDesc + RIGHT_MARGIN);
            }
            if (!groups.isEmpty()) {
                inner = Math.max(inner, FLAG_INDENT + maxFlagLabel + MIN_FILL + maxStatus + RIGHT_MARGIN);
            }
            for (String group : groups.keySet()) {
                inner = Math.max(inner, DIV_PREFIX.length() + visibleWidth(group) + 1 + RIGHT_MARGIN);
            }
            return Math.min(inner, MAX_INNER);
        }

        /**
         * 第二遍: 按 内部 反推列宽并生成行
         *
         * @param inner 内部
         * @return 构建rows的结果
         */
        List<Row> buildRows(int inner) {
            List<Row> rows = new ArrayList<>();
            if (!endpoints.isEmpty()) {
                // 端点行: 缩进 + 名称列 + 间距 + URL 列 + 间距 + 描述列
                int descReserve = Math.min(maxDesc, MIN_DESC_WIDTH);
                int budget = Math.max(0, inner - ENDPOINT_INDENT - GAP - GAP - descReserve - RIGHT_MARGIN);
                int urlWidth = Math.min(maxUrl, budget);
                int labelWidth = Math.min(maxEndpointLabel, Math.max(0, budget - urlWidth));
                urlWidth = Math.min(maxUrl, Math.max(0, budget - labelWidth));
                rows.add(new DividerRow(ENDPOINT_SECTION));
                for (ServiceInfoRegistry.EndpointInfo endpoint : endpoints) {
                    rows.add(new TextRow(new Line()
                            .add(" ".repeat(ENDPOINT_INDENT))
                            .addPadded(endpointLabel(endpoint), labelWidth)
                            .add(" ".repeat(GAP))
                            .addPadded(AnsiStyle.cyan(nullToEmpty(endpoint.getUrl())), urlWidth)
                            .add(" ".repeat(GAP))
                            .add(AnsiStyle.gray(nullToEmpty(endpoint.getDescription())))));
                }
            }
            if (!groups.isEmpty()) {
                // 开关行: 缩进 + 名称列 + 可伸缩间隙 + 状态块 (右对齐)
                int labelWidth = Math.min(maxFlagLabel,
                        Math.max(0, inner - FLAG_INDENT - MIN_FILL - maxStatus - RIGHT_MARGIN));
                for (Map.Entry<String, List<ServiceInfoRegistry.FlagInfo>> entry : groups.entrySet()) {
                    rows.add(new DividerRow(entry.getKey()));
                    for (ServiceInfoRegistry.FlagInfo flag : entry.getValue()) {
                        rows.add(new TextRow(new Line()
                                .add(" ".repeat(FLAG_INDENT))
                                .addPadded(flagLabel(flag), labelWidth)
                                .fill()
                                .add(statusBlock(flag))));
                    }
                }
            }
            return rows;
        }
    }

    // ------------------------------------------------------------------ 行模型

    /** 横幅中的一行 (渲染结果已包含左右边框) */
    private interface Row {
        /**
         * 按指定内容宽度渲染整行, 可见宽度恒为 内部 + 2
         *
         * @param inner 内部
         * @return render的结果
         */
        String render(int inner);
    }

    /** 文本行: ║ 内容 ... ║ */
    private static final class TextRow implements Row {
        private final Line line;

        /**
         * 文本row。
         * @param line 线
         */
        TextRow(Line line) {
            this.line = line;
        }

        @Override
        public String render(int inner) {
 // 可伸缩间隙最多撑到 内部 - RIGHT_MARGIN, 右侧始终保留留白, 不贴边框
            String body = clip(line.render(inner - RIGHT_MARGIN, inner), inner);
            return color(AnsiStyle.CYAN) + V_LINE + reset() + body + color(AnsiStyle.CYAN) + V_LINE + reset();
        }
    }

    /** 分组分隔行: ╟── 分组名 ──────╢ */
    private static final class DividerRow implements Row {
        private final String label;

        /**
          * dividerrow。
         * @param label 标签
         */
        DividerRow(String label) {
            this.label = label;
        }

        @Override
        public String render(int inner) {
            int fill = Math.max(1, inner - DIV_PREFIX.length() - visibleWidth(label) - 1);
            return color(AnsiStyle.CYAN) + DIV_LEFT + DIV_PREFIX + reset()
                    + AnsiStyle.bold(AnsiStyle.cyan(label))
                    + color(AnsiStyle.CYAN) + " " + DIV_LINE.repeat(fill) + DIV_RIGHT + reset();
        }
    }

    /**
     * 一行内容: 由若干文本片段与"可伸缩间隙"组成, 渲染时按目标宽度展开间隙。
     */
    private static final class Line {
        /** 可伸缩间隙标记 */
        private static final Object FILL = new Object();

        private final List<Object> segments = new ArrayList<>();
        private int width;
        private int fills;

        /**
         * 追加文本片段 (自动累加可见宽度)
         *
         * @param text 文本
         * @return 添加的结果
         */
        Line add(String text) {
            if (text == null || text.isEmpty()) {
                return this;
            }
            segments.add(text);
            width += visibleWidth(text);
            return this;
        }

        /**
         * 追加文本并按列宽对齐: 不足补空格, 超出按可见宽度截断
         *
         * @param text 文本
         * @param columnWidth columnwidth
         * @return 添加padded的结果
         */
        Line addPadded(String text, int columnWidth) {
            int textWidth = visibleWidth(text);
            if (textWidth > columnWidth) {
                /**
                 * 添加。
                 * @param columnWidth columnwidth
                 */
                add(clip(text, columnWidth));
                return this;
            }
            /**
             * 添加。
             * @param text 文本
             */
            add(text);
            if (textWidth < columnWidth) {
                /**
                 * 添加。
                 * @param textWidth 文本width
                 */
                add(" ".repeat(columnWidth - textWidth));
            }
            return this;
        }

        /**
         * 追加一个可伸缩间隙 (渲染时独占剩余空间)
         *
         * @return fill的结果
         */
        Line fill() {
            segments.add(FILL);
            fills++;
            return this;
        }

        /**
         * 渲染文本。
         *
         * @param fillTarget 可伸缩间隙的展开目标列宽 (内容区宽度)
         * @param total      行内容总列宽 (含右侧留白)
         * @return render的结果
         */
        String render(int fillTarget, int total) {
            int each = 0;
            int extra = 0;
            int fillTotal = 0;
            if (fills > 0) {
                fillTotal = Math.max(MIN_FILL * fills, fillTarget - width);
                each = fillTotal / fills;
                extra = fillTotal % fills;
            }
            StringBuilder sb = new StringBuilder();
            int used = 0;
            for (Object segment : segments) {
                if (segment == FILL) {
                    sb.append(" ".repeat(each + (used++ < extra ? 1 : 0)));
                } else {
                    sb.append((String) segment);
                }
            }
            int pad = total - (width + fillTotal);
            if (pad > 0) {
                sb.append(" ".repeat(pad));
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ 工具方法

    /**
     * 生成一条框线
     *
     * @param left left
     * @param line 线
     * @param right right
     * @param inner 内部
     * @return 帧的结果
     */
    private static String frame(String left, String line, String right, int inner) {
        return color(AnsiStyle.CYAN) + left + line.repeat(inner) + right + reset();
    }

    /**
     * 居中 (按可见宽度)
     *
     * @param text 文本
     * @param inner 内部
     * @return center的结果
     */
    private static String center(String text, int inner) {
        int pad = inner - visibleWidth(text);
        if (pad <= 0) {
            return text;
        }
        int left = pad / 2;
        return " ".repeat(left) + text + " ".repeat(pad - left);
    }

    /**
     * 按可见宽度截断并以 {@code ...} 收尾 (ANSI 序列原样保留)
     *
     * @param text 文本
     * @param maxVisible 最大visible
     * @return clip的结果
     */
    private static String clip(String text, int maxVisible) {
        if (visibleWidth(text) <= maxVisible) {
            return text;
        }
        if (maxVisible <= ELLIPSIS.length()) {
            return ELLIPSIS.substring(0, Math.max(0, maxVisible));
        }
        int budget = maxVisible - ELLIPSIS.length();
        StringBuilder sb = new StringBuilder();
        int used = 0;
        int i = 0;
        while (i < text.length()) {
            if (isAnsiStart(text, i)) {
                int next = skipAnsi(text, i);
                sb.append(text, i, next);
                i = next;
                continue;
            }
            int codePoint = text.codePointAt(i);
            int charWidth = isWide(codePoint) ? 2 : 1;
            if (used + charWidth > budget) {
                break;
            }
            sb.appendCodePoint(codePoint);
            used += charWidth;
            i += Character.charCount(codePoint);
        }
        return sb.toString() + reset() + ELLIPSIS;
    }

    /**
     * 终端可见列宽: ANSI 转义序列计 0, 全角字符计 2, 其余计 1
     *
     * @param text 文本
     * @return visibleWidth的结果
     */
    private static int visibleWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        int i = 0;
        while (i < text.length()) {
            if (isAnsiStart(text, i)) {
                i = skipAnsi(text, i);
                continue;
            }
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            width += isWide(codePoint) ? 2 : 1;
        }
        return width;
    }

    /**
     * 是否 ANSI 转义序列起始 ({@code ESC[})
     *
     * @param text 文本
     * @param index 索引
     * @return 是否ansi启动的结果
     */
    private static boolean isAnsiStart(String text, int index) {
        return text.charAt(index) == '\u001B' && index + 1 < text.length() && text.charAt(index + 1) == '[';
    }

    /**
     * 跳过一条 ANSI 转义序列, 返回结束位置 (兼容 {@code ESC[1;36m} 这类多参数序列)
     *
     * @param text 文本
     * @param index 索引
     * @return 跳过ansi的结果
     */
    private static int skipAnsi(String text, int index) {
        int i = index + 2;
        while (i < text.length() && !Character.isLetter(text.charAt(i))) {
            i++;
        }
        return Math.min(i + 1, text.length());
    }

    /**
     * 是否输出 ANSI 颜色码 (遵循 {@link AnsiStyle#isEnabled()})
     *
     * @param code 编码
     * @return color的结果
     */
    private static String color(String code) {
        return AnsiStyle.isEnabled() ? code : "";
    }

    /**
     * 颜色复位码 (遵循 {@link AnsiStyle#isEnabled()})
     *
     * @return reset的结果
     */
    private static String reset() {
        return AnsiStyle.isEnabled() ? AnsiStyle.RESET : "";
    }

    /**
     * 是否为全角 (终端占 2 列) 码点
     *
     * @param codePoint 编码point
     * @return 是否wide的结果
     */
    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }

    /**
     * 去掉全部空白字符, 用于去重比对
     *
     * @param text 文本
     * @return squash的结果
     */
    private static String squash(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /**
     * 提取 URL 的路径部分 (去掉协议/主机/查询串), 用于端点去重
     *
     * @param url url
     * @return 路径的的结果
     */
    private static String pathOf(String url) {
        String path = nullToEmpty(url).trim();
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int slash = path.indexOf('/', scheme + 3);
            path = slash >= 0 ? path.substring(slash) : "/";
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path;
    }

    /**
     * 是否非空文本
     *
     * @param text 文本
     * @return 是否包含文本的结果
     */
    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * 空转为空
     *
     * @param text 文本
     * @return 空转为空的结果
     */
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
