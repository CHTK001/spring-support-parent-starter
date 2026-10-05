package com.chua.starter.monitor.support.datasync;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP 输入源（{@code HTTP_JSON} / {@code HTTP_HTML}）的扩展选项。
 *
 * <p>与 {@link SyncSourceOptions}（文件族）并存，同一个 {@code source_options} 字段里存哪一套，
 * 由 {@link SyncSourceType} 决定——两者字段名不重叠，读错类型会立刻因缺字段而暴露，不会静默走偏。</p>
 *
 * <p>刻意不放「分页 / 增量游标」：一次请求一个响应，重复抓取的去重交给写入模式
 * （UPSERT / OVERWRITE）。真要翻页，在源 URL 上拼参数 + cron 驱动即可，
 * 控制台不替用户猜游标字段。</p>
 *
 * @param method         请求方法：{@code GET} / {@code POST} / {@code PUT}，默认 {@code GET}
 * @param headers        请求头（如 {@code Authorization}）
 * @param body           请求体（GET 时忽略）
 * @param encoding       响应编码，默认 {@code UTF-8}；中文站点常需 {@code GBK}
 * @param jsonPath       JSON 数据位置，点号路径，支持 {@code [i]} 下标，如 {@code data.list[0].items}；
 *                       留空表示响应根就是数组（或根对象里自动取第一个数组字段）
 * @param rowSelector    HTML 行选择器（对整篇文档 select），默认 {@code table tr}
 * @param cellSelector   HTML 单元格选择器（对每一行 select），默认 {@code td,th}
 * @param headerRow      HTML 首行是否表头，默认 true
 * @param timeoutSeconds 超时秒数，默认 30
 * @param cookie         请求 Cookie 整串（形如 {@code a=1; b=2}），默认不带。
 *                       <p>单独一个字段而不是塞进 {@code headers}：Cookie 常含分号与空格，
 *                       在「请求头键值对」表里当一行既难读又容易被前端转义坏；
 *                       而且多数站点只认 {@code Cookie} 这一个头，
 *                       让用户自己拼一个叫 {@code Cookie} 的请求头反而更容易写错。</p>
 *                       <p>{@code headers} 里若也带了 {@code Cookie}，以本字段为准，
 *                       并在日志里提示一次 —— 两个 Cookie 头会被服务端拼接成非预期值。</p>
 * @author CH
 * @since 4.0.0.43
 */
public record SyncHttpOptions(
        String method,
        Map<String, String> headers,
        String body,
        String encoding,
        String jsonPath,
        String rowSelector,
        String cellSelector,
        Boolean headerRow,
        Integer timeoutSeconds,
        String cookie) {

    /**
     * 默认请求方法
     */
    public static final String DEFAULT_METHOD = "GET";

    /**
     * 默认 HTML 行选择器
     */
    public static final String DEFAULT_ROW_SELECTOR = "table tr";

    /**
     * 默认 HTML 单元格选择器
     */
    public static final String DEFAULT_CELL_SELECTOR = "td,th";

    /**
     * 默认超时（秒）
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 全默认值。
     *
     * @return 选项
     */
    public static SyncHttpOptions defaults() {
        return new SyncHttpOptions(null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 把 null / 空串补齐成默认值。
     *
     * @return 归一化后的选项
     */
    public SyncHttpOptions normalized() {
        Map<String, String> safeHeaders = headers == null || headers.isEmpty() ? Map.of() : headers;
        return new SyncHttpOptions(
                isBlank(method) ? DEFAULT_METHOD : method.trim().toUpperCase(Locale.ROOT),
                safeHeaders,
                body,
                isBlank(encoding) ? StandardCharsets.UTF_8.name() : encoding.trim(),
                isBlank(jsonPath) ? "" : jsonPath.trim(),
                isBlank(rowSelector) ? DEFAULT_ROW_SELECTOR : rowSelector.trim(),
                isBlank(cellSelector) ? DEFAULT_CELL_SELECTOR : cellSelector.trim(),
                headerRow == null || headerRow,
                timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds,
                isBlank(cookie) ? "" : cookie.trim());
    }

    /**
     * 是否要带 Cookie。
     *
     * @return true 表示有非空 Cookie
     */
    public boolean hasCookie() {
        return cookie != null && !cookie.isBlank();
    }

    /**
     * 响应编码；配了非法编码名时回落 UTF-8，避免整个任务因一个错字不可用。
     *
     * @return 字符集
     */
    public Charset charset() {
        try {
            return Charset.forName(normalized().encoding());
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 是否有请求体。
     *
     * @return true 表示需要带 body
     */
    public boolean hasBody() {
        return body != null && !body.isBlank();
    }

    /**
     * 空串判断
     *
     * @param value 原值
     * @return true 表示 null 或空白
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}