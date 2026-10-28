package com.chua.starter.datasourcehub.support.model;

/**
 * 驱动 jar 视图。
 *
 * <p>JDBC 驱动与中间件客户端共用同一份台账，靠 {@link #kind} 区分；
 * 前端据此把「传上来的这个 jar 能干什么」显示清楚，而不是只看到一个文件名。</p>
 *
 * @param id              驱动 jar ID
 * @param jarName         上传时的原始文件名
 * @param storedName      落盘文件名
 * @param driverClassName 入口类全名（JDBC 驱动类 / 中间件 SPI 实现类）
 * @param jarSize         字节数
 * @param jarSha256       内容 sha256
 * @param remark          备注
 * @param createTime      上传时间
 * @param kind            jar 类别：{@code jdbc} / {@code middleware}
 * @param providerName    中间件类型名（{@code kafka} 等），仅 {@code kind=middleware} 有值
 * @author CH
 * @since 4.0.0.43
 */
public record JdbcDriverJarVO(
        Long id,
        String jarName,
        String storedName,
        String driverClassName,
        Long jarSize,
        String jarSha256,
        String remark,
        String createTime,
        String kind,
        String providerName) {

    /**
     * 类别缺省值：老数据没有 {@code jar_kind} 时一律按 JDBC 驱动处理。
     */
    public static final String KIND_JDBC = "jdbc";

    /**
     * 类别：单 jar 的中间件客户端。
     */
    public static final String KIND_MIDDLEWARE = "middleware";

    /**
     * 归一化后的类别，永不为空。
     *
     * @return {@code jdbc} 或 {@code middleware}
     */
    public String normalizedKind() {
        return kind == null || kind.isBlank() ? KIND_JDBC : kind.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 是否中间件客户端 jar。
     *
     * @return 是返回 {@code true}
     */
    public boolean isMiddleware() {
        return KIND_MIDDLEWARE.equals(normalizedKind());
    }
}
