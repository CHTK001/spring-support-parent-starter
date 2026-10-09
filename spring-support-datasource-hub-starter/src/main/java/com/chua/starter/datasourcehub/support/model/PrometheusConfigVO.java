package com.chua.starter.datasourcehub.support.model;

/**
 * Prometheus 配置文件内容。
 *
 * @param path    文件绝对路径
 * @param content 文件内容
 * @param exists  文件是否存在
 * @author CH
 * @since 4.0.0.42
 */
public record PrometheusConfigVO(
        String path,
        String content,
        boolean exists
) {
}
