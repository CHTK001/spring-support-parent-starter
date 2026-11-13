package com.chua.starter.server.support.model.vo;

/**
 * 主机文件指纹。
 *
 * @param md5       小写十六进制 MD5
 * @param size      字节数
 * @param elapsedMs 计算耗时（毫秒）
 * @author CH
 * @since 4.0.0.62
 */
public record HostFileDigestVO(
        String md5,
        long size,
        long elapsedMs
) {
}
