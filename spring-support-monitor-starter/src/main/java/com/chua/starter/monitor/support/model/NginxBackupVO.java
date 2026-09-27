package com.chua.starter.monitor.support.model;

import java.util.List;

/**
 * Nginx 配置备份条目。
 *
 * @param name     备份文件名
 * @param path     备份文件绝对路径
 * @param size     字节数
 * @param mtime    保存时间（可读文本）
 * @param sourceSize 备份来源配置文件的字节数
 * @param reason   备份原因
 * @author CH
 * @since 4.0.0.42
 */
public record NginxBackupVO(
        String name,
        String path,
        Long size,
        String mtime,
        Long sourceSize,
        String reason
) {
}
