package com.chua.starter.monitor.support.middleware;

import com.chua.starter.datasourcehub.support.model.DataSourceVO;

/**
 * 中间件访问端点：把落库的数据源行规整成「基地址 + 凭据」三元组。
 *
 * <p>Nacos/InfluxDB/Neo4j 这类中间件都通过 HTTP OpenAPI 管理，数据源表里的
 * {@code host}/{@code port} 给出地址，{@code database} 字段留空时用各类型的默认上下文路径；
 * 若用户直接填写完整 URL（如反向代理挂在子路径下），则原样使用，端口字段被忽略。</p>
 *
 * @param base     协议基地址，形如 {@code http://127.0.0.1:8848}，不含上下文路径
 * @param context  上下文路径，以 {@code /} 开头且不含结尾斜杠，可为空串
 * @param username 用户名，空表示中间件未开启鉴权
 * @param password 密码
 * @author CH
 * @since 4.0.0.42
 */
public record MiddlewareEndpoint(String base, String context, String username, String password) {

    /**
     * 由数据源行解析端点。
     *
     * @param ds             数据源配置
     * @param defaultContext 未填写库名字段时使用的默认上下文路径，如 {@code /nacos}
     * @return 端点
     */
    public static MiddlewareEndpoint of(DataSourceVO ds, String defaultContext) {
        String database = trim(ds.database());
        if (isAbsoluteUrl(database)) {
            java.net.URI uri = java.net.URI.create(database);
            String base = uri.getScheme() + "://" + uri.getAuthority();
            String path = trim(uri.getPath());
            return new MiddlewareEndpoint(base, path.isEmpty() ? "" : path,
                    trim(ds.username()), ds.password() == null ? "" : ds.password());
        }
        String scheme = "https".equalsIgnoreCase(database) ? "https" : "http";
        int port = ds.port() == null ? 0 : ds.port();
        String base = scheme + "://" + trim(ds.host()) + (port > 0 ? ":" + port : "");
        String context = database.isEmpty()
                ? normalizeContext(defaultContext)
                : normalizeContext(database);
        return new MiddlewareEndpoint(base, context, trim(ds.username()),
                ds.password() == null ? "" : ds.password());
    }

    /**
     * 拼接完整请求地址。
     *
     * @param path OpenAPI 路径，以 {@code /} 开头
     * @return 可直接请求的地址
     */
    public String url(String path) {
        return base + context + path;
    }

    /**
     * 是否开启了鉴权。
     *
     * @return 用户名非空即视为开启
     */
    public boolean authed() {
        return !username.isEmpty();
    }

    /**
     * 判断填写值是否已经是完整 URL。
     *
     * @param value 库名字段原值
     * @return 以 http(s):// 开头为真
     */
    private static boolean isAbsoluteUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    /**
     * 规整上下文路径：补齐前导斜杠、去掉结尾斜杠。
     *
     * @param context 原始上下文
     * @return 规整结果，空值返回空串
     */
    private static String normalizeContext(String context) {
        String v = trim(context);
        if (v.isEmpty() || "/".equals(v)) {
            return "";
        }
        if (!v.startsWith("/")) {
            v = "/" + v;
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    /**
     * 空值安全的去空白。
     *
     * @param value 原值
     * @return 去空白结果，null 转空串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
