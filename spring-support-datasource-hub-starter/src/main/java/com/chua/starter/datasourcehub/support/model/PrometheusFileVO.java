package com.chua.starter.datasourcehub.support.model;

/**
 * Prometheus 配置文件/备份文件条目。
 *
 * @param path  文件绝对路径
 * @param size  字节数，取不到时为 null
 * @param mtime 修改时间（yyyy-MM-dd HH:mm），取不到时为空串
 * @param main  是否为主配置文件（prometheus.yml）
 * @author CH
 * @since 4.0.0.42
 */
public record PrometheusFileVO(
        String path,
        Long size,
        String mtime,
        boolean main
) {
}
