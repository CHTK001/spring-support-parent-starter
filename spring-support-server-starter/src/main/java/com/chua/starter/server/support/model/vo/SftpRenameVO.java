package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SFTP 重命名/移动请求
 *
 * @param fromPath 源路径
 * @param toPath   目标路径
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SftpRenameVO(
        String fromPath,
        String toPath
) {
}
