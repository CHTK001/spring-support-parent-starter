package com.chua.starter.datasourcehub.support.model;

/**
 * Nginx 日志文件条目。
 *
 * @param path     日志文件绝对路径
 * @param type     归类：access / error / other
 * @param size     文件大小（字节），取不到为 null
 * @param mtime    最后修改时间（可读文本），取不到为 null
 * @author CH
 * @since 4.0.0.42
 */
public record NginxLogFileVO(
        String path,
        String type,
        Long size,
        String mtime
) {
}
