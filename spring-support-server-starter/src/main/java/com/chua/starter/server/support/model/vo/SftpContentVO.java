package com.chua.starter.server.support.model.vo;

/**
 * SFTP 文本内容读取结果
 *
 * @param path      远端文件路径（回显，便于前端对齐并发编辑）
 * @param content   按 UTF-8 解码的正文，超长时只含前 1MB
 * @param size      远端文件真实字节数
 * @param truncated 是否因超出读取上限而被截断
 * @author CH
 * @since 4.0.0.42
 */
public record SftpContentVO(
        String path,
        String content,
        long size,
        boolean truncated
) {
}
