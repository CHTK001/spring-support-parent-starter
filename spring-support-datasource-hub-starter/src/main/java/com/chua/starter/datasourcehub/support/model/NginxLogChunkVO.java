package com.chua.starter.datasourcehub.support.model;

/**
 * Nginx 日志增量读取结果。
 *
 * <p>前端定时刷新时带上上次读到的字节游标 {@code cursor}，服务端只返回游标之后的新内容，
 * 而不是每次重新 tail 几百行。日志文件被轮转（大小变小）时 {@code rotated} 为 true，
 * 前端据此丢弃本地累积内容重新全量拉取，避免把两个文件的内容拼在一起。</p>
 *
 * @param content  增量文本（rotated 为 true 时是全量尾部）
 * @param cursor   本次读到的字节位置，下次带上
 * @param size     日志文件当前总字节数
 * @param rotated  是否发生了日志轮转
 * @param path     实际读取的日志路径
 * @author CH
 * @since 4.0.0.42
 */
public record NginxLogChunkVO(
        String content,
        Long cursor,
        Long size,
        boolean rotated,
        String path
) {
}
