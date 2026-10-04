package com.chua.starter.monitor.support.datasync;

import java.util.Locale;

/**
 * 数据同步任务的输入源类型。
 *
 * <p>数据面分两大族，产出统一收敛成「行 = Map&lt;列名, 字符串值&gt;」：</p>
 * <ul>
 *   <li><b>库表族</b>：{@link #JDBC} 数据源中心的 jdbc 数据源 + 读取 SQL</li>
 *   <li><b>文件族</b>：{@link #FILE} 服务器本地单文件（csv / excel）、
 *       {@link #DIR} 服务器本地目录（按匹配规则扫描 + 监听实时录入）</li>
 *   <li><b>网络族</b>：{@link #HTTP_JSON} 拉接口取 JSON 数组、
 *       {@link #HTTP_HTML} 抓网页用 jsoup 解析表格行</li>
 * </ul>
 *
 * <p>网络族与文件族共用 {@code sourcePath} 存「源定位」：文件族是本地绝对路径，
 * 网络族是请求 URL；扩展项分别放 {@link SyncSourceOptions}（文件）与
 * {@link SyncHttpOptions}（网络），由本类型决定用哪一套。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public enum SyncSourceType {

    /**
     * jdbc 数据源 + 读取 SQL
     */
    JDBC,

    /**
     * 服务器本地单个文件（csv / excel）
     */
    FILE,

    /**
     * 服务器本地目录（按匹配规则扫描 + 监听实时录入）
     */
    DIR,

    /**
     * HTTP 接口，响应为 JSON（取其中的数组作为数据行）
     */
    HTTP_JSON,

    /**
     * HTTP 网页，用 jsoup 按 CSS 选择器解析表格行
     */
    HTTP_HTML;

    /**
     * 是否网络源（需要 URL 而非本地路径）
     *
     * @return true 表示 HTTP_JSON / HTTP_HTML
     */
    public boolean isHttp() {
        return this == HTTP_JSON || this == HTTP_HTML;
    }

    /**
     * 是否本地文件源（需要绝对路径）
     *
     * @return true 表示 FILE / DIR
     */
    public boolean isFile() {
        return this == FILE || this == DIR;
    }

    /**
     * 宽松解析：空值按 {@link #JDBC} 处理（兼容存量任务没有该字段的情况）。
     *
     * @param value 原始值
     * @return 源类型
     * @throws IllegalArgumentException 值不在枚举内
     */
    public static SyncSourceType from(String value) {
        if (value == null || value.isBlank()) {
            return JDBC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的输入源类型: " + value
                    + "（可选 JDBC/FILE/DIR/HTTP_JSON/HTTP_HTML）");
        }
    }
}