package com.chua.starter.ai.support.workspace.terminal;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 终端输出日志写入器（每个聊天会话一个文件，追加写入 + 满额截尾）。
 *
 * <p>终端输出是原始终端字节流，直接追加到磁盘日志；文件超过 {@link #MAX_LOG_BYTES} 时
 * 只保留最近 {@link #KEEP_LOG_BYTES}，并且截断点会对齐到 UTF-8 字符边界与换行，
 * 避免把多字节中文截成半个字符、把 ANSI 转义序列截断导致回放时终端串色。</p>
 *
 * <p>磁盘不可写时降级为「仅内存」：{@code failed} 置位后不再重复尝试，日志只打一次，
 * 不影响终端本身继续使用。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
final class TerminalLogWriter implements Closeable {

    /**
     * 日志文件上限（字节），超过即截尾
     */
    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;

    /**
     * 截尾后保留的尾部字节数
     */
    private static final long KEEP_LOG_BYTES = 1024L * 1024L;

    /**
     * UTF-8 续字节掩码
     */
    private static final int UTF8_CONTINUATION_MASK = 0xC0;

    /**
     * UTF-8 续字节标志
     */
    private static final int UTF8_CONTINUATION_FLAG = 0x80;

    /**
     * 换行字节
     */
    private static final byte LINE_BREAK = '\n';

    /**
     * 日志文件路径
     */
    private final Path path;

    /**
     * 追加写入流
     */
    private OutputStream outputStream;

    /**
     * 当前文件字节数（文件真实大小，不是本次进程写入量）
     */
    private long bytesWritten;

    /**
     * 是否已写入失败（失败后不再重试，降级为仅内存历史）
     */
    private boolean failed;

    /**
     * 构造写入器。
     *
     * @param path 日志文件路径
     */
    private TerminalLogWriter(Path path) {
        this.path = path;
    }

    /**
     * 打开日志写入器。
     *
     * <p>文件已超上限时先截尾，再以追加方式打开，保证进程重启后不会把上一轮的
     * 超量内容继续堆下去。</p>
     *
     * @param path 日志文件路径
     * @return 日志写入器
     * @throws IOException 创建目录或打开文件失败
     */
    static TerminalLogWriter open(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        TerminalLogWriter writer = new TerminalLogWriter(path);
        long size = Files.exists(path) ? Files.size(path) : 0L;
        if (size > MAX_LOG_BYTES) {
            writer.truncate();
            size = writer.bytesWritten;
        }
        writer.outputStream = Files.newOutputStream(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        writer.bytesWritten = size;
        return writer;
    }

    /**
     * 追加一段终端输出。
     *
     * @param text 输出片段
     * @return 实际写入的字节数，写入失败返回 0
     */
    synchronized long append(String text) {
        if (failed || outputStream == null || text == null || text.isEmpty()) {
            return 0L;
        }
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            outputStream.write(bytes);
            outputStream.flush();
            bytesWritten += bytes.length;
            if (bytesWritten > MAX_LOG_BYTES) {
                rotate();
            }
            return bytes.length;
        } catch (IOException e) {
            failed = true;
            log.warn("[terminal] 终端输出日志写入失败，已降级为仅内存历史 path={} error={}",
                    path, TerminalEvents.messageOf(e));
            closeQuietly();
            return 0L;
        }
    }

    /**
     * 当前日志文件字节数。
     *
     * @return 文件字节数
     */
    synchronized long bytesWritten() {
        return bytesWritten;
    }

    /**
     * 截尾并重新打开写入流。
     *
     * @throws IOException 截尾或重开失败
     */
    private void rotate() throws IOException {
        closeQuietly();
        truncate();
        outputStream = Files.newOutputStream(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /**
     * 只保留文件尾部的有效内容。
     *
     * <p>截断点先对齐 UTF-8 字符首字节，再向后跳到下一个换行（存在时），
     * 保证回放时不会出现半个汉字或半条 ANSI 序列。</p>
     *
     * @throws IOException 读写文件失败
     */
    private void truncate() throws IOException {
        if (!Files.exists(path)) {
            bytesWritten = 0L;
            return;
        }
        byte[] content = Files.readAllBytes(path);
        int start = (int) Math.max(0L, content.length - KEEP_LOG_BYTES);
        while (start < content.length && (content[start] & UTF8_CONTINUATION_MASK) == UTF8_CONTINUATION_FLAG) {
            start++;
        }
        int lineStart = start;
        while (lineStart < content.length && content[lineStart] != LINE_BREAK) {
            lineStart++;
        }
        // 存在换行才跳行；整段没有换行时保留对齐后的完整内容
        if (lineStart < content.length) {
            start = lineStart + 1;
        }
        byte[] tail = new byte[content.length - start];
        System.arraycopy(content, start, tail, 0, tail.length);
        Files.write(path, tail,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        bytesWritten = tail.length;
    }

    /**
     * 关闭写入流（忽略关闭异常）。
     */
    private void closeQuietly() {
        OutputStream stream = outputStream;
        outputStream = null;
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // 关闭失败不影响终端销毁
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }
}
