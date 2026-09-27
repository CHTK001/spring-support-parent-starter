package com.chua.starter.monitor.support.model;

import java.io.InputStream;

/**
 * 文件资产下载内容
 *
 * <p>持有底层存储的输出流，由调用方（控制层）负责关闭。</p>
 *
 * @param fileName      文件名（路径最后一段）
 * @param contentType   内容类型，无法识别时为 application/octet-stream
 * @param contentLength 字节数，存储未返回元数据时为 -1
 * @param content       内容流
 * @author CH
 * @since 4.0.0.42
 */
public record FileAssetContentVO(
        String fileName,
        String contentType,
        long contentLength,
        InputStream content
) {
}
