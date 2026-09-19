package com.chua.starter.server.support.spi;

/**
 * 远程日志分片（实时日志读取结果）
 * <p>
 * {@code nextCursor} 必须是「文件字节偏移」而非输出字符数：
 * 首帧返回末尾 N 行、其真实位置就是文件末尾；增量帧读到 EOF 同样位于文件末尾。
 * 若用输出字符长度做游标，下一帧会从错误偏移读取，导致重复输出历史日志，
 * 中文日志还会因字节/字符数不一致而错位。
 * </p>
 *
 * @param content    本次读取到的日志文本
 * @param nextCursor 下次读取应使用的字节偏移
 * @param eof        本次是否未读到新内容（内容为空）
 * @author CH
 * @since 4.0.0.42
 * @return 结果值
 */
public record RemoteLogChunk(String content, long nextCursor, boolean eof) {

    /**
     * 构造分片并自动推导 eof
     *
     * @param content    日志文本
     * @param nextCursor 下次字节偏移
     * @return 分片
     */
    public static RemoteLogChunk of(String content, long nextCursor) {
        String text = content == null ? "" : content;
        return new RemoteLogChunk(text, nextCursor, text.isEmpty());
    }
}
