package com.chua.starter.monitor.support.model;

/**
 * Nginx 版本与编译参数（nginx -V 解析结果）。
 *
 * @param version            版本号（从 "nginx/1.xx.x" 提取），取不到为 null
 * @param configureArguments ./configure 后的编译参数，取不到为 null
 * @param raw                nginx -V 原始输出
 * @author CH
 * @since 4.0.0.42
 */
public record NginxVersionVO(
        String version,
        String configureArguments,
        String raw
) {
}
