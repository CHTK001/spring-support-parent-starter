package com.chua.starter.server.support.model;

import java.util.Locale;

/**
 * 远端文件 stat 结果：上传覆盖判定（md5 + 更新时间）的取数来源。
 *
 * <p>各通道目录列表里的 {@code modified} 只是展示文本（POSIX long-iso、WinRM LastWriteTime、
 * 本机 Date.toString），格式互不相同、不可解析；覆盖判定必须用本类携带的 epoch 毫秒与 md5。</p>
 *
 * @param exists             文件是否存在
 * @param size               字节大小（不存在时为 0）
 * @param lastModifiedMillis 最后修改时间（epoch 毫秒，UTC 基准；通道未取到时为 null）
 * @param md5                内容 MD5（小写十六进制；通道未取到时为 null）
 * @author CH
 * @since 4.0.0.52
 */
public record RemoteFileStatVO(boolean exists, long size, Long lastModifiedMillis, String md5) {

    /**
     * 通道 stat 命令的统一输出前缀：{@code STAT|size|mtimeMillis|md5} 或 {@code STAT|MISSING}
     */
    private static final String MARK = "STAT|";

    /**
     * 表示文件不存在的 stat 结果。
     *
     * @return exists=false 的结果
     */
    public static RemoteFileStatVO missing() {
        return new RemoteFileStatVO(false, 0L, null, null);
    }

    /**
     * 解析通道 stat 命令的统一输出。
     *
     * <p>本机、POSIX SSH、Windows SSH/WinRM、Agent 各通道都按 {@code STAT|...} 单行格式回显，
     * 这里做统一解析，通道实现不再各写一套。</p>
     *
     * @param output 命令输出，可为空
     * @return 文件不存在返回 {@link #missing()}；解析成功返回 stat；
     *         输出不可解析返回 null（调用方退回直接覆盖的既有行为）
     */
    public static RemoteFileStatVO parse(String output) {
        if (output == null) {
            return null;
        }
        for (String line : output.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.regionMatches(true, 0, MARK, 0, MARK.length())) {
                continue;
            }
            // -1 保留尾部空段：md5 取不到时输出形如 STAT|123|456|
            String[] p = t.split("\\|", -1);
            if (p.length >= 2 && "MISSING".equalsIgnoreCase(p[1].trim())) {
                return missing();
            }
            if (p.length < 4) {
                return null;
            }
            try {
                long size = Long.parseLong(p[1].trim());
                String mtime = p[2].trim();
                String md5 = p[3].trim();
                return new RemoteFileStatVO(true, size,
                        mtime.isEmpty() ? null : Long.valueOf(mtime),
                        md5.isEmpty() ? null : md5.toLowerCase(Locale.ROOT));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
